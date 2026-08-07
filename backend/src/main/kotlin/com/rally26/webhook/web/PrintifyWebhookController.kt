package com.rally26.webhook.web

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.ObjectMapper
import com.rally26.config.PrintifyProperties
import com.rally26.order.application.FulfillmentOperationsService
import com.rally26.order.domain.FulfillmentStatus
import com.rally26.webhook.domain.WebhookProcessingStatus
import com.rally26.webhook.persistence.WebhookEventRepository
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
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

private val log = LoggerFactory.getLogger(PrintifyWebhookController::class.java)
private const val PROVIDER = "printify"
private const val HMAC_ALGORITHM = "HmacSHA256"

@JsonIgnoreProperties(ignoreUnknown = true)
data class PrintifyWebhookResource(
    val id: String? = null,
    val type: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PrintifyWebhookEnvelope(
    val id: String,
    val type: String,
    val resource: PrintifyWebhookResource? = null,
)

/**
 * Inbound Printify webhook receiver (Phase 24 slice 24.4, DESIGN-DOC.md
 * section 14.1G, ADR-070). Mirrors `StripeWebhookController`'s shape
 * exactly: HMAC signature verification, `webhook_event` dedupe via
 * `(provider, external_event_id)`, and resolution via a value *we* minted
 * and made DB-unique at draft-creation time (`fulfillment.printify_order_id`,
 * migration V51) — never by trusting organization/entity claims embedded
 * in the payload itself.
 *
 * Real-world context this receiver does not currently have live traffic
 * from: `OrderService` deliberately never calls Printify's
 * `send_to_production.json` (existing ADR-backed scope — orders stay
 * drafts), so none of the event types below will fire for orders this
 * system creates today. This receiver exists to satisfy the isolation/
 * scoping acceptance criterion and make future activation config-only,
 * not because it has real signal now.
 *
 * FLAGGED ASSUMPTION, not verified against Printify's live docs in this
 * session: the signature header name/encoding and the envelope/`resource`
 * field names below are a best-effort guess, not fabricated with false
 * confidence. Both must be confirmed — and this file corrected if they
 * differ — before `PRINTIFY_WEBHOOK_ENABLED` is ever set to `true` in
 * production. See ADR-070's manual token-rotation runbook.
 */
@RestController
@RequestMapping("/api/v1/webhooks/printify")
class PrintifyWebhookController(
    private val printifyProperties: PrintifyProperties,
    private val webhookEventRepository: WebhookEventRepository,
    private val fulfillmentOperationsService: FulfillmentOperationsService,
    private val objectMapper: ObjectMapper,
) {
    @PostMapping
    fun receive(
        @RequestBody payload: String,
        @RequestHeader("X-Pfy-Signature") signatureHeader: String,
    ): ResponseEntity<Void> {
        if (!printifyProperties.webhookEnabled || printifyProperties.webhookSecret.isBlank()) {
            // No active Printify webhook subscription exists today — nothing
            // legitimate should ever reach this URL, so a 404 avoids exposing an
            // unauthenticated processing path before a real secret is configured.
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build()
        }
        if (!verifySignature(payload, signatureHeader)) {
            log.warn("Printify webhook signature verification failed")
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build()
        }

        val envelope =
            try {
                objectMapper.readValue(payload, PrintifyWebhookEnvelope::class.java)
            } catch (e: Exception) {
                log.warn("Failed to parse Printify webhook payload: {}", e.message)
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build()
            }

        val existing = webhookEventRepository.findExisting(PROVIDER, envelope.id)
        if (existing != null) {
            // Printify's own automatic retry of an event we already recorded — not an error.
            return ResponseEntity.ok().build()
        }

        val payloadHash = sha256Hex(payload)
        var relatedEntityId: UUID? = null
        var failureMessage: String? = null
        val outcome =
            try {
                val resourceId = envelope.resource?.id
                val newStatus =
                    when (envelope.type) {
                        "order:sent-to-production" -> FulfillmentStatus.IN_PRODUCTION
                        "order:shipment:created" -> FulfillmentStatus.SHIPPED
                        "order:shipment:delivered" -> FulfillmentStatus.DELIVERED
                        else -> null
                    }
                if (newStatus != null && resourceId != null) {
                    val fulfillment =
                        fulfillmentOperationsService.applyProviderStatusUpdate(
                            printifyOrderId = resourceId,
                            newStatus = newStatus,
                            carrier = null,
                            trackingNumber = null,
                            trackingUrl = null,
                            note = "Updated by Printify webhook (${envelope.type}).",
                        )
                    relatedEntityId = fulfillment?.id
                    if (fulfillment != null) WebhookProcessingStatus.PROCESSED else WebhookProcessingStatus.IGNORED
                } else {
                    WebhookProcessingStatus.IGNORED
                }
            } catch (e: Exception) {
                log.error("Failed to process Printify webhook event {} ({}): {}", envelope.id, envelope.type, e.message, e)
                failureMessage = e.message ?: e.javaClass.simpleName
                WebhookProcessingStatus.FAILED
            }

        webhookEventRepository.insert(
            provider = PROVIDER,
            externalEventId = envelope.id,
            eventType = envelope.type,
            payload = payload,
            payloadHash = payloadHash,
            signatureVerified = true,
            processingStatus = outcome,
            relatedEntityType = if (relatedEntityId != null) "fulfillment" else null,
            relatedEntityId = relatedEntityId,
            lastError = failureMessage,
        )

        return if (outcome == WebhookProcessingStatus.FAILED) {
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        } else {
            ResponseEntity.ok().build()
        }
    }

    private fun verifySignature(
        payload: String,
        signatureHeader: String,
    ): Boolean {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(SecretKeySpec(printifyProperties.webhookSecret.toByteArray(Charsets.UTF_8), HMAC_ALGORITHM))
        val expectedHex = mac.doFinal(payload.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        return MessageDigest.isEqual(expectedHex.toByteArray(Charsets.UTF_8), signatureHeader.trim().toByteArray(Charsets.UTF_8))
    }

    private fun sha256Hex(value: String): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
