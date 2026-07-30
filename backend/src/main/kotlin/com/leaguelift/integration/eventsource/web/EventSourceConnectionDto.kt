package com.leaguelift.integration.eventsource.web

import com.leaguelift.integration.eventsource.domain.EventSourceConnection
import jakarta.validation.constraints.NotBlank
import java.time.Instant
import java.util.UUID

data class ConnectIcsFeedRequest(
	@field:NotBlank val label: String,
	@field:NotBlank val feedUrl: String,
)

data class EventSourceConnectionResponse(
	val id: UUID,
	val provider: String,
	val label: String,
	val feedUrl: String?,
	val status: String,
	val lastSyncedAt: Instant?,
	val lastSyncStatus: String?,
	val lastSyncError: String?,
	val createdAt: Instant,
)

fun EventSourceConnection.toResponse() = EventSourceConnectionResponse(
	id = id,
	provider = provider.name,
	label = label,
	feedUrl = feedUrl,
	status = status.name,
	lastSyncedAt = lastSyncedAt,
	lastSyncStatus = lastSyncStatus?.name,
	lastSyncError = lastSyncError,
	createdAt = createdAt,
)
