package com.rally26.outbox.web

import com.rally26.outbox.domain.OutboxEvent
import java.time.Instant
import java.util.UUID

data class OutboxEventResponse(
	val id: UUID,
	val aggregateType: String,
	val aggregateId: UUID,
	val organizationId: UUID?,
	val eventType: String,
	val status: String,
	val attemptCount: Int,
	val availableAt: Instant,
	val processedAt: Instant?,
	val lastError: String?,
	val createdAt: Instant,
)

fun OutboxEvent.toResponse(): OutboxEventResponse = OutboxEventResponse(
	id = id,
	aggregateType = aggregateType,
	aggregateId = aggregateId,
	organizationId = organizationId,
	eventType = eventType,
	status = status.name,
	attemptCount = attemptCount,
	availableAt = availableAt,
	processedAt = processedAt,
	lastError = lastError,
	createdAt = createdAt,
)
