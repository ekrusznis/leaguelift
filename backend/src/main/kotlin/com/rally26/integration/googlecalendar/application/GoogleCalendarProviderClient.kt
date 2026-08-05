package com.rally26.integration.googlecalendar.application

import com.rally26.common.error.ServiceUnavailableException
import com.rally26.config.IntegrationProperties
import com.rally26.integration.googlecalendar.domain.GoogleCalendarDescriptor
import com.rally26.integration.googlecalendar.domain.GoogleCalendarEventPayload
import com.rally26.integration.googlecalendar.domain.GoogleCalendarWriteResult
import org.springframework.stereotype.Component
import java.security.MessageDigest

/**
 * Provider-specific seam for Phase 19.4. The local/test implementation is
 * deterministic; non-stub environments fail closed until the official Google client
 * and credentialed contract tests are activated in Phase 20.
 */
interface GoogleCalendarProviderClient {
    fun listCalendars(accessToken: String): List<GoogleCalendarDescriptor>

    fun upsertEvent(
        accessToken: String,
        calendarId: String,
        externalEventId: String?,
        payload: GoogleCalendarEventPayload,
    ): GoogleCalendarWriteResult

    fun deleteEvent(
        accessToken: String,
        calendarId: String,
        externalEventId: String,
    )
}

@Component
class ScaffoldGoogleCalendarProviderClient(
    private val properties: IntegrationProperties,
) : GoogleCalendarProviderClient {
    override fun listCalendars(accessToken: String): List<GoogleCalendarDescriptor> {
        requireStub(accessToken)
        return listOf(
            GoogleCalendarDescriptor("primary", "Primary calendar", "America/New_York", primary = true, writable = true),
            GoogleCalendarDescriptor("league-lift-test", "Rally26 test calendar", "America/New_York", primary = false, writable = true),
            GoogleCalendarDescriptor("read-only-test", "Read-only test calendar", "America/New_York", primary = false, writable = false),
        )
    }

    override fun upsertEvent(
        accessToken: String,
        calendarId: String,
        externalEventId: String?,
        payload: GoogleCalendarEventPayload,
    ): GoogleCalendarWriteResult {
        requireStub(accessToken)
        val id = externalEventId ?: "ll-${digest("$calendarId:${payload.sourceEventId}")}"
        return GoogleCalendarWriteResult(id, "stub-etag-${digest(payload.toString())}")
    }

    override fun deleteEvent(
        accessToken: String,
        calendarId: String,
        externalEventId: String,
    ) {
        requireStub(accessToken)
    }

    private fun requireStub(accessToken: String) {
        if (!properties.stubMode || !accessToken.startsWith("stub-access-")) {
            throw ServiceUnavailableException(
                "GOOGLE_CALENDAR_CLIENT_NOT_ACTIVATED",
                "Google Calendar synchronization is scaffolded but has not been activated with a verified provider client.",
            )
        }
    }

    private fun digest(value: String): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(24)
}
