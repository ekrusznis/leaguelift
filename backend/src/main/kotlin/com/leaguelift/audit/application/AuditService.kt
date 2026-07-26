package com.leaguelift.audit.application

import com.leaguelift.audit.persistence.AuditEventRepository
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Records immutable audit events. Audit records are never updated or deleted
 * (DESIGN-DOC.md sections 14.1, 26.4 — audit history must be preservable/immutable).
 * Called from within the same transaction as the state change being audited.
 */
@Service
class AuditService(private val auditEventRepository: AuditEventRepository) {

	fun record(
		actorUserId: UUID?,
		organizationId: UUID?,
		action: String,
		entityType: String,
		entityId: UUID,
		metadataJson: String = "{}",
	) {
		auditEventRepository.insert(actorUserId, organizationId, action, entityType, entityId, metadataJson)
	}
}
