package com.rally26.outbox.domain

import java.time.Instant
import java.util.UUID

enum class OutboxEventStatus { PENDING, PROCESSING, PROCESSED, FAILED, DEAD_LETTER }

data class OutboxEvent(
	val id: UUID,
	val aggregateType: String,
	val aggregateId: UUID,
	val organizationId: UUID?,
	val eventType: String,
	val schemaVersion: Int,
	val payload: String,
	val status: OutboxEventStatus,
	val attemptCount: Int,
	val availableAt: Instant,
	val processedAt: Instant?,
	val lastError: String?,
	val createdAt: Instant,
)
