package com.leaguelift.integration.eventsource.application

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

private val DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd")

data class ParsedIcsEvent(
	val uid: String,
	val summary: String?,
	val description: String?,
	val location: String?,
	val startAt: Instant?,
	val endAt: Instant?,
	/** Raw RFC 5545 value (CONFIRMED/TENTATIVE/CANCELLED) — the caller maps this to [com.leaguelift.event.domain.EventStatus], not this parser. */
	val status: String?,
)

/**
 * A minimal incoming-ICS reader (Phase 12 slice 3, ADR-033) — the counterpart to
 * `IcsCalendarProvider`'s outgoing generator (Phase 10 slice 3, ADR-028). Only the
 * properties this codebase's `event` model actually uses are read (UID, SUMMARY,
 * DESCRIPTION, LOCATION, DTSTART, DTEND, STATUS); everything else in a real-world
 * feed (ORGANIZER, ATTENDEE, RRULE, VALARM, VTIMEZONE) is silently ignored, not
 * partially/incorrectly interpreted. No recurrence expansion (RRULE) — a
 * recurring VEVENT block is read as the single occurrence it literally states.
 */
object IcsFeedParser {

	fun parse(content: String, defaultZone: ZoneId): List<ParsedIcsEvent> {
		val events = mutableListOf<ParsedIcsEvent>()
		var inEvent = false
		var uid: String? = null
		var summary: String? = null
		var description: String? = null
		var location: String? = null
		var startAt: Instant? = null
		var endAt: Instant? = null
		var status: String? = null

		for (line in unfold(content)) {
			when {
				line == "BEGIN:VEVENT" -> {
					inEvent = true
					uid = null; summary = null; description = null; location = null; startAt = null; endAt = null; status = null
				}
				line == "END:VEVENT" -> {
					inEvent = false
					val currentUid = uid
					if (currentUid != null) {
						events.add(ParsedIcsEvent(currentUid, summary, description, location, startAt, endAt, status))
					}
				}
				inEvent && line.isNotBlank() -> {
					val (name, params, value) = splitProperty(line) ?: continue
					when (name) {
						"UID" -> uid = value
						"SUMMARY" -> summary = unescapeText(value)
						"DESCRIPTION" -> description = unescapeText(value)
						"LOCATION" -> location = unescapeText(value)
						"STATUS" -> status = value
						"DTSTART" -> startAt = parseInstant(value, params, defaultZone)
						"DTEND" -> endAt = parseInstant(value, params, defaultZone)
					}
				}
			}
		}
		return events
	}

	/** RFC 5545 line unfolding: a line beginning with a single space or tab continues the previous line. */
	private fun unfold(content: String): List<String> {
		val normalized = content.replace("\r\n", "\n").replace("\r", "\n")
		val rawLines = normalized.split("\n")
		val unfolded = mutableListOf<String>()
		for (line in rawLines) {
			if ((line.startsWith(" ") || line.startsWith("\t")) && unfolded.isNotEmpty()) {
				unfolded[unfolded.size - 1] = unfolded.last() + line.substring(1)
			} else {
				unfolded.add(line)
			}
		}
		return unfolded
	}

	private fun splitProperty(line: String): Triple<String, Map<String, String>, String>? {
		val colonIndex = line.indexOf(':')
		if (colonIndex == -1) return null
		val nameAndParams = line.substring(0, colonIndex)
		val value = line.substring(colonIndex + 1)
		val parts = nameAndParams.split(';')
		val name = parts[0].trim().uppercase()
		val params = parts.drop(1).associate { part ->
			val eq = part.indexOf('=')
			if (eq == -1) part.uppercase() to "" else part.substring(0, eq).uppercase() to part.substring(eq + 1)
		}
		return Triple(name, params, value)
	}

	private fun unescapeText(value: String): String =
		value.replace("\\n", "\n").replace("\\N", "\n").replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\")

	private fun parseInstant(value: String, params: Map<String, String>, defaultZone: ZoneId): Instant? {
		val trimmed = value.trim()
		return try {
			when {
				params["VALUE"] == "DATE" || (trimmed.length == 8 && trimmed.all { it.isDigit() }) ->
					LocalDate.parse(trimmed, DATE_FORMAT).atStartOfDay(defaultZone).toInstant()
				trimmed.endsWith("Z") ->
					LocalDateTime.parse(trimmed.dropLast(1), DATE_TIME_FORMAT).atZone(ZoneId.of("UTC")).toInstant()
				params.containsKey("TZID") ->
					LocalDateTime.parse(trimmed, DATE_TIME_FORMAT).atZone(zoneOrDefault(params.getValue("TZID"), defaultZone)).toInstant()
				else ->
					LocalDateTime.parse(trimmed, DATE_TIME_FORMAT).atZone(defaultZone).toInstant()
			}
		} catch (e: DateTimeParseException) {
			null
		}
	}

	private fun zoneOrDefault(tzid: String, defaultZone: ZoneId): ZoneId =
		try {
			ZoneId.of(tzid)
		} catch (e: Exception) {
			defaultZone
		}
}
