package com.rally26.integration.core.application

import com.rally26.common.error.ServiceUnavailableException
import com.rally26.integration.core.domain.IntegrationProvider
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame

class PlatformProviderAdapterTest {
    @Test
    fun `subscription billing fails closed until Stripe activation`() {
        val provider = DisabledPlatformSubscriptionBillingProvider()
        val error = assertFailsWith<ServiceUnavailableException> {
            provider.createCustomer(
                PlatformSubscriptionCustomerRequest(UUID.randomUUID(), "billing@example.com", "Test Club", "customer-1"),
            )
        }
        assertEquals("PLATFORM_SUBSCRIPTION_BILLING_NOT_ACTIVATED", error.code)
    }

    @Test
    fun `webhook verifier registry exposes only registered official verifier`() {
        val verifier = object : ProviderWebhookVerifier {
            override fun supports(provider: IntegrationProvider) = provider == IntegrationProvider.STRIPE
            override fun verify(request: ProviderWebhookVerificationRequest) = ProviderWebhookVerificationResult(true, "evt_1", "test")
        }
        val registry = ProviderWebhookVerifierRegistry(listOf(verifier))
        assertSame(verifier, registry.find(IntegrationProvider.STRIPE))
        assertNull(registry.find(IntegrationProvider.PRINTIFY))
    }
}
