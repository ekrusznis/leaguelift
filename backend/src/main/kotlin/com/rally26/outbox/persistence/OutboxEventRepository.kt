package com.rally26.outbox.persistence

import com.rally26.outbox.domain.OutboxEvent
import com.rally26.outbox.domain.OutboxEventStatus
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

private const val COLUMNS =
    "id, aggregate_type, aggregate_id, organization_id, event_type, schema_version, payload, status, attempt_count, available_at, processed_at, last_error, created_at"

/**
 * Phase 8 slice 1 (ADR-022) adds real claim/complete/fail/dead-letter/reprocess methods
 * on top of Phase 0's write-only [insert] + [countByStatus]. Status lifecycle: a fresh
 * row is `PENDING`; [claimBatch] moves due `PENDING`/`FAILED` rows to `PROCESSING`;
 * [markProcessed] on success; [markFailed] moves a retryable failure to `FAILED` with
 * `available_at` pushed forward by backoff (distinct from a fresh, never-yet-failed
 * `PENDING` row — this is what lets the platform-admin dashboard's separate
 * pending/failed counts mean something); [markDeadLetter] once attempts are exhausted.
 * [reprocess] is the platform-admin manual-recovery path back from `DEAD_LETTER`/
 * `FAILED` to `PENDING`.
 */
@Repository
class OutboxEventRepository(
    private val jdbcClient: JdbcClient,
) {
    fun insert(
        aggregateType: String,
        aggregateId: UUID,
        organizationId: UUID?,
        eventType: String,
        payloadJson: String,
        schemaVersion: Int = 1,
    ) {
        jdbcClient
            .sql(
                """
                insert into outbox_event
                	(id, aggregate_type, aggregate_id, organization_id, event_type, schema_version, payload, status, attempt_count, available_at, created_at)
                values
                	(:id, :aggregateType, :aggregateId, :organizationId, :eventType, :schemaVersion, cast(:payload as jsonb), 'PENDING', 0, now(), now())
                """.trimIndent(),
            ).param("id", UUID.randomUUID())
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
        jdbcClient
            .sql("select count(*) from outbox_event where status = :status")
            .param("status", status)
            .query(Long::class.java)
            .single()

    /**
     * Atomically claims up to [limit] due `PENDING`/`FAILED` rows for this worker tick —
     * `for update skip locked` inside the CTE means a second instance running the same
     * query concurrently skips rows already claimed rather than blocking or double-
     * processing them (safe today with a single instance, forward-compatible with more).
     */
    fun claimBatch(limit: Int): List<OutboxEvent> =
        jdbcClient
            .sql(
                """
                with claimed as (
                	select id from outbox_event
                	where status in ('PENDING', 'FAILED') and available_at <= now()
                	order by available_at
                	for update skip locked
                	limit :limit
                )
                update outbox_event o
                set status = 'PROCESSING', attempt_count = attempt_count + 1
                from claimed c
                where o.id = c.id
                returning o.id, o.aggregate_type, o.aggregate_id, o.organization_id, o.event_type, o.schema_version, o.payload, o.status, o.attempt_count, o.available_at, o.processed_at, o.last_error, o.created_at
                """.trimIndent(),
            ).param("limit", limit)
            .query(::mapRow)
            .list()

    fun markProcessed(id: UUID) {
        jdbcClient
            .sql("update outbox_event set status = 'PROCESSED', processed_at = now() where id = :id")
            .param("id", id)
            .update()
    }

    fun markFailed(
        id: UUID,
        availableAt: Instant,
        lastError: String,
    ) {
        jdbcClient
            .sql(
                "update outbox_event set status = 'FAILED', available_at = :availableAt, last_error = :lastError where id = :id",
            ).param("id", id)
            .param("availableAt", Timestamp.from(availableAt))
            .param("lastError", lastError.take(2000))
            .update()
    }

    fun markDeadLetter(
        id: UUID,
        lastError: String,
    ) {
        jdbcClient
            .sql("update outbox_event set status = 'DEAD_LETTER', last_error = :lastError where id = :id")
            .param("id", id)
            .param("lastError", lastError.take(2000))
            .update()
    }

    /** Platform-admin dead-letter/failed inspection view (DESIGN-DOC.md section 17 "provide admin visibility"). */
    fun findByStatus(
        status: String,
        limit: Int,
    ): List<OutboxEvent> =
        jdbcClient
            .sql("select $COLUMNS from outbox_event where status = :status order by created_at desc limit :limit")
            .param("status", status)
            .param("limit", limit)
            .query(::mapRow)
            .list()

    /** Manual recovery — resets a stuck row back to claimable. Returns true if a matching row existed. */
    fun reprocess(id: UUID): Boolean {
        val updated =
            jdbcClient
                .sql(
                    """
                    update outbox_event
                    set status = 'PENDING', attempt_count = 0, available_at = now(), last_error = null
                    where id = :id and status in ('DEAD_LETTER', 'FAILED')
                    """.trimIndent(),
                ).param("id", id)
                .update()
        return updated > 0
    }

    private fun mapRow(
        rs: ResultSet,
        rowNum: Int,
    ): OutboxEvent =
        OutboxEvent(
            id = rs.getObject("id", UUID::class.java),
            aggregateType = rs.getString("aggregate_type"),
            aggregateId = rs.getObject("aggregate_id", UUID::class.java),
            organizationId = rs.getObject("organization_id", UUID::class.java),
            eventType = rs.getString("event_type"),
            schemaVersion = rs.getInt("schema_version"),
            payload = rs.getString("payload"),
            status = OutboxEventStatus.valueOf(rs.getString("status")),
            attemptCount = rs.getInt("attempt_count"),
            availableAt = rs.getTimestamp("available_at").toInstant(),
            processedAt = rs.getTimestamp("processed_at")?.toInstant(),
            lastError = rs.getString("last_error"),
            createdAt = rs.getTimestamp("created_at").toInstant(),
        )
}
