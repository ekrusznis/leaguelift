package com.leaguelift.activity.web

import java.time.Instant
import java.util.UUID

data class ActivityFeedItem(
	val id: UUID,
	val organizationId: UUID?,
	val organizationName: String?,
	val action: String,
	val entityType: String,
	val entityId: UUID,
	val occurredAt: Instant,
)

data class ActivityFeedResponse(val items: List<ActivityFeedItem>)
