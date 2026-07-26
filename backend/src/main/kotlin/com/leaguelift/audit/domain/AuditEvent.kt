package com.leaguelift.audit.domain

import java.time.Instant
import java.util.UUID

data class AuditEvent(
	val id: UUID,
	val actorUserId: UUID?,
	val organizationId: UUID?,
	val action: String,
	val entityType: String,
	val entityId: UUID,
	val metadata: String,
	val createdAt: Instant,
)
