package com.rally26.integration.quickbooks.application

import com.rally26.audit.application.AuditService
import com.rally26.common.error.ForbiddenException
import com.rally26.common.error.NotFoundException
import com.rally26.common.web.CurrentUser
import com.rally26.integration.core.application.IntegrationAccessToken
import com.rally26.integration.core.application.IntegrationCatalogService
import com.rally26.integration.core.application.IntegrationOAuthService
import com.rally26.integration.core.application.IntegrationSyncService
import com.rally26.integration.core.domain.IntegrationConnection
import com.rally26.integration.core.domain.IntegrationProvider
import com.rally26.integration.quickbooks.domain.QuickBooksAccount
import com.rally26.integration.quickbooks.persistence.QuickBooksRepository
import com.rally26.membership.application.MembershipService
import com.rally26.subscription.application.PlanEntitlementService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class QuickBooksAuthorizationAuditTest {
    private val catalog = mockk<IntegrationCatalogService>(relaxed = true)
    private val oauth = mockk<IntegrationOAuthService>()
    private val repository = mockk<QuickBooksRepository>(relaxed = true)
    private val provider = mockk<QuickBooksProviderClient>()
    private val sync = mockk<IntegrationSyncService>(relaxed = true)
    private val membership = mockk<MembershipService>(relaxed = true)
    private val audit = mockk<AuditService>(relaxed = true)
    private val planEntitlementService = mockk<PlanEntitlementService>(relaxed = true)
    private val service =
        QuickBooksService(
            catalog,
            oauth,
            repository,
            provider,
            QuickBooksAccountingMappingPolicy(),
            QuickBooksPostingIntentPolicy(),
            QuickBooksActivationReadinessPolicy(),
            sync,
            membership,
            audit,
            planEntitlementService,
        )

    private val organizationId = UUID.randomUUID()
    private val connectionId = UUID.randomUUID()
    private val currentUser = CurrentUser(UUID.randomUUID(), "owner@example.com", "Owner")

    @Test
    fun `overview authorizes manager before reading QuickBooks organization data`() {
        every { membership.requireManagerRole(organizationId, currentUser) } throws
            ForbiddenException("MEMBERSHIP_MANAGEMENT_DENIED", "Owner or administrator access is required.")

        assertFailsWith<ForbiddenException> {
            service.overview(organizationId, currentUser)
        }

        verify(exactly = 0) { catalog.listForOrganization(any(), any()) }
        verify(exactly = 0) { repository.listBatches(any(), any()) }
        verify(exactly = 0) { repository.listMappings(any()) }
        verify(exactly = 0) { provider.readCompany(any(), any()) }
        verify(exactly = 0) { provider.listAccounts(any(), any()) }
    }

    @Test
    fun `foreign connection is rejected before mapping rows or provider accounts are read`() {
        every {
            oauth.accessTokenForOrganizationConnection(organizationId, connectionId, currentUser)
        } throws NotFoundException("INTEGRATION_CONNECTION_NOT_FOUND", "The integration connection could not be found.")

        assertFailsWith<NotFoundException> {
            service.validateMappings(organizationId, connectionId, currentUser)
        }

        verify(exactly = 0) { repository.listMappings(any()) }
        verify(exactly = 0) { repository.markMappingValidation(any(), any()) }
        verify(exactly = 0) { provider.listAccounts(any(), any()) }
        verify(exactly = 0) {
            audit.record(
                currentUser.userId,
                organizationId,
                "integration.quickbooks_mappings_revalidated",
                "integration_connection",
                connectionId,
            )
        }
    }

    @Test
    fun `successful mapping revalidation records scoped audit evidence without provider secrets`() {
        val access = mockk<IntegrationAccessToken>()
        val connection = mockk<IntegrationConnection>()
        every { access.connection } returns connection
        every { access.accessToken } returns "stub-access-closeout"
        every { connection.externalAccountId } returns "realm-closeout"
        every { connection.provider } returns IntegrationProvider.QUICKBOOKS_ONLINE
        every {
            oauth.accessTokenForOrganizationConnection(organizationId, connectionId, currentUser)
        } returns access
        every { provider.listAccounts("stub-access-closeout", "realm-closeout") } returns listOf(account())
        every { repository.listMappings(connectionId) } returns emptyList()

        val diagnostics = service.validateMappings(organizationId, connectionId, currentUser)

        assertTrue(diagnostics.isNotEmpty())
        verify(exactly = 1) { repository.markAccountsRead(connectionId) }
        verify(exactly = 1) { repository.listMappings(connectionId) }
        verify(exactly = 1) { repository.markMappingValidation(connectionId, false) }
        verify(exactly = 1) {
            audit.record(
                currentUser.userId,
                organizationId,
                "integration.quickbooks_accounts_read",
                "integration_connection",
                connectionId,
            )
        }
        verify(exactly = 1) {
            audit.record(
                currentUser.userId,
                organizationId,
                "integration.quickbooks_mappings_revalidated",
                "integration_connection",
                connectionId,
            )
        }
    }

    private fun account() =
        QuickBooksAccount(
            id = "qb-income-fees",
            name = "Program Fee Income",
            fullyQualifiedName = "Program Fee Income",
            accountType = "Income",
            accountSubType = "ServiceFeeIncome",
            classification = "Revenue",
            active = true,
        )
}
