package com.leaguelift.outbox.persistence

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.util.UUID

/**
 * Phase 0 provides the outbox table and a writer only. A background worker that
 * claims PENDING rows, dispatches them, and retries with backoff is introduced with
 * the first module that actually needs asynchronous side effects (e.g. email on
 * organization invitation in Phase 1) — see DESIGN-DOC.md section 20.2.
 */
@Repository
class OutboxEventRepository(private val jdbcClient: JdbcClient) {

	fun insert(
		aggregateType: String,
		aggregateId: UUID,
		organizationId: UUID?,
		eventType: String,
		payloadJson: String,
		schemaVersion: Int = 1,
	) {
		jdbcClient.sql(
			"""
			insert into outbox_event
				(id, aggregate_type, aggregate_id, organization_id, event_type, schema_version, payload, status, attempt_count, available_at, created_at)
			values
				(:id, :aggregateType, :aggregateId, :organizationId, :eventType, :schemaVersion, cast(:payload as jsonb), 'PENDING', 0, now(), now())
			""".trimIndent(),
		)
			.param("id", UUID.randomUUID())
			.param("aggregateType", aggregateType)
			.param("aggregateId", aggregateId)
			.param("organizationId", organizationId)
			.param("eventType", eventType)
			.param("schemaVersion", schemaVersion)
			.param("payload", payloadJson)
			.update()
	}

	/** Platform-admin-only health aggregate (DESIGN-DOC.md section 10.2/18.2 outbox backlog). */
	fun countByStatus(status: String): Long =
		jdbcClient.sql("select count(*) from outbox_event where status = :status")
			.param("status", status)
			.query(Long::class.java)
			.single()
}
