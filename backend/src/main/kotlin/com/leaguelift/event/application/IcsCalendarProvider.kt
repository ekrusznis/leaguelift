package com.leaguelift.event.application

import com.leaguelift.event.domain.Event
import com.leaguelift.event.domain.EventStatus
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField

private val UTC_STAMP: DateTimeFormatter = DateTimeFormatterBuilder()
	.appendPattern("yyyyMMdd'T'HHmmss'Z'")
	.parseDefaulting(ChronoField.NANO_OF_SECOND, 0)
	.toFormatter()

/**
 * The only [CalendarProvider] implementation (Phase 10 slice 3, ADR-028) — RFC 5545
 * `VCALENDAR`/`VEVENT` text, no external dependency. Every timestamp is emitted in UTC
 * (`Z`-suffixed) rather than embedding a `VTIMEZONE` block for [Event.timezone] — an
 * `Instant` is already zone-independent, so UTC output is always correct and every
 * mainstream calendar client converts it to the viewer's own local time automatically;
 * a `VTIMEZONE` block would only matter for a floating (zone-less) time, which this
 * schema doesn't have. Never includes [Event.meetingPoint]/[Event.directionsNotes] —
 * those stay private, per section 14.1A's "never leak private meeting-location notes."
 */
@Component
class IcsCalendarProvider : CalendarProvider {

	override fun buildIcs(event: Event, displayTitle: String): String = wrap(listOf(EventWithDisplayTitle(event, displayTitle)))

	override fun buildIcs(events: List<EventWithDisplayTitle>): String = wrap(events)

	private fun wrap(events: List<EventWithDisplayTitle>): String {
		val lines = mutableListOf("BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:-//LeagueLift//Events//EN", "CALSCALE:GREGORIAN")
		events.forEach { lines += vevent(it.event, it.displayTitle) }
		lines += "END:VCALENDAR"
		return lines.joinToString("\r\n") + "\r\n"
	}

	private fun vevent(event: Event, displayTitle: String): List<String> {
		val lines = mutableListOf("BEGIN:VEVENT", "UID:${event.id}@leaguelift.app", "DTSTAMP:${stamp(Instant.now())}")
		event.startAt?.let { lines += fold("DTSTART:${stamp(it)}") }
		event.endAt?.let { lines += fold("DTEND:${stamp(it)}") }
		lines += fold("SUMMARY:${escape(displayTitle)}")
		val locationParts = listOfNotNull(event.venueName, event.area, event.address).filter { it.isNotBlank() }
		if (locationParts.isNotEmpty()) lines += fold("LOCATION:${escape(locationParts.joinToString(", "))}")
		event.description?.takeIf { it.isNotBlank() }?.let { lines += fold("DESCRIPTION:${escape(it)}") }
		lines += "STATUS:${icsStatus(event.status)}"
		lines += "END:VEVENT"
		return lines
	}

	private fun icsStatus(status: EventStatus): String = when (status) {
		EventStatus.CANCELLED -> "CANCELLED"
		EventStatus.DRAFT, EventStatus.TENTATIVE, EventStatus.DELAYED, EventStatus.POSTPONED -> "TENTATIVE"
		EventStatus.SCHEDULED, EventStatus.COMPLETED -> "CONFIRMED"
	}

	private fun stamp(instant: Instant): String = UTC_STAMP.format(instant.atZone(java.time.ZoneOffset.UTC))

	/** RFC 5545 §3.3.11 text escaping — backslash, comma, semicolon, and newline all need escaping. */
	private fun escape(value: String): String =
		value.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\r\n", "\\n").replace("\n", "\\n")

	/** RFC 5545 §3.1 line folding: no content line may exceed 75 octets; continuation lines start with a single space. */
	private fun fold(line: String): String {
		if (line.toByteArray(Charsets.UTF_8).size <= 75) return line
		val result = StringBuilder()
		var remaining = line
		var first = true
		while (remaining.toByteArray(Charsets.UTF_8).size > 75) {
			val limit = if (first) 75 else 74
			var cut = minOf(limit, remaining.length)
			while (remaining.substring(0, cut).toByteArray(Charsets.UTF_8).size > limit) cut--
			result.append(if (first) "" else "\r\n ").append(remaining.substring(0, cut))
			remaining = remaining.substring(cut)
			first = false
		}
		result.append(if (first) "" else "\r\n ").append(remaining)
		return result.toString()
	}
}
