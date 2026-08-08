package com.rally26.audit.domain

import java.time.Instant
import java.util.UUID

enum class AuditActorType {
    USER,
    SYSTEM,
    PROVIDER,
}

enum class AuditResult {
    SUCCESS,
    FAILURE,
    DENIED,
    PARTIAL,
}

data class AuditEvent(
    val id: UUID,
    val actorUserId: UUID?,
    val organizationId: UUID?,
    val action: String,
    val entityType: String,
    val entityId: UUID,
    val metadata: String,
    val createdAt: Instant,
    val teamId: UUID? = null,
    val householdId: UUID? = null,
    val participantId: UUID? = null,
    val targetUserId: UUID? = null,
    val actorType: AuditActorType = if (actorUserId == null) AuditActorType.SYSTEM else AuditActorType.USER,
    val result: AuditResult = AuditResult.SUCCESS,
    val summary: String = action,
    val correlationId: UUID? = null,
)
