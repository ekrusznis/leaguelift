package com.rally26.integration.sportsdata.application

import com.rally26.audit.application.AuditService
import com.rally26.common.error.NotFoundException
import com.rally26.common.web.CurrentUser
import com.rally26.integration.core.application.IntegrationAccessToken
import com.rally26.integration.core.application.IntegrationCatalogService
import com.rally26.integration.core.application.IntegrationOAuthService
import com.rally26.integration.core.application.IntegrationSyncService
import com.rally26.integration.core.domain.IntegrationAuthMode
import com.rally26.integration.core.domain.IntegrationCategory
import com.rally26.integration.core.domain.IntegrationConnection
import com.rally26.integration.core.domain.IntegrationConnectionStatus
import com.rally26.integration.core.domain.IntegrationOwnerType
import com.rally26.integration.core.domain.IntegrationProvider
import com.rally26.integration.core.domain.IntegrationSyncDirection
import com.rally26.integration.core.domain.IntegrationSyncStatus
import com.rally26.integration.core.domain.IntegrationSyncTrigger
import com.rally26.integration.sportsdata.domain.SportsDataEntityType
import com.rally26.integration.sportsdata.domain.SportsDataExternalRecord
import com.rally26.integration.sportsdata.domain.SportsDataImportRun
import com.rally26.integration.sportsdata.domain.SportsDataImportStatus
import com.rally26.integration.sportsdata.domain.SportsDataSourceMode
import com.rally26.integration.sportsdata.persistence.SportsDataRepository
import com.rally26.membership.application.MembershipService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse

class SportsDataServiceTest {
    private val catalog = mockk<IntegrationCatalogService>()
    private val oauth = mockk<IntegrationOAuthService>()
    private val providerClient = mockk<SportsDataProviderClient>()
    private val repository = mockk<SportsDataRepository>()
    private val sync = mockk<IntegrationSyncService>()
    private val membership = mockk<MembershipService>(relaxed = true)
    private val audit = mockk<AuditService>(relaxed = true)
    private val service = SportsDataService(catalog, oauth, listOf(providerClient), repository, sync, membership, audit)
    private val organizationId = UUID.randomUUID()
    private val connectionId = UUID.randomUUID()
    private val currentUser = CurrentUser(UUID.randomUUID(), "owner@example.com", "Owner")

    @Test
    fun `previewing a connected TeamSnap connection maps provider records into a review-only preview`() {
        val connection = connection(IntegrationProvider.TEAMSNAP)
        val records = listOf(SportsDataExternalRecord(SportsDataEntityType.TEAM, "ts-team-1", "ts-org-1", "12U", emptyMap()))
        val run =
            SportsDataImportRun(
                UUID.randomUUID(),
                organizationId,
                connectionId,
                IntegrationProvider.TEAMSNAP,
                null,
                SportsDataSourceMode.OAUTH,
                SportsDataImportStatus.PREVIEWED,
                false,
                1,
                1,
                0,
                0,
                0,
                "a".repeat(64),
                currentUser.userId,
                Instant.now(),
                Instant.now(),
            )
        val syncRun = syncRun(IntegrationProvider.TEAMSNAP)

        every { oauth.accessTokenForOrganizationConnection(organizationId, connectionId, currentUser) } returns
            IntegrationAccessToken(connection, "stub-access-teamsnap")
        every { providerClient.supports(IntegrationProvider.TEAMSNAP) } returns true
        every { providerClient.fetchSnapshot(IntegrationProvider.TEAMSNAP, "stub-access-teamsnap") } returns records
        every { repository.findMapping(connectionId, SportsDataEntityType.TEAM, "ts-team-1") } returns null
        every {
            sync.beginOrganizationRun(organizationId, connectionId, IntegrationProvider.TEAMSNAP, any(), any(), any(), currentUser)
        } returns syncRun
        every { sync.finish(syncRun.id, any(), any(), any(), any()) } returns
            syncRun.copy(status = IntegrationSyncStatus.SUCCEEDED, completedAt = Instant.now())
        every {
            repository.createPreviewRun(
                organizationId,
                connectionId,
                syncRun.id,
                IntegrationProvider.TEAMSNAP,
                SportsDataSourceMode.OAUTH,
                SportsDataImportStatus.PREVIEWED,
                1,
                1,
                0,
                0,
                0,
                any(),
                currentUser.userId,
            )
        } returns run

        val result = service.previewConnected(organizationId, connectionId, currentUser)

        assertFalse(result.directImportEnabled)
        assertEquals(0, result.issues.size)
        assertEquals("TeamSnap", result.message.substringAfter("developer application for ").trimEnd('.'))
        verify(exactly = 1) { providerClient.fetchSnapshot(IntegrationProvider.TEAMSNAP, "stub-access-teamsnap") }
    }

    @Test
    fun `previewing a connection for a provider outside the narrowed sports-data catalog is not found`() {
        every { oauth.accessTokenForOrganizationConnection(organizationId, connectionId, currentUser) } returns
            IntegrationAccessToken(connection(IntegrationProvider.QUICKBOOKS_ONLINE), "stub-access")

        assertFails { service.previewConnected(organizationId, connectionId, currentUser) }
            .also { assertEquals(NotFoundException::class, it::class) }
    }

    private fun connection(provider: IntegrationProvider) =
        IntegrationConnection(
            id = connectionId,
            provider = provider,
            category = IntegrationCategory.SPORTS_DATA,
            ownerType = IntegrationOwnerType.ORGANIZATION,
            organizationId = organizationId,
            userId = null,
            authMode = IntegrationAuthMode.OAUTH2,
            status = IntegrationConnectionStatus.CONNECTED,
            grantedScopes = emptyList(),
            externalAccountId = "ext-1",
            externalAccountName = "Rally26 Test Club",
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

    private fun syncRun(provider: IntegrationProvider) =
        com.rally26.integration.core.domain.IntegrationSyncRun(
            UUID.randomUUID(),
            connectionId,
            provider,
            IntegrationOwnerType.ORGANIZATION,
            organizationId,
            null,
            IntegrationSyncDirection.READ,
            IntegrationSyncTrigger.STUB,
            IntegrationSyncStatus.RUNNING,
            null,
            null,
            "{}",
            0,
            0,
            0,
            0,
            0,
            null,
            null,
            null,
            null,
            currentUser.userId,
            Instant.now(),
            Instant.now(),
            null,
        )
}
