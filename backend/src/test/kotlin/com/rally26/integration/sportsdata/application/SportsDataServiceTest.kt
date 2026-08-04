package com.rally26.integration.sportsdata.application

import com.rally26.audit.application.AuditService
import com.rally26.common.web.CurrentUser
import com.rally26.integration.core.application.IntegrationCatalogService
import com.rally26.integration.core.application.IntegrationOAuthService
import com.rally26.integration.core.application.IntegrationSyncService
import com.rally26.integration.core.domain.IntegrationProvider
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
import kotlin.test.assertFalse
import kotlin.test.assertEquals

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
    private val currentUser = CurrentUser(UUID.randomUUID(), "owner@example.com", "Owner")

    @Test
    fun `partner pending file preview never enables direct import`() {
        val records = listOf(SportsDataExternalRecord(SportsDataEntityType.TEAM, "gc-team-1", "gc-org-1", "16U", emptyMap()))
        val run = SportsDataImportRun(UUID.randomUUID(), organizationId, null, IntegrationProvider.GAMECHANGER, null, SportsDataSourceMode.FILE_IMPORT, SportsDataImportStatus.PREVIEWED, false, 1, 1, 0, 0, 0, "a".repeat(64), currentUser.userId, Instant.now(), Instant.now())
        val syncRun = com.rally26.integration.core.domain.IntegrationSyncRun(
            UUID.randomUUID(), null, IntegrationProvider.GAMECHANGER,
            com.rally26.integration.core.domain.IntegrationOwnerType.ORGANIZATION,
            organizationId, null,
            com.rally26.integration.core.domain.IntegrationSyncDirection.READ,
            com.rally26.integration.core.domain.IntegrationSyncTrigger.MANUAL,
            com.rally26.integration.core.domain.IntegrationSyncStatus.RUNNING,
            null, null, "{}", 0, 0, 0, 0, 0, null, null, null, null,
            currentUser.userId, Instant.now(), Instant.now(), null,
        )
        every { sync.beginOrganizationRun(organizationId, null, IntegrationProvider.GAMECHANGER, any(), any(), any(), currentUser) } returns syncRun
        every { sync.finish(syncRun.id, any(), any(), any(), any()) } returns syncRun.copy(status = com.rally26.integration.core.domain.IntegrationSyncStatus.SUCCEEDED, completedAt = Instant.now())
        every { repository.createPreviewRun(organizationId, null, syncRun.id, IntegrationProvider.GAMECHANGER, SportsDataSourceMode.FILE_IMPORT, SportsDataImportStatus.PREVIEWED, 1, 1, 0, 0, 0, any(), currentUser.userId) } returns run

        val result = service.previewFile(organizationId, IntegrationProvider.GAMECHANGER, records, currentUser)

        assertFalse(result.directImportEnabled)
        assertEquals(0, result.issues.size)
        verify(exactly = 0) { providerClient.fetchSnapshot(any(), any()) }
    }
}
