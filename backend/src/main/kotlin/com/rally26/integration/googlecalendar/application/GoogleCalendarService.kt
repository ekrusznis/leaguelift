package com.rally26.integration.googlecalendar.application

import com.rally26.audit.application.AuditService
import com.rally26.common.error.NotFoundException
import com.rally26.common.error.ValidationException
import com.rally26.common.web.CurrentUser
import com.rally26.integration.core.application.IntegrationCatalogItem
import com.rally26.integration.core.application.IntegrationCatalogService
import com.rally26.integration.core.application.IntegrationOAuthService
import com.rally26.integration.core.domain.IntegrationConnection
import com.rally26.integration.core.domain.IntegrationConnectionStatus
import com.rally26.integration.core.domain.IntegrationProvider
import com.rally26.integration.googlecalendar.domain.GoogleCalendarConnectionSetting
import com.rally26.integration.googlecalendar.domain.GoogleCalendarDescriptor
import com.rally26.integration.googlecalendar.domain.GoogleCalendarEventMapping
import com.rally26.integration.googlecalendar.persistence.GoogleCalendarRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

data class GoogleCalendarOverview(
    val catalog: IntegrationCatalogItem,
    val setting: GoogleCalendarConnectionSetting?,
    val mappingCount: Int,
    val icsFallbackAvailable: Boolean,
    val automaticSyncAvailable: Boolean,
)

@Service
class GoogleCalendarService(
    private val catalogService: IntegrationCatalogService,
    private val oauthService: IntegrationOAuthService,
    private val repository: GoogleCalendarRepository,
    private val providerClient: GoogleCalendarProviderClient,
    private val auditService: AuditService,
) {
    fun overview(currentUser: CurrentUser): GoogleCalendarOverview {
        val item =
            catalogService
                .listForUser(currentUser)
                .firstOrNull { it.definition.provider == IntegrationProvider.GOOGLE_CALENDAR }
                ?: throw NotFoundException("INTEGRATION_PROVIDER_NOT_FOUND", "Google Calendar could not be found in the provider catalog.")
        val connection = item.connection
        return GoogleCalendarOverview(
            catalog = item,
            setting = connection?.let { repository.findSetting(it.id) },
            mappingCount = connection?.let { repository.listMappings(it.id).size } ?: 0,
            icsFallbackAvailable = true,
            automaticSyncAvailable = false,
        )
    }

    @Transactional
    fun listCalendars(currentUser: CurrentUser): List<GoogleCalendarDescriptor> {
        val access = requireGoogleAccess(currentUser)
        val calendars = providerClient.listCalendars(access.accessToken)
        repository.markCalendarsListed(access.connection.id)
        return calendars
    }

    @Transactional
    fun selectCalendar(
        calendarId: String,
        currentUser: CurrentUser,
    ): GoogleCalendarConnectionSetting {
        if (calendarId.isBlank()) throw ValidationException("Choose a Google Calendar.")
        val access = requireGoogleAccess(currentUser)
        val calendar =
            providerClient
                .listCalendars(access.accessToken)
                .firstOrNull { it.id == calendarId && it.writable }
                ?: throw ValidationException("Choose a writable Google Calendar returned by the connected account.")
        val setting = repository.upsertSetting(access.connection.id, calendar.id, calendar.name, calendar.timezone)
        auditService.record(
            currentUser.userId,
            null,
            "integration.google_calendar_selected",
            "integration_connection",
            access.connection.id,
        )
        return setting
    }

    @Transactional
    fun clearCalendar(currentUser: CurrentUser): GoogleCalendarConnectionSetting? {
        val connection = requireGoogleConnection(currentUser)
        val setting = repository.clearSelection(connection.id)
        auditService.record(
            currentUser.userId,
            null,
            "integration.google_calendar_selection_cleared",
            "integration_connection",
            connection.id,
        )
        return setting
    }

    fun listMappings(currentUser: CurrentUser): List<GoogleCalendarEventMapping> =
        repository.listMappings(requireGoogleConnection(currentUser).id)

    private fun requireGoogleAccess(currentUser: CurrentUser) =
        oauthService.accessTokenForUserConnection(requireGoogleConnection(currentUser).id, currentUser)

    private fun requireGoogleConnection(currentUser: CurrentUser): IntegrationConnection {
        val connection =
            oauthService
                .listUserConnections(currentUser)
                .firstOrNull {
                    it.provider == IntegrationProvider.GOOGLE_CALENDAR &&
                        it.status in setOf(IntegrationConnectionStatus.CONNECTED, IntegrationConnectionStatus.DEGRADED)
                }
                ?: throw ValidationException("Google Calendar is not connected.")
        return connection
    }
}
