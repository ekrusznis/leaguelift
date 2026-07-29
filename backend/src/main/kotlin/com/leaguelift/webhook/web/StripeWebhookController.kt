package com.leaguelift.webhook.web

import com.leaguelift.config.StripeProperties
import com.leaguelift.fundraising.application.ContributionService
import com.leaguelift.webhook.domain.WebhookProcessingStatus
import com.leaguelift.webhook.persistence.WebhookEventRepository
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
 * contributions (`fundraising/application/ContributionService.kt`) — Stripe
 * Connect account-status webhooks remain Phase 5. Processed synchronously inline
 * (no outbox-consumer worker exists yet, and doesn't need to for one lightweight
 * event type): a genuine processing failure returns 500 so Stripe's own automatic
 * retry schedule covers it, rather than us building a retry worker for this.
 */
@RestController
@RequestMapping("/api/v1/webhooks/stripe")
class StripeWebhookController(
	private val stripeProperties: StripeProperties,
	private val webhookEventRepository: WebhookEventRepository,
	private val contributionService: ContributionService,
) {

	@PostMapping
	fun receive(
		@RequestBody payload: String,
		@RequestHeader("Stripe-Signature") signatureHeader: String,
	): ResponseEntity<Void> {
		val event = try {
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
		var failureMessage: String? = null
		val outcome = try {
			when (event.type) {
				"checkout.session.completed" -> {
					val session = event.dataObjectDeserializer.getObject().orElseThrow {
						IllegalStateException("Unable to deserialize checkout.session.completed payload")
					} as Session
					val contribution = contributionService.confirmFromWebhook(session.id, session.paymentStatus)
					relatedEntityId = contribution?.id
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
			relatedEntityType = if (relatedEntityId != null) "contribution" else null,
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
		MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
			.joinToString("") { "%02x".format(it) }
}
