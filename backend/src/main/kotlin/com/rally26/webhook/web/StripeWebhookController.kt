package com.rally26.webhook.web

import com.rally26.config.StripeProperties
import com.rally26.fundraising.application.ContributionService
import com.rally26.order.application.OrderService
import com.rally26.order.domain.ShippingAddress
import com.rally26.sponsorship.application.SponsorshipService
import com.rally26.webhook.domain.WebhookProcessingStatus
import com.rally26.webhook.persistence.WebhookEventRepository
import com.stripe.exception.SignatureVerificationException
import com.stripe.model.checkout.Session
import com.stripe.net.Webhook
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.security.MessageDigest
import java.util.UUID

private val log = LoggerFactory.getLogger(StripeWebhookController::class.java)
private const val PROVIDER = "stripe"

/**
 * Inbound Stripe webhook receiver (DESIGN-DOC.md section 17). Only
 * `checkout.session.completed` is consumed this slice, to confirm campaign
 * contributions (`fundraising/application/ContributionService.kt`), store
 * orders (`order/application/OrderService.kt`), and sponsorship purchases
 * (`sponsorship/application/SponsorshipService.kt`) — Stripe Connect account-status
 * webhooks remain Phase 5. A session's own metadata (`orderId`/`sponsorshipId`
 * present or not) disambiguates which one a given event is for, since all three use
 * the same event type. Processed synchronously inline (no outbox-consumer worker
 * exists yet, and doesn't need to for one lightweight event type): a genuine
 * processing failure returns 500 so Stripe's own automatic retry schedule covers
 * it, rather than us building a retry worker for this.
 */
@RestController
@RequestMapping("/api/v1/webhooks/stripe")
class StripeWebhookController(
    private val stripeProperties: StripeProperties,
    private val webhookEventRepository: WebhookEventRepository,
    private val contributionService: ContributionService,
    private val orderService: OrderService,
    private val sponsorshipService: SponsorshipService,
) {
    @PostMapping
    fun receive(
        @RequestBody payload: String,
        @RequestHeader("Stripe-Signature") signatureHeader: String,
    ): ResponseEntity<Void> {
        val event =
            try {
                Webhook.constructEvent(payload, signatureHeader, stripeProperties.webhookSecret)
            } catch (e: SignatureVerificationException) {
                log.warn("Stripe webhook signature verification failed: {}", e.message)
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build()
            }

        val existing = webhookEventRepository.findExisting(PROVIDER, event.id)
        if (existing != null) {
            // Stripe's own automatic retry of an event we already recorded — not an error.
            return ResponseEntity.ok().build()
        }

        val payloadHash = sha256Hex(payload)
        var relatedEntityId: UUID? = null
        var relatedEntityType: String? = null
        var failureMessage: String? = null
        val outcome =
            try {
                when (event.type) {
                    "checkout.session.completed" -> {
                        // deserializeUnsafe (not getObject) deliberately skips Stripe's
                        // API-version compatibility check and parses the raw JSON directly
                        // — getObject() returns an empty Optional whenever the webhook
                        // event's own api_version differs from the one stripe-java was
                        // compiled against (Stripe's own documented workaround for this).
                        // The Session fields this handler reads (id/paymentStatus/
                        // paymentIntent/metadata/address) are stable across versions.
                        val session = event.dataObjectDeserializer.deserializeUnsafe() as Session
                        if (session.metadata?.containsKey("orderId") == true) {
                            relatedEntityType = "order"
                            relatedEntityId =
                                orderService
                                    .confirmFromWebhook(
                                        session.id,
                                        session.paymentStatus,
                                        shippingAddressOf(session),
                                        session.paymentIntent,
                                    )?.id
                        } else if (session.metadata?.containsKey("sponsorshipId") == true) {
                            relatedEntityType = "sponsorship"
                            relatedEntityId =
                                sponsorshipService.confirmFromWebhook(session.id, session.paymentStatus, session.paymentIntent)?.id
                        } else {
                            relatedEntityType = "contribution"
                            relatedEntityId =
                                contributionService.confirmFromWebhook(session.id, session.paymentStatus, session.paymentIntent)?.id
                        }
                        WebhookProcessingStatus.PROCESSED
                    }
                    else -> WebhookProcessingStatus.IGNORED
                }
            } catch (e: Exception) {
                log.error("Failed to process Stripe webhook event {} ({}): {}", event.id, event.type, e.message, e)
                failureMessage = e.message ?: e.javaClass.simpleName
                WebhookProcessingStatus.FAILED
            }

        webhookEventRepository.insert(
            provider = PROVIDER,
            externalEventId = event.id,
            eventType = event.type,
            payload = payload,
            payloadHash = payloadHash,
            signatureVerified = true,
            processingStatus = outcome,
            relatedEntityType = if (relatedEntityId != null) relatedEntityType else null,
            relatedEntityId = relatedEntityId,
            lastError = failureMessage,
        )

        return if (outcome == WebhookProcessingStatus.FAILED) {
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        } else {
            ResponseEntity.ok().build()
        }
    }

    private fun sha256Hex(value: String): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    /**
     * Stripe Checkout's shipping_address_collection surfaces the collected address
     * on `customer_details.address` (this SDK version has no separate top-level
     * "shipping" field — confirmed against the actual Session model class).
     */
    private fun shippingAddressOf(session: Session): ShippingAddress? {
        val address = session.customerDetails?.address ?: return null
        return ShippingAddress(
            name = session.customerDetails?.name,
            line1 = address.line1,
            line2 = address.line2,
            city = address.city,
            state = address.state,
            postalCode = address.postalCode,
            country = address.country,
        )
    }
}
