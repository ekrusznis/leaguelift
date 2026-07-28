package com.leaguelift.audit.persistence

import com.leaguelift.audit.domain.AuditEvent
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class AuditEventRepository(private val jdbcClient: JdbcClient) {

	fun listRecentForOrganization(organizationId: UUID, limit: Int): List<AuditEvent> =
		jdbcClient.sql(
			"""
			select id, actor_user_id, organization_id, action, entity_type, entity_id, metadata, created_at
			from audit_event
			where organization_id = :organizationId
			order by created_at desc
			limit :limit
			""".trimIndent(),
		)
			.param("organizationId", organizationId)
			.param("limit", limit)
			.query(::mapRow)
			.list()

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

	private fun mapRow(rs: java.sql.ResultSet, rowNum: Int): AuditEvent =
		AuditEvent(
			id = rs.getObject("id", UUID::class.java),
			actorUserId = rs.getObject("actor_user_id", UUID::class.java),
			organizationId = rs.getObject("organization_id", UUID::class.java),
			action = rs.getString("action"),
			entityType = rs.getString("entity_type"),
			entityId = rs.getObject("entity_id", UUID::class.java),
			metadata = rs.getString("metadata"),
			createdAt = rs.getTimestamp("created_at").toInstant(),
		)
}
