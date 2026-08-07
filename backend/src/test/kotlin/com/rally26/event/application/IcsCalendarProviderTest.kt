package com.rally26.event.application

import com.rally26.event.domain.Event
import com.rally26.event.domain.EventSourceType
import com.rally26.event.domain.EventStatus
import com.rally26.event.domain.EventType
import com.rally26.event.domain.EventVisibility
import java.time.Instant
import java.time.LocalDate
import java.util.TimeZone
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IcsCalendarProviderTest {
    private val provider = IcsCalendarProvider()

    private fun event(
        startAt: Instant? = Instant.parse("2026-08-15T18:00:00Z"),
        endAt: Instant? = Instant.parse("2026-08-15T20:00:00Z"),
        status: EventStatus = EventStatus.SCHEDULED,
        description: String? = null,
        meetingPoint: String? = null,
        directionsNotes: String? = null,
        allDayDate: LocalDate? = null,
    ) = Event(
        id = UUID.randomUUID(),
        organizationId = UUID.randomUUID(),
        teamId = UUID.randomUUID(),
        tournamentId = null,
        opponentTeamId = null,
        opponentName = null,
        eventType = EventType.COMPETITION,
        title = null,
        description = description,
        status = status,
        startAt = startAt,
        endAt = endAt,
        arrivalAt = null,
        meetingAt = null,
        timezone = "America/New_York",
        venueName = "Riverside Park",
        address = "123 Main St",
        latitude = null,
        longitude = null,
        area = "Field 3",
        meetingPoint = meetingPoint,
        directionsNotes = directionsNotes,
        visibility = EventVisibility.TEAM,
        sourceType = EventSourceType.MANUAL,
        provider = null,
        connectionId = null,
        externalEventId = null,
        externalSyncHash = null,
        sourceUpdatedAt = null,
        createdByUserId = UUID.randomUUID(),
        updatedByUserId = UUID.randomUUID(),
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
        allDayDate = allDayDate,
    )

    @Test
    fun `builds a valid VCALENDAR VEVENT with UTC timestamps`() {
        val ics = provider.buildIcs(event(), "Home vs Away")

        assertTrue(ics.startsWith("BEGIN:VCALENDAR\r\n"))
        assertTrue(ics.contains("BEGIN:VEVENT"))
        assertTrue(ics.contains("DTSTART:20260815T180000Z"))
        assertTrue(ics.contains("DTEND:20260815T200000Z"))
        assertTrue(ics.contains("SUMMARY:Home vs Away"))
        assertTrue(ics.contains("STATUS:CONFIRMED"))
        assertTrue(ics.contains("END:VEVENT"))
        assertTrue(ics.endsWith("END:VCALENDAR\r\n"))
    }

    @Test
    fun `omits DTSTART and DTEND for a TBD event with no start time`() {
        val ics = provider.buildIcs(event(startAt = null, endAt = null), "TBD Semifinal")

        assertFalse(ics.contains("DTSTART"))
        assertFalse(ics.contains("DTEND"))
        assertTrue(ics.contains("SUMMARY:TBD Semifinal"))
    }

    @Test
    fun `maps CANCELLED status to the ICS CANCELLED value`() {
        val ics = provider.buildIcs(event(status = EventStatus.CANCELLED), "Cancelled Practice")

        assertTrue(ics.contains("STATUS:CANCELLED"))
    }

    @Test
    fun `never includes private meeting point or directions notes`() {
        val ics =
            provider.buildIcs(
                event(meetingPoint = "Meet by the flagpole", directionsNotes = "Use the north gate, code 1234"),
                "Practice",
            )

        assertFalse(ics.contains("flagpole"))
        assertFalse(ics.contains("north gate"))
    }

    @Test
    fun `escapes commas and semicolons in free-text fields`() {
        val ics = provider.buildIcs(event(description = "Bring water, snacks; arrive early"), "Practice")

        assertTrue(ics.contains("DESCRIPTION:Bring water\\, snacks\\; arrive early"))
    }

    @Test
    fun `an all-day event emits VALUE=DATE, not a UTC DATE-TIME`() {
        val ics =
            provider.buildIcs(event(startAt = null, endAt = null, allDayDate = LocalDate.of(2026, 7, 4)), "Independence Day Tournament")

        assertTrue(ics.contains("DTSTART;VALUE=DATE:20260704"))
        assertTrue(ics.contains("DTEND;VALUE=DATE:20260705"))
        assertFalse(ics.contains("DTSTART:"))
        assertFalse(ics.contains("DTEND:"))
    }

    @Test
    fun `an all-day event's date never shifts regardless of the JVM's default timezone`() {
        val originalDefault = TimeZone.getDefault()
        try {
            val allDayEvent = event(startAt = null, endAt = null, allDayDate = LocalDate.of(2026, 12, 31))

            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
            val westCoastIcs = provider.buildIcs(allDayEvent, "New Year's Eve Tournament")

            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati"))
            val farEastIcs = provider.buildIcs(allDayEvent, "New Year's Eve Tournament")

            assertTrue(westCoastIcs.contains("DTSTART;VALUE=DATE:20261231"))
            assertTrue(farEastIcs.contains("DTSTART;VALUE=DATE:20261231"))
            assertTrue(westCoastIcs.contains("DTEND;VALUE=DATE:20270101"))
            assertTrue(farEastIcs.contains("DTEND;VALUE=DATE:20270101"))
        } finally {
            TimeZone.setDefault(originalDefault)
        }
    }

    @Test
    fun `combined-schedule ics includes one VEVENT per event`() {
        val ics = provider.buildIcs(listOf(EventWithDisplayTitle(event(), "Game 1"), EventWithDisplayTitle(event(), "Game 2")))

        assertEqualsCount(2, ics, "BEGIN:VEVENT")
    }

    private fun assertEqualsCount(
        expected: Int,
        haystack: String,
        needle: String,
    ) {
        var count = 0
        var index = haystack.indexOf(needle)
        while (index != -1) {
            count++
            index = haystack.indexOf(needle, index + needle.length)
        }
        kotlin.test.assertEquals(expected, count)
    }
}
