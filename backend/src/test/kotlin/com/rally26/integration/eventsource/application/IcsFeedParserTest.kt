package com.rally26.integration.eventsource.application

import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IcsFeedParserTest {
    private val defaultZone = ZoneId.of("America/New_York")

    @Test
    fun `parses a UTC VEVENT`() {
        val ics =
            """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            UID:game-1@example.com
            SUMMARY:Varsity vs Rivals
            LOCATION:Home Field
            DESCRIPTION:Bring water
            DTSTART:20260905T193000Z
            DTEND:20260905T213000Z
            STATUS:CONFIRMED
            END:VEVENT
            END:VCALENDAR
            """.trimIndent()

        val events = IcsFeedParser.parse(ics, defaultZone)

        assertEquals(1, events.size)
        val event = events.first()
        assertEquals("game-1@example.com", event.uid)
        assertEquals("Varsity vs Rivals", event.summary)
        assertEquals("Home Field", event.location)
        assertEquals("Bring water", event.description)
        assertEquals(Instant.parse("2026-09-05T19:30:00Z"), event.startAt)
        assertEquals(Instant.parse("2026-09-05T21:30:00Z"), event.endAt)
        assertEquals("CONFIRMED", event.status)
    }

    @Test
    fun `resolves a floating (no offset) DTSTART against the connection's own timezone`() {
        val ics =
            """
            BEGIN:VEVENT
            UID:game-2
            SUMMARY:Practice
            DTSTART:20260905T153000
            END:VEVENT
            """.trimIndent()

        val events = IcsFeedParser.parse(ics, defaultZone)

        assertEquals(Instant.parse("2026-09-05T15:30:00Z").plusSeconds(4 * 3600), events.first().startAt)
    }

    @Test
    fun `resolves a TZID-qualified DTSTART against that explicit zone, not the default`() {
        val ics =
            """
            BEGIN:VEVENT
            UID:game-3
            SUMMARY:Away game
            DTSTART;TZID=America/Los_Angeles:20260905T123000
            END:VEVENT
            """.trimIndent()

        val events = IcsFeedParser.parse(ics, defaultZone)

        // 12:30 PM Pacific == 19:30 UTC (PDT, UTC-7) in September.
        assertEquals(Instant.parse("2026-09-05T19:30:00Z"), events.first().startAt)
    }

    @Test
    fun `treats an all-day VALUE=DATE event as midnight in the default zone`() {
        val ics =
            """
            BEGIN:VEVENT
            UID:tournament-day
            SUMMARY:Tournament Day
            DTSTART;VALUE=DATE:20260905
            END:VEVENT
            """.trimIndent()

        val events = IcsFeedParser.parse(ics, defaultZone)

        assertEquals(Instant.parse("2026-09-05T00:00:00Z").plusSeconds(4 * 3600), events.first().startAt)
    }

    @Test
    fun `unfolds a continuation line and unescapes text`() {
        val ics =
            "BEGIN:VEVENT\r\nUID:game-4\r\nSUMMARY:Long title that wraps\r\n  across a folded line\r\n" +
                "DESCRIPTION:Line one\\nLine two\\, still going\r\nEND:VEVENT"

        val events = IcsFeedParser.parse(ics, defaultZone)

        assertEquals("Long title that wraps across a folded line", events.first().summary)
        assertEquals("Line one\nLine two, still going", events.first().description)
    }

    @Test
    fun `ignores an event with no UID`() {
        val ics =
            """
            BEGIN:VEVENT
            SUMMARY:No identity
            DTSTART:20260905T153000Z
            END:VEVENT
            """.trimIndent()

        assertTrue(IcsFeedParser.parse(ics, defaultZone).isEmpty())
    }

    @Test
    fun `leaves startAt null when DTSTART is absent (a TBD event)`() {
        val ics =
            """
            BEGIN:VEVENT
            UID:tbd-game
            SUMMARY:TBD opponent
            END:VEVENT
            """.trimIndent()

        val event = IcsFeedParser.parse(ics, defaultZone).first()

        assertNull(event.startAt)
    }

    @Test
    fun `parses multiple VEVENT blocks`() {
        val ics =
            """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            UID:game-a
            SUMMARY:Game A
            DTSTART:20260905T153000Z
            END:VEVENT
            BEGIN:VEVENT
            UID:game-b
            SUMMARY:Game B
            DTSTART:20260912T153000Z
            END:VEVENT
            END:VCALENDAR
            """.trimIndent()

        val events = IcsFeedParser.parse(ics, defaultZone)

        assertEquals(2, events.size)
        assertEquals(setOf("game-a", "game-b"), events.map { it.uid }.toSet())
    }
}
