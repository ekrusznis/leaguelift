package com.rally26.integration.googlecalendar.application

import com.rally26.audit.application.AuditService
import com.rally26.common.error.ValidationException
import com.rally26.common.web.CurrentUser
import com.rally26.integration.core.application.IntegrationAccessToken
import com.rally26.integration.core.application.IntegrationCatalogItem
import com.rally26.integration.core.application.IntegrationCatalogService
import com.rally26.integration.core.application.IntegrationOAuthService
import com.rally26.integration.core.domain.IntegrationAdapterMode
import com.rally26.integration.core.domain.IntegrationAuthMode
import com.rally26.integration.core.domain.IntegrationCategory
import com.rally26.integration.core.domain.IntegrationConnection
import com.rally26.integration.core.domain.IntegrationConnectionStatus
import com.rally26.integration.core.domain.IntegrationOwnerType
import com.rally26.integration.core.domain.IntegrationProvider
import com.rally26.integration.core.domain.IntegrationProviderDefinition
import com.rally26.integration.core.domain.IntegrationReadiness
import com.rally26.integration.googlecalendar.domain.GoogleCalendarConnectionSetting
import com.rally26.integration.googlecalendar.domain.GoogleCalendarDescriptor
import com.rally26.integration.googlecalendar.domain.GoogleCalendarSyncDirection
import com.rally26.integration.googlecalendar.persistence.GoogleCalendarRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GoogleCalendarServiceTest {
    private val catalogService = mockk<IntegrationCatalogService>()
    private val oauthService = mockk<IntegrationOAuthService>()
    private val repository = mockk<GoogleCalendarRepository>()
    private val providerClient = mockk<GoogleCalendarProviderClient>()
    private val auditService = mockk<AuditService>(relaxed = true)
    private val service = GoogleCalendarService(catalogService, oauthService, repository, providerClient, auditService)
    private val currentUser = CurrentUser(UUID.randomUUID(), "person@example.com", "Person")

    @Test
    fun `overview keeps ICS available while automatic synchronization is disabled`() {
        every { catalogService.listForUser(currentUser) } returns
            listOf(
                IntegrationCatalogItem(definition(), IntegrationReadiness.NOT_CONFIGURED, null, stub = false),
            )

        val result = service.overview(currentUser)

        assertTrue(result.icsFallbackAvailable)
        assertFalse(result.automaticSyncAvailable)
        assertEquals(0, result.mappingCount)
        assertEquals(null, result.setting)
        verify(exactly = 0) { providerClient.listCalendars(any()) }
    }

    @Test
    fun `calendar selection accepts only a writable calendar returned by the provider`() {
        val connection = connection()
        val writable = GoogleCalendarDescriptor("primary", "Primary", "America/New_York", primary = true, writable = true)
        val readOnly = GoogleCalendarDescriptor("readonly", "Read only", "America/New_York", primary = false, writable = false)
        val now = Instant.now()
        val setting =
            GoogleCalendarConnectionSetting(
                connectionId = connection.id,
                selectedCalendarId = writable.id,
                selectedCalendarName = writable.name,
                selectedCalendarTimezone = writable.timezone,
                syncDirection = GoogleCalendarSyncDirection.RALLY26_TO_GOOGLE,
                automaticSyncEnabled = false,
                lastCalendarListedAt = now,
                createdAt = now,
                updatedAt = now,
            )
        every { oauthService.listUserConnections(currentUser) } returns listOf(connection)
        every { oauthService.accessTokenForUserConnection(connection.id, currentUser) } returns
            IntegrationAccessToken(connection, "stub-access-token")
        every { providerClient.listCalendars("stub-access-token") } returns listOf(writable, readOnly)
        every { repository.upsertSetting(connection.id, writable.id, writable.name, writable.timezone) } returns setting

        val result = service.selectCalendar(writable.id, currentUser)

        assertEquals(writable.id, result.selectedCalendarId)
        assertFalse(result.automaticSyncEnabled)
        verify(exactly = 1) { repository.upsertSetting(connection.id, writable.id, writable.name, writable.timezone) }

        assertFailsWith<ValidationException> { service.selectCalendar(readOnly.id, currentUser) }
        verify(exactly = 0) { repository.upsertSetting(connection.id, readOnly.id, any(), any()) }
    }

    private fun definition() =
        IntegrationProviderDefinition(
            provider = IntegrationProvider.GOOGLE_CALENDAR,
            displayName = "Google Calendar",
            category = IntegrationCategory.CALENDAR,
            ownershipScope = IntegrationOwnerType.USER,
            primaryAuthMode = IntegrationAuthMode.OAUTH2,
            supportedAuthModes = listOf(IntegrationAuthMode.OAUTH2),
            baselineReadiness = IntegrationReadiness.NOT_CONFIGURED,
            adapterMode = IntegrationAdapterMode.OAUTH_SCAFFOLD,
            description = "Personal calendar scaffold",
            activationRequirement = "Verified Google OAuth application",
            defaultScopes = emptyList(),
            sortOrder = 1,
            visibleToCustomers = true,
        )

    private fun connection() =
        IntegrationConnection(
            id = UUID.randomUUID(),
            provider = IntegrationProvider.GOOGLE_CALENDAR,
            category = IntegrationCategory.CALENDAR,
            ownerType = IntegrationOwnerType.USER,
            organizationId = null,
            userId = currentUser.userId,
            authMode = IntegrationAuthMode.OAUTH2,
            status = IntegrationConnectionStatus.CONNECTED,
            grantedScopes = emptyList(),
            externalAccountId = "person@example.com",
            externalAccountName = "Person",
            credentialId = UUID.randomUUID(),
            accessTokenExpiresAt = Instant.now().plusSeconds(3600),
            refreshLockedAt = null,
            refreshLockedByUserId = null,
            lastSuccessfulSyncAt = null,
            lastHealthCheckAt = null,
            lastErrorCode = null,
            lastErrorMessage = null,
            legacyResourceType = null,
            legacyResourceId = null,
            createdByUserId = currentUser.userId,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            connectedAt = Instant.now(),
            revokedAt = null,
            disconnectedAt = null,
        )
}
