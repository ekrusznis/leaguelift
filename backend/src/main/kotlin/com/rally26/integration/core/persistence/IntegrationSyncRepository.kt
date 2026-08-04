package com.rally26.integration.core.persistence

import com.rally26.integration.core.domain.IntegrationOwnerType
import com.rally26.integration.core.domain.IntegrationProvider
import com.rally26.integration.core.domain.IntegrationSyncDirection
import com.rally26.integration.core.domain.IntegrationSyncIssue
import com.rally26.integration.core.domain.IntegrationSyncIssueSeverity
import com.rally26.integration.core.domain.IntegrationSyncRun
import com.rally26.integration.core.domain.IntegrationSyncStatus
import com.rally26.integration.core.domain.IntegrationSyncSummary
import com.rally26.integration.core.domain.IntegrationSyncTrigger
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID

@Repository
class IntegrationSyncRepository(private val jdbcClient: JdbcClient) {
    fun create(
        connectionId: UUID?,
        provider: IntegrationProvider,
        ownerType: IntegrationOwnerType,
        organizationId: UUID?,
        userId: UUID?,
        direction: IntegrationSyncDirection,
        trigger: IntegrationSyncTrigger,
        idempotencyKey: String?,
        requestedByUserId: UUID?,
    ): IntegrationSyncRun {
        val id = UUID.randomUUID()
        jdbcClient.sql(
            """
            insert into integration_sync_run
                (id, connection_id, provider, owner_type, organization_id, user_id,
                 direction, trigger_type, status, idempotency_key, requested_by_user_id)
            values
                (:id, :connectionId, :provider, :ownerType, :organizationId, :userId,
                 :direction, :triggerType, 'QUEUED', :idempotencyKey, :requestedByUserId)
            """.trimIndent(),
        )
            .param("id", id)
            .param("connectionId", connectionId)
            .param("provider", provider.name)
            .param("ownerType", ownerType.name)
            .param("organizationId", organizationId)
            .param("userId", userId)
            .param("direction", direction.name)
            .param("triggerType", trigger.name)
            .param("idempotencyKey", idempotencyKey?.take(200))
            .param("requestedByUserId", requestedByUserId)
            .update()
        return requireNotNull(find(id))
    }

    fun findByIdempotency(
        provider: IntegrationProvider,
        ownerType: IntegrationOwnerType,
        organizationId: UUID?,
        userId: UUID?,
        idempotencyKey: String,
    ): IntegrationSyncRun? {
        val ownerClause = when (ownerType) {
            IntegrationOwnerType.PLATFORM -> "owner_type = 'PLATFORM'"
            IntegrationOwnerType.ORGANIZATION -> "owner_type = 'ORGANIZATION' and organization_id = :organizationId"
            IntegrationOwnerType.USER -> "owner_type = 'USER' and user_id = :userId"
        }
        var query = jdbcClient.sql(
            "select $RUN_COLUMNS from integration_sync_run where provider = :provider and idempotency_key = :idempotencyKey and $ownerClause order by requested_at desc limit 1",
        )
            .param("provider", provider.name)
            .param("idempotencyKey", idempotencyKey)
        if (ownerType == IntegrationOwnerType.ORGANIZATION) query = query.param("organizationId", organizationId)
        if (ownerType == IntegrationOwnerType.USER) query = query.param("userId", userId)
        return query.query(::mapRun).optional().orElse(null)
    }

    fun markRunning(id: UUID): IntegrationSyncRun {
        jdbcClient.sql("update integration_sync_run set status = 'RUNNING', started_at = coalesce(started_at, now()) where id = :id and status = 'QUEUED'")
            .param("id", id).update()
        return requireNotNull(find(id))
    }

    fun complete(
        id: UUID,
        status: IntegrationSyncStatus,
        summary: IntegrationSyncSummary,
        cursor: String? = null,
        checkpointJson: String = "{}",
        rateLimitRemaining: Int? = null,
        rateLimitResetsAt: Instant? = null,
        errorCode: String? = null,
        errorMessage: String? = null,
    ): IntegrationSyncRun {
        require(status in TERMINAL_STATUSES)
        jdbcClient.sql(
            """
            update integration_sync_run
            set status = :status, cursor_value = :cursorValue,
                checkpoint_json = cast(:checkpointJson as jsonb),
                discovered_count = :discoveredCount, created_count = :createdCount,
                updated_count = :updatedCount, skipped_count = :skippedCount,
                failed_count = :failedCount, rate_limit_remaining = :rateLimitRemaining,
                rate_limit_resets_at = :rateLimitResetsAt, error_code = :errorCode,
                error_message = :errorMessage, started_at = coalesce(started_at, now()),
                completed_at = now()
            where id = :id
            """.trimIndent(),
        )
            .param("id", id)
            .param("status", status.name)
            .param("cursorValue", cursor?.take(1000))
            .param("checkpointJson", checkpointJson)
            .param("discoveredCount", summary.discovered)
            .param("createdCount", summary.created)
            .param("updatedCount", summary.updated)
            .param("skippedCount", summary.skipped)
            .param("failedCount", summary.failed)
            .param("rateLimitRemaining", rateLimitRemaining)
            .param("rateLimitResetsAt", rateLimitResetsAt)
            .param("errorCode", errorCode?.take(120))
            .param("errorMessage", errorMessage?.take(500))
            .update()
        return requireNotNull(find(id))
    }

    fun addIssue(
        syncRunId: UUID,
        severity: IntegrationSyncIssueSeverity,
        code: String,
        message: String,
        externalEntityType: String? = null,
        externalEntityId: String? = null,
        internalEntityType: String? = null,
        internalEntityId: UUID? = null,
        retryable: Boolean = false,
        detailsJson: String = "{}",
    ): IntegrationSyncIssue {
        val id = UUID.randomUUID()
        jdbcClient.sql(
            """
            insert into integration_sync_issue
                (id, sync_run_id, severity, code, message, external_entity_type,
                 external_entity_id, internal_entity_type, internal_entity_id,
                 retryable, details_json)
            values
                (:id, :syncRunId, :severity, :code, :message, :externalEntityType,
                 :externalEntityId, :internalEntityType, :internalEntityId,
                 :retryable, cast(:detailsJson as jsonb))
            """.trimIndent(),
        )
            .param("id", id)
            .param("syncRunId", syncRunId)
            .param("severity", severity.name)
            .param("code", code.take(120))
            .param("message", message.take(1000))
            .param("externalEntityType", externalEntityType?.take(100))
            .param("externalEntityId", externalEntityId?.take(500))
            .param("internalEntityType", internalEntityType?.take(100))
            .param("internalEntityId", internalEntityId)
            .param("retryable", retryable)
            .param("detailsJson", detailsJson)
            .update()
        return jdbcClient.sql("select $ISSUE_COLUMNS from integration_sync_issue where id = :id")
            .param("id", id).query(::mapIssue).single()
    }

    fun find(id: UUID): IntegrationSyncRun? =
        jdbcClient.sql("select $RUN_COLUMNS from integration_sync_run where id = :id")
            .param("id", id).query(::mapRun).optional().orElse(null)

    fun listForOrganization(organizationId: UUID, limit: Int): List<IntegrationSyncRun> =
        jdbcClient.sql("select $RUN_COLUMNS from integration_sync_run where organization_id = :organizationId order by requested_at desc limit :limit")
            .param("organizationId", organizationId).param("limit", limit).query(::mapRun).list()

    fun listForUser(userId: UUID, limit: Int): List<IntegrationSyncRun> =
        jdbcClient.sql("select $RUN_COLUMNS from integration_sync_run where user_id = :userId order by requested_at desc limit :limit")
            .param("userId", userId).param("limit", limit).query(::mapRun).list()

    fun listPlatform(limit: Int): List<IntegrationSyncRun> =
        jdbcClient.sql("select $RUN_COLUMNS from integration_sync_run order by requested_at desc limit :limit")
            .param("limit", limit).query(::mapRun).list()

    fun listIssues(syncRunId: UUID): List<IntegrationSyncIssue> =
        jdbcClient.sql("select $ISSUE_COLUMNS from integration_sync_issue where sync_run_id = :syncRunId order by created_at, id")
            .param("syncRunId", syncRunId).query(::mapIssue).list()

    private fun mapRun(rs: ResultSet, rowNum: Int) = IntegrationSyncRun(
        id = rs.getObject("id", UUID::class.java),
        connectionId = rs.getObject("connection_id", UUID::class.java),
        provider = IntegrationProvider.valueOf(rs.getString("provider")),
        ownerType = IntegrationOwnerType.valueOf(rs.getString("owner_type")),
        organizationId = rs.getObject("organization_id", UUID::class.java),
        userId = rs.getObject("user_id", UUID::class.java),
        direction = IntegrationSyncDirection.valueOf(rs.getString("direction")),
        trigger = IntegrationSyncTrigger.valueOf(rs.getString("trigger_type")),
        status = IntegrationSyncStatus.valueOf(rs.getString("status")),
        idempotencyKey = rs.getString("idempotency_key"),
        cursor = rs.getString("cursor_value"),
        checkpointJson = rs.getString("checkpoint_json"),
        discoveredCount = rs.getInt("discovered_count"),
        createdCount = rs.getInt("created_count"),
        updatedCount = rs.getInt("updated_count"),
        skippedCount = rs.getInt("skipped_count"),
        failedCount = rs.getInt("failed_count"),
        rateLimitRemaining = rs.getObject("rate_limit_remaining") as Int?,
        rateLimitResetsAt = rs.getTimestamp("rate_limit_resets_at")?.toInstant(),
        errorCode = rs.getString("error_code"),
        errorMessage = rs.getString("error_message"),
        requestedByUserId = rs.getObject("requested_by_user_id", UUID::class.java),
        requestedAt = rs.getTimestamp("requested_at").toInstant(),
        startedAt = rs.getTimestamp("started_at")?.toInstant(),
        completedAt = rs.getTimestamp("completed_at")?.toInstant(),
    )

    private fun mapIssue(rs: ResultSet, rowNum: Int) = IntegrationSyncIssue(
        id = rs.getObject("id", UUID::class.java),
        syncRunId = rs.getObject("sync_run_id", UUID::class.java),
        severity = IntegrationSyncIssueSeverity.valueOf(rs.getString("severity")),
        code = rs.getString("code"),
        message = rs.getString("message"),
        externalEntityType = rs.getString("external_entity_type"),
        externalEntityId = rs.getString("external_entity_id"),
        internalEntityType = rs.getString("internal_entity_type"),
        internalEntityId = rs.getObject("internal_entity_id", UUID::class.java),
        retryable = rs.getBoolean("retryable"),
        detailsJson = rs.getString("details_json"),
        createdAt = rs.getTimestamp("created_at").toInstant(),
    )

    private companion object {
        val TERMINAL_STATUSES = setOf(IntegrationSyncStatus.SUCCEEDED, IntegrationSyncStatus.PARTIAL, IntegrationSyncStatus.FAILED, IntegrationSyncStatus.CANCELLED)
        const val RUN_COLUMNS = "id, connection_id, provider, owner_type, organization_id, user_id, direction, trigger_type, status, idempotency_key, cursor_value, checkpoint_json, discovered_count, created_count, updated_count, skipped_count, failed_count, rate_limit_remaining, rate_limit_resets_at, error_code, error_message, requested_by_user_id, requested_at, started_at, completed_at"
        const val ISSUE_COLUMNS = "id, sync_run_id, severity, code, message, external_entity_type, external_entity_id, internal_entity_type, internal_entity_id, retryable, details_json, created_at"
    }
}
