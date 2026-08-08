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
        val inferred = inferScope(entityType, entityId)
        val effectiveOrganizationId = organizationId ?: inferred?.organizationId
        val effectiveTeamId = teamId ?: inferred?.teamId
        val effectiveHouseholdId = householdId ?: inferred?.householdId
        val effectiveParticipantId = participantId ?: inferred?.participantId
        val effectiveTargetUserId = targetUserId ?: inferred?.targetUserId

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
            .param("organizationId", effectiveOrganizationId)
            .param("teamId", effectiveTeamId)
            .param("householdId", effectiveHouseholdId)
            .param("participantId", effectiveParticipantId)
            .param("targetUserId", effectiveTargetUserId)
            .param("action", action)
            .param("result", result.name)
            .param("entityType", entityType)
            .param("entityId", entityId)
            .param("summary", summary)
            .param("metadata", metadataJson)
            .param("correlationId", correlationId)
            .update()
    }

    /**
     * Compatibility bridge for the many pre-Phase-27 audit calls that only supplied
     * entity type/id. Explicit scopes always win. This resolver covers the structural
     * entity types that can be determined without guessing; newer domain code should
     * still pass richer scope directly when it has it.
     */
    private fun inferScope(
        entityType: String,
        entityId: UUID,
    ): InferredAuditScope? =
        when (entityType.uppercase()) {
            "ORGANIZATION" ->
                queryScope(
                    """
                    select id as organization_id, null::uuid as team_id, null::uuid as household_id,
                           null::uuid as participant_id, null::uuid as target_user_id
                    from organization where id = :id
                    """.trimIndent(),
                    entityId,
                )
            "TEAM" ->
                queryScope(
                    """
                    select organization_id, id as team_id, null::uuid as household_id,
                           null::uuid as participant_id, null::uuid as target_user_id
                    from team where id = :id
                    """.trimIndent(),
                    entityId,
                )
            "HOUSEHOLD" ->
                queryScope(
                    """
                    select organization_id, null::uuid as team_id, id as household_id,
                           null::uuid as participant_id, null::uuid as target_user_id
                    from household where id = :id
                    """.trimIndent(),
                    entityId,
                )
            "PARTICIPANT", "ATHLETE" ->
                queryScope(
                    """
                    select organization_id, null::uuid as team_id, household_id, id as participant_id,
                           null::uuid as target_user_id
                    from participant where id = :id
                    """.trimIndent(),
                    entityId,
                )
            "EVENT" ->
                queryScope(
                    """
                    select organization_id, team_id, null::uuid as household_id,
                           null::uuid as participant_id, null::uuid as target_user_id
                    from event where id = :id
                    """.trimIndent(),
                    entityId,
                )
            "MESSAGE_THREAD" ->
                queryScope(
                    """
                    select organization_id,
                           case when scope_type = 'TEAM' then scope_id else null end as team_id,
                           null::uuid as household_id, null::uuid as participant_id, null::uuid as target_user_id
                    from message_thread where id = :id
                    """.trimIndent(),
                    entityId,
                )
            "MESSAGE", "MESSAGE_ENTRY" ->
                queryScope(
                    """
                    select mt.organization_id,
                           case when mt.scope_type = 'TEAM' then mt.scope_id else null end as team_id,
                           null::uuid as household_id, null::uuid as participant_id, null::uuid as target_user_id
                    from message_entry me join message_thread mt on mt.id = me.thread_id and mt.organization_id = me.organization_id
                    where me.id = :id
                    """.trimIndent(),
                    entityId,
                )
            "USER", "APP_USER" -> InferredAuditScope(targetUserId = entityId)
            else -> null
        }

    private fun queryScope(
        sql: String,
        entityId: UUID,
    ): InferredAuditScope? =
        jdbcClient
            .sql(sql)
            .param("id", entityId)
            .query { rs, _ ->
                InferredAuditScope(
                    organizationId = rs.getObject("organization_id", UUID::class.java),
                    teamId = rs.getObject("team_id", UUID::class.java),
                    householdId = rs.getObject("household_id", UUID::class.java),
                    participantId = rs.getObject("participant_id", UUID::class.java),
                    targetUserId = rs.getObject("target_user_id", UUID::class.java),
                )
            }.optional()
            .orElse(null)

    private data class InferredAuditScope(
        val organizationId: UUID? = null,
        val teamId: UUID? = null,
        val householdId: UUID? = null,
        val participantId: UUID? = null,
        val targetUserId: UUID? = null,
    )

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
