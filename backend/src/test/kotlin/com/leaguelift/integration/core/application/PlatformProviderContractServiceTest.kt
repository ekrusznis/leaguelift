package com.leaguelift.integration.core.application

import com.leaguelift.common.web.CurrentUser
import com.leaguelift.config.IntegrationProperties
import com.leaguelift.config.PrintifyProperties
import com.leaguelift.config.ResendProperties
import com.leaguelift.integration.core.domain.IntegrationProvider
import io.mockk.every
import io.mockk.mockk
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlatformProviderContractServiceTest {
    private val readiness = mockk<PlatformIntegrationReadinessService>()
    private val currentUser = CurrentUser(UUID.randomUUID(), "admin@example.com", "Admin", platformAdministrator = true)

    @Test
    fun `contract readiness never claims a live probe in phase 19`() {
        every { readiness.list(currentUser) } returns listOf(
            PlatformIntegrationReadiness(IntegrationProvider.STRIPE, "Stripe", "PAYMENTS", PlatformIntegrationConfigurationStatus.CONFIGURED, "secret", "configured", emptyList()),
        )
        val service = PlatformProviderContractService(
            readiness,
            IntegrationProperties(stubMode = false),
            PrintifyProperties("token", "shop"),
            ResendProperties(),
        )

        val stripe = service.list(currentUser).first { it.provider == IntegrationProvider.STRIPE }

        assertFalse(stripe.liveProbeEnabled)
        assertTrue(stripe.productionStubBlocked)
    }
}
