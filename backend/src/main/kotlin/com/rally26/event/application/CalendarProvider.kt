package com.rally26.event.application

import com.rally26.event.domain.Event

/**
 * The `CalendarProvider` seam DESIGN-DOC.md section 14.1A/17 reserves (Phase 10 slice
 * 3, ADR-028) — standards-based `.ics`/Add-to-Calendar output for a single event or an
 * authorized schedule range, deliberately **not** Google OAuth/calendar-write/two-way
 * sync (a later, separate decision per section 19.3 #23). Lives in the `event` module,
 * not `notification/` alongside `EmailProvider`/`SmsProvider`/`AnalyticsProvider` —
 * unlike those, building an `.ics` file is pure event-domain formatting with no
 * external HTTP call or credential, so there's nothing "provider-shaped" to abstract
 * yet beyond this one interface; a future real Google Calendar OAuth integration would
 * still implement this same interface.
 */
interface CalendarProvider {
    /** A single-event `.ics` file — one VEVENT. */
    fun buildIcs(
        event: Event,
        displayTitle: String,
    ): String

    /** A combined-schedule `.ics` file — one VEVENT per event, e.g. a household's or participant's authorized schedule. */
    fun buildIcs(events: List<EventWithDisplayTitle>): String
}

data class EventWithDisplayTitle(
    val event: Event,
    val displayTitle: String,
)
