package com.leaguelift.integration.core.application

import com.leaguelift.common.web.CurrentUser
import com.leaguelift.config.IntegrationProperties
import com.leaguelift.config.PrintifyProperties
import com.leaguelift.config.ResendProperties
import com.leaguelift.integration.core.domain.IntegrationProvider
import org.springframework.stereotype.Service

enum class ProviderContractCapabilityStatus { READY, SCAFFOLDED, NOT_CONFIGURED, NOT_APPLICABLE }

data class ProviderContractCapability(
    val name: String,
    val status: ProviderContractCapabilityStatus,
    val summary: String,
)

data class PlatformProviderContract(
    val provider: IntegrationProvider,
    val configurationStatus: PlatformIntegrationConfigurationStatus,
    val credentialOwnership: String,
    val credentialRotation: String,
    val healthCheckMode: String,
    val liveProbeEnabled: Boolean,
    val webhookVerification: String,
    val idempotency: String,
    val productionStubBlocked: Boolean,
    val capabilities: List<ProviderContractCapability>,
)

/**
 * Code/contract readiness only. Phase 19 never turns a configured secret into a
 * fabricated live health result; Phase 20 owns credentialed probes and verified
 * webhook lifecycle tests.
 */
@Service
class PlatformProviderContractService(
    private val readinessService: PlatformIntegrationReadinessService,
    private val integrationProperties: IntegrationProperties,
    private val printifyProperties: PrintifyProperties,
    private val resendProperties: ResendProperties,
) {
    fun list(currentUser: CurrentUser): List<PlatformProviderContract> {
        val readiness = readinessService.list(currentUser).associateBy { it.provider }
        return listOf(
            contract(
                IntegrationProvider.STRIPE,
                readiness,
                "Environment secret + webhook secret",
                "Environment/KMS rotation with application restart",
                "Configuration-only until Phase 20",
                "Stripe-Signature verification is active for the existing webhook inbox",
                "Provider event ID and source transaction identity",
                listOf(
                    capability("Payments and refunds", ProviderContractCapabilityStatus.READY, "Existing Stripe payment/refund seams remain intact."),
                    capability("Connect account health", ProviderContractCapabilityStatus.SCAFFOLDED, "Account-health mapping is modeled; credentialed webhook rehearsal remains Phase 20."),
                    capability("Subscription billing", ProviderContractCapabilityStatus.SCAFFOLDED, "Platform subscription adapter boundary is reserved; no customer-facing Stripe credential flow exists."),
                ),
            ),
            contract(
                IntegrationProvider.PRINTIFY,
                readiness,
                "LeagueLift platform API token",
                "Replace token in managed secret storage; never organization-entered",
                "Configuration/shop readiness only until Phase 20",
                if (printifyProperties.webhookSecret.isNotBlank()) "HMAC secret configured; official event contract still requires verification" else "HMAC verifier seam scaffolded; secret not configured",
                "Provider order identity and LeagueLift fulfillment identity",
                listOf(
                    capability("Catalog and draft orders", ProviderContractCapabilityStatus.READY, "Existing catalog/product/draft-order clients are preserved."),
                    capability("Shipment webhooks", ProviderContractCapabilityStatus.SCAFFOLDED, "Signature, replay, and normalized status contracts are ready; official payload fixtures remain Phase 20."),
                    capability("Token rotation", ProviderContractCapabilityStatus.SCAFFOLDED, "Configuration can be rotated without exposing the token to organizations."),
                ),
            ),
            contract(
                IntegrationProvider.RESEND,
                readiness,
                "LeagueLift email API key and verified sender domain",
                "Managed secret rotation",
                "Configuration/delivery-result readiness only",
                if (resendProperties.webhookSecret.isNotBlank()) "Webhook signing secret configured; official event fixture verification remains Phase 20" else "Bounce/complaint webhook verifier seam scaffolded",
                "Outbox event identity and provider idempotency header",
                listOf(
                    capability("Transactional delivery", ProviderContractCapabilityStatus.READY, "Existing EmailProvider/outbox adapter is preserved."),
                    capability("Bounce and complaint events", ProviderContractCapabilityStatus.SCAFFOLDED, "Normalized result contracts are ready; domain activation remains Phase 20."),
                ),
            ),
            contract(
                IntegrationProvider.TWILIO,
                readiness,
                "LeagueLift account SID/auth token/from number",
                "Managed secret rotation",
                "Configuration/delivery-result readiness only",
                "Twilio request-signature verifier seam scaffolded using the platform auth token",
                "Outbox event identity and provider message identity",
                listOf(
                    capability("Opted-in transactional SMS", ProviderContractCapabilityStatus.READY, "Existing SMS provider interface is preserved."),
                    capability("Delivery callbacks", ProviderContractCapabilityStatus.SCAFFOLDED, "Normalized undeliverable/opt-out contracts are ready; live callback verification remains Phase 20."),
                ),
            ),
            contract(
                IntegrationProvider.DIGITALOCEAN_SPACES,
                readiness,
                "LeagueLift S3-compatible access key and secret",
                "Managed key rotation",
                "Configuration-only storage readiness",
                "Not applicable",
                "Signed upload key and object identity",
                listOf(
                    capability("Signed uploads", ProviderContractCapabilityStatus.READY, "Existing FileStorageProvider seam is preserved."),
                    capability("Read/write/delete probes", ProviderContractCapabilityStatus.SCAFFOLDED, "Credentialed bucket probes remain Phase 20."),
                ),
            ),
        )
    }

    private fun contract(
        provider: IntegrationProvider,
        readiness: Map<IntegrationProvider, PlatformIntegrationReadiness>,
        ownership: String,
        rotation: String,
        health: String,
        webhook: String,
        idempotency: String,
        capabilities: List<ProviderContractCapability>,
    ) = PlatformProviderContract(
        provider,
        readiness[provider]?.status ?: PlatformIntegrationConfigurationStatus.NOT_CONFIGURED,
        ownership,
        rotation,
        health,
        liveProbeEnabled = false,
        webhookVerification = webhook,
        idempotency = idempotency,
        productionStubBlocked = !integrationProperties.stubMode,
        capabilities = capabilities,
    )

    private fun capability(name: String, status: ProviderContractCapabilityStatus, summary: String) =
        ProviderContractCapability(name, status, summary)
}
