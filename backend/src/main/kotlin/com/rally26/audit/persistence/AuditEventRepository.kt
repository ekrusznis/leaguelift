package com.rally26.audit.persistence

import com.rally26.audit.domain.AuditActorType
import com.rally26.audit.domain.AuditEvent
import com.rally26.audit.domain.AuditResult
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class AuditEventRepository(
    private val jdbcClient: JdbcClient,
) {
    fun listRecentForOrganization(
        organizationId: UUID,
        limit: Int,
    ): List<AuditEvent> =
        jdbcClient
            .sql(
                """
                select $SELECT_COLUMNS
                from audit_event
                where organization_id = :organizationId
                order by created_at desc, id desc
                limit :limit
                """.trimIndent(),
            ).param("organizationId", organizationId)
            .param("limit", limit)
            .query(::mapRow)
            .list()

    /** Cross-org activity feed — every organization the caller belongs to, in one feed. */
    fun listRecentForOrganizations(
        organizationIds: Collection<UUID>,
        limit: Int,
    ): List<AuditEvent> {
        if (organizationIds.isEmpty()) return emptyList()
        return jdbcClient
            .sql(
                """
                select $SELECT_COLUMNS
                from audit_event
                where organization_id in (:organizationIds)
                order by created_at desc, id desc
                limit :limit
                """.trimIndent(),
            ).param("organizationIds", organizationIds.toList())
            .param("limit", limit)
            .query(::mapRow)
            .list()
    }

    /** Platform-admin-only activity feed with no organization filter. */
    fun listRecentAcrossAllOrganizations(limit: Int): List<AuditEvent> =
        jdbcClient
            .sql(
                """
                select $SELECT_COLUMNS
                from audit_event
                order by created_at desc, id desc
                limit :limit
                """.trimIndent(),
            ).param("limit", limit)
            .query(::mapRow)
            .list()

    fun insert(
        actorUserId: UUID?,
        organizationId: UUID?,
        action: String,
        entityType: String,
        entityId: UUID,
        metadataJson: String,
        teamId: UUID? = null,
        householdId: UUID? = null,
        participantId: UUID? = null,
        targetUserId: UUID? = null,
        actorType: AuditActorType = if (actorUserId == null) AuditActorType.SYSTEM else AuditActorType.USER,
        result: AuditResult = AuditResult.SUCCESS,
        summary: String = action,
        correlationId: UUID? = null,
    ) {
        jdbcClient
            .sql(
                """
                insert into audit_event (
                    id, actor_user_id, actor_type, organization_id, team_id, household_id,
                    participant_id, target_user_id, action, result, entity_type, entity_id,
                    summary, metadata, correlation_id, created_at
                ) values (
                    :id, :actorUserId, :actorType, :organizationId, :teamId, :householdId,
                    :participantId, :targetUserId, :action, :result, :entityType, :entityId,
                    :summary, cast(:metadata as jsonb), :correlationId, now()
                )
                """.trimIndent(),
            ).param("id", UUID.randomUUID())
            .param("actorUserId", actorUserId)
            .param("actorType", actorType.name)
            .param("organizationId", organizationId)
            .param("teamId", teamId)
            .param("householdId", householdId)
            .param("participantId", participantId)
            .param("targetUserId", targetUserId)
            .param("action", action)
            .param("result", result.name)
            .param("entityType", entityType)
            .param("entityId", entityId)
            .param("summary", summary)
            .param("metadata", metadataJson)
            .param("correlationId", correlationId)
            .update()
    }

    private fun mapRow(
        rs: java.sql.ResultSet,
        rowNum: Int,
    ): AuditEvent =
        AuditEvent(
            id = rs.getObject("id", UUID::class.java),
            actorUserId = rs.getObject("actor_user_id", UUID::class.java),
            organizationId = rs.getObject("organization_id", UUID::class.java),
            action = rs.getString("action"),
            entityType = rs.getString("entity_type"),
            entityId = rs.getObject("entity_id", UUID::class.java),
            metadata = rs.getString("metadata"),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            teamId = rs.getObject("team_id", UUID::class.java),
            householdId = rs.getObject("household_id", UUID::class.java),
            participantId = rs.getObject("participant_id", UUID::class.java),
            targetUserId = rs.getObject("target_user_id", UUID::class.java),
            actorType = AuditActorType.valueOf(rs.getString("actor_type")),
            result = AuditResult.valueOf(rs.getString("result")),
            summary = rs.getString("summary"),
            correlationId = rs.getObject("correlation_id", UUID::class.java),
        )

    private companion object {
        const val SELECT_COLUMNS =
            "id, actor_user_id, actor_type, organization_id, team_id, household_id, " +
                "participant_id, target_user_id, action, result, entity_type, entity_id, " +
                "summary, metadata, correlation_id, created_at"
    }
}
