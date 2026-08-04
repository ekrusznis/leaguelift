package com.rally26.integration.core.application

import com.rally26.common.error.ServiceUnavailableException
import com.rally26.integration.core.domain.IntegrationProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.stereotype.Component
import java.util.UUID

data class PlatformProviderProbeResult(
    val healthy: Boolean,
    val latencyMs: Long? = null,
    val errorCode: String? = null,
    val errorMessage: String? = null,
)

/**
 * Phase 19 seam for credentialed platform-provider probes. Platform Operations
 * exposes configuration readiness only; callers must not invoke a live probe until
 * the provider-specific Phase 20 activation flag and contract tests exist.
 */
interface PlatformProviderHealthAdapter {
    fun supports(provider: IntegrationProvider): Boolean
    fun probe(): PlatformProviderProbeResult
}

data class PlatformSubscriptionCustomerRequest(
    val organizationId: UUID,
    val billingEmail: String,
    val displayName: String,
    val idempotencyKey: String,
)

data class PlatformSubscriptionRequest(
    val organizationId: UUID,
    val externalCustomerId: String,
    val planCode: String,
    val idempotencyKey: String,
)

data class PlatformSubscriptionReference(
    val externalId: String,
    val status: String,
)

/** Stripe-backed Rally26 subscription billing seam. It is platform-operated;
 * organizations never enter Stripe credentials or see an integration connection. */
interface PlatformSubscriptionBillingProvider {
    fun createCustomer(request: PlatformSubscriptionCustomerRequest): PlatformSubscriptionReference
    fun createSubscription(request: PlatformSubscriptionRequest): PlatformSubscriptionReference
    fun cancelAtPeriodEnd(externalSubscriptionId: String, idempotencyKey: String): PlatformSubscriptionReference
}

/**
 * Fail-closed Phase 19 implementation. The official Stripe subscription calls are
 * introduced only after products/prices, test keys, webhook events, and cancellation
 * recovery are verified in Phase 20.
 */
@Component
@ConditionalOnMissingBean(PlatformSubscriptionBillingProvider::class)
class DisabledPlatformSubscriptionBillingProvider : PlatformSubscriptionBillingProvider {
    override fun createCustomer(request: PlatformSubscriptionCustomerRequest): PlatformSubscriptionReference = unavailable()
    override fun createSubscription(request: PlatformSubscriptionRequest): PlatformSubscriptionReference = unavailable()
    override fun cancelAtPeriodEnd(externalSubscriptionId: String, idempotencyKey: String): PlatformSubscriptionReference = unavailable()

    private fun unavailable(): Nothing = throw ServiceUnavailableException(
        "PLATFORM_SUBSCRIPTION_BILLING_NOT_ACTIVATED",
        "Rally26 subscription billing is scaffolded but has not been activated against verified Stripe products and webhook contracts.",
    )
}
