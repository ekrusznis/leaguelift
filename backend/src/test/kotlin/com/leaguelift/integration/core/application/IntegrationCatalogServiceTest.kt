package com.leaguelift.integration.core.application

import com.leaguelift.config.IntegrationProperties
import com.leaguelift.config.IntegrationProviderRuntimeProperties
import com.leaguelift.integration.core.domain.IntegrationAdapterMode
import com.leaguelift.integration.core.domain.IntegrationAuthMode
import com.leaguelift.integration.core.domain.IntegrationCategory
import com.leaguelift.integration.core.domain.IntegrationOwnerType
import com.leaguelift.integration.core.domain.IntegrationProvider
import com.leaguelift.integration.core.domain.IntegrationProviderDefinition
import com.leaguelift.integration.core.domain.IntegrationReadiness
import com.leaguelift.integration.core.persistence.IntegrationConnectionRepository
import com.leaguelift.integration.core.persistence.IntegrationProviderRepository
import com.leaguelift.membership.application.MembershipService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals

class IntegrationCatalogServiceTest {
    private val providerRepository = mockk<IntegrationProviderRepository>()
    private val connectionRepository = mockk<IntegrationConnectionRepository>()
    private val membershipService = mockk<MembershipService>()
    private val adapterRegistry = mockk<IntegrationAdapterRegistry>()

    @Test
    fun `oauth scaffold remains not configured without runtime activation`() {
        val service = IntegrationCatalogService(
            providerRepository,
            connectionRepository,
            membershipService,
            IntegrationProperties(),
            adapterRegistry,
        )
        assertEquals(IntegrationReadiness.NOT_CONFIGURED, service.readiness(quickBooksDefinition()))
        verify(exactly = 0) { adapterRegistry.find(any()) }
    }

    @Test
    fun `local configured adapter becomes available without claiming connected`() {
        val adapter = mockk<IntegrationAuthorizationAdapter>()
        every { adapterRegistry.find(IntegrationProvider.QUICKBOOKS_ONLINE) } returns adapter
        val service = IntegrationCatalogService(
            providerRepository,
            connectionRepository,
            membershipService,
            IntegrationProperties(
                stubMode = true,
                providers = mapOf(
                    "quickbooks-online" to IntegrationProviderRuntimeProperties(enabled = true, clientId = "local-client"),
                ),
            ),
            adapterRegistry,
        )
        assertEquals(IntegrationReadiness.AVAILABLE, service.readiness(quickBooksDefinition()))
    }

    @Test
    fun `partner pending baseline cannot be promoted by configuration`() {
        val definition = quickBooksDefinition().copy(
            provider = IntegrationProvider.GAMECHANGER,
            primaryAuthMode = IntegrationAuthMode.FILE_IMPORT,
            baselineReadiness = IntegrationReadiness.PARTNER_PENDING,
            adapterMode = IntegrationAdapterMode.PARTNER_PENDING,
        )
        val service = IntegrationCatalogService(
            providerRepository,
            connectionRepository,
            membershipService,
            IntegrationProperties(),
            adapterRegistry,
        )
        assertEquals(IntegrationReadiness.PARTNER_PENDING, service.readiness(definition))
    }

    private fun quickBooksDefinition() = IntegrationProviderDefinition(
        provider = IntegrationProvider.QUICKBOOKS_ONLINE,
        displayName = "QuickBooks Online",
        category = IntegrationCategory.ACCOUNTING,
        ownershipScope = IntegrationOwnerType.ORGANIZATION,
        primaryAuthMode = IntegrationAuthMode.OAUTH2,
        supportedAuthModes = listOf(IntegrationAuthMode.OAUTH2),
        baselineReadiness = IntegrationReadiness.NOT_CONFIGURED,
        adapterMode = IntegrationAdapterMode.OAUTH_SCAFFOLD,
        description = "Accounting scaffold",
        activationRequirement = "Phase 20 credentials",
        defaultScopes = emptyList(),
        sortOrder = 1,
        visibleToCustomers = true,
    )
}
