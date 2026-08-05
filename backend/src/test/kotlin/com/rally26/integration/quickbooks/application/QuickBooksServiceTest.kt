package com.rally26.integration.quickbooks.application

import com.rally26.audit.application.AuditService
import com.rally26.common.web.CurrentUser
import com.rally26.integration.core.application.IntegrationCatalogItem
import com.rally26.integration.core.application.IntegrationCatalogService
import com.rally26.integration.core.application.IntegrationOAuthService
import com.rally26.integration.core.application.IntegrationSyncService
import com.rally26.integration.core.domain.IntegrationAdapterMode
import com.rally26.integration.core.domain.IntegrationAuthMode
import com.rally26.integration.core.domain.IntegrationCategory
import com.rally26.integration.core.domain.IntegrationOwnerType
import com.rally26.integration.core.domain.IntegrationProvider
import com.rally26.integration.core.domain.IntegrationProviderDefinition
import com.rally26.integration.core.domain.IntegrationReadiness
import com.rally26.integration.quickbooks.persistence.QuickBooksRepository
import com.rally26.membership.application.MembershipService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QuickBooksServiceTest {
    private val catalog = mockk<IntegrationCatalogService>()
    private val oauth = mockk<IntegrationOAuthService>()
    private val repository = mockk<QuickBooksRepository>()
    private val provider = mockk<QuickBooksProviderClient>()
    private val sync = mockk<IntegrationSyncService>()
    private val membership = mockk<MembershipService>(relaxed = true)
    private val audit = mockk<AuditService>(relaxed = true)
    private val service = QuickBooksService(catalog, oauth, repository, provider, sync, membership, audit)
    private val organizationId = UUID.randomUUID()
    private val currentUser = CurrentUser(UUID.randomUUID(), "owner@example.com", "Owner")

    @Test
    fun `overview never enables provider writes during scaffold phase`() {
        every { catalog.listForOrganization(organizationId, currentUser) } returns
            listOf(
                IntegrationCatalogItem(definition(), IntegrationReadiness.NOT_CONFIGURED, null, false),
            )
        every { repository.listBatches(organizationId) } returns emptyList()

        val result = service.overview(organizationId, currentUser)

        assertFalse(result.providerWritesEnabled)
        assertTrue(result.accountingReviewRequired)
        verify(exactly = 0) { provider.readCompany(any(), any()) }
        verify(exactly = 0) { provider.listAccounts(any(), any()) }
    }

    private fun definition() =
        IntegrationProviderDefinition(
            IntegrationProvider.QUICKBOOKS_ONLINE,
            "QuickBooks Online",
            IntegrationCategory.ACCOUNTING,
            IntegrationOwnerType.ORGANIZATION,
            IntegrationAuthMode.OAUTH2,
            listOf(IntegrationAuthMode.OAUTH2),
            IntegrationReadiness.NOT_CONFIGURED,
            IntegrationAdapterMode.OAUTH_SCAFFOLD,
            "Accounting scaffold",
            "Phase 20 credentials",
            emptyList(),
            20,
            true,
        )
}
