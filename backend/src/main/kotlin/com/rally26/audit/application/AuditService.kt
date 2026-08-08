package com.rally26.audit.application

import com.rally26.audit.domain.AuditActorType
import com.rally26.audit.domain.AuditResult
import com.rally26.audit.persistence.AuditEventRepository
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Records immutable audit events inside the same transaction as the state change being audited.
 *
 * The original six-argument call shape remains source-compatible. Phase 27 callers can opt into
 * explicit team/household/participant/target-user scope as domains are upgraded to richer history.
 */
@Service
class AuditService(
    private val auditEventRepository: AuditEventRepository,
) {
    fun record(
        actorUserId: UUID?,
        organizationId: UUID?,
        action: String,
        entityType: String,
        entityId: UUID,
        metadataJson: String = "{}",
        teamId: UUID? = null,
        householdId: UUID? = null,
        participantId: UUID? = null,
        targetUserId: UUID? = null,
        actorType: AuditActorType = if (actorUserId == null) AuditActorType.SYSTEM else AuditActorType.USER,
        result: AuditResult = AuditResult.SUCCESS,
        summary: String = action,
        correlationId: UUID? = null,
    ) {
        auditEventRepository.insert(
            actorUserId = actorUserId,
            organizationId = organizationId,
            action = action,
            entityType = entityType,
            entityId = entityId,
            metadataJson = metadataJson,
            teamId = teamId,
            householdId = householdId,
            participantId = participantId,
            targetUserId = targetUserId,
            actorType = actorType,
            result = result,
            summary = summary,
            correlationId = correlationId,
        )
    }
}
