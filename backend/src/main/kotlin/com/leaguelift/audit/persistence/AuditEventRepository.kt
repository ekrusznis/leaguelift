package com.leaguelift.audit.persistence

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class AuditEventRepository(private val jdbcClient: JdbcClient) {

	fun insert(
		actorUserId: UUID?,
		organizationId: UUID?,
		action: String,
		entityType: String,
		entityId: UUID,
		metadataJson: String,
	) {
		jdbcClient.sql(
			"""
			insert into audit_event (id, actor_user_id, organization_id, action, entity_type, entity_id, metadata, created_at)
			values (:id, :actorUserId, :organizationId, :action, :entityType, :entityId, cast(:metadata as jsonb), now())
			""".trimIndent(),
		)
			.param("id", UUID.randomUUID())
			.param("actorUserId", actorUserId)
			.param("organizationId", organizationId)
			.param("action", action)
			.param("entityType", entityType)
			.param("entityId", entityId)
			.param("metadata", metadataJson)
			.update()
	}
}
