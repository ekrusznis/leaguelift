package com.rally26.webhook.domain

import java.time.Instant
import java.util.UUID

enum class WebhookProcessingStatus { PROCESSED, FAILED, IGNORED }

/**
 * An inbound provider webhook, recorded for idempotency and traceability
 * (DESIGN-DOC.md section 17). The unique `(provider, externalEventId)` pair is the
 * guard against a provider's automatic webhook retries reprocessing the same event.
 */
data class WebhookEvent(
	val id: UUID,
	val provider: String,
	val externalEventId: String,
	val eventType: String,
	val payload: String,
	val payloadHash: String,
	val signatureVerified: Boolean,
	val processingStatus: WebhookProcessingStatus,
	val attemptCount: Int,
	val relatedEntityType: String?,
	val relatedEntityId: UUID?,
	val receivedAt: Instant,
	val processedAt: Instant?,
	val lastError: String?,
)
