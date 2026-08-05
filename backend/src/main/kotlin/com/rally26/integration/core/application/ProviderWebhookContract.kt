package com.rally26.integration.core.application

import com.rally26.integration.core.domain.IntegrationProvider
import org.springframework.stereotype.Component

data class ProviderWebhookContract(
    val provider: IntegrationProvider,
    val signatureMode: String,
    val eventIdentityField: String,
    val replayProtection: String,
    val officialPayloadFixtureVerified: Boolean,
)

data class ProviderWebhookVerificationRequest(
    val provider: IntegrationProvider,
    val headers: Map<String, String>,
    val rawBody: ByteArray,
)

data class ProviderWebhookVerificationResult(
    val verified: Boolean,
    val externalEventId: String? = null,
    val eventType: String? = null,
    val errorCode: String? = null,
    val errorMessage: String? = null,
)

/**
 * Provider-neutral signed-callback seam. Phase 19 deliberately supplies no
 * invented Printify/Resend/Twilio implementation: an official verifier is
 * registered only after Phase 20 fixture and signature-contract verification.
 */
interface ProviderWebhookVerifier {
    fun supports(provider: IntegrationProvider): Boolean

    fun verify(request: ProviderWebhookVerificationRequest): ProviderWebhookVerificationResult
}

@Component
class ProviderWebhookVerifierRegistry(
    private val verifiers: List<ProviderWebhookVerifier>,
) {
    fun find(provider: IntegrationProvider): ProviderWebhookVerifier? = verifiers.firstOrNull { it.supports(provider) }
}

/** Metadata-only registry used by Platform Admin readiness. It does not accept a
 * webhook or invent a provider payload; real handlers must verify official contracts. */
object ProviderWebhookContracts {
    val all =
        listOf(
            ProviderWebhookContract(
                IntegrationProvider.STRIPE,
                "Stripe-Signature",
                "event.id",
                "webhook_event(provider, external_event_id)",
                true,
            ),
            ProviderWebhookContract(
                IntegrationProvider.PRINTIFY,
                "HMAC scaffold",
                "provider event ID",
                "webhook_event(provider, external_event_id)",
                false,
            ),
            ProviderWebhookContract(
                IntegrationProvider.RESEND,
                "Signing-secret scaffold",
                "provider event ID",
                "webhook_event(provider, external_event_id)",
                false,
            ),
            ProviderWebhookContract(
                IntegrationProvider.TWILIO,
                "X-Twilio-Signature scaffold",
                "message/event identity",
                "webhook_event(provider, external_event_id)",
                false,
            ),
        )
}
