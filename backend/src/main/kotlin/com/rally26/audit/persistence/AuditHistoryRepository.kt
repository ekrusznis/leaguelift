package com.rally26.audit.persistence

import com.rally26.audit.domain.AuditActorType
import com.rally26.audit.domain.AuditHistoryFilterAccess
import com.rally26.audit.domain.AuditHistoryItem
import com.rally26.audit.domain.AuditHistoryQuery
import com.rally26.audit.domain.AuditHistorySortDirection
import com.rally26.audit.domain.AuditHistorySortField
import com.rally26.audit.domain.AuditResult
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.time.ZoneOffset
import java.util.UUID

@Repository
class AuditHistoryRepository(
    private val jdbcClient: JdbcClient,
) {
    fun resolveFilterAccess(
        viewerUserId: UUID,
        platformAdministrator: Boolean,
    ): AuditHistoryFilterAccess {
        if (platformAdministrator) return AuditHistoryFilterAccess(true, true, true)
        return jdbcClient
            .sql(
                """
                select
                  exists (
                    select 1 from guardian_relationship gr
                    where gr.user_id = :viewerUserId and gr.status = 'ACTIVE'
                  ) as is_guardian,
                  exists (
                    select 1 from role_assignment ra
                    where ra.user_id = :viewerUserId and ra.status = 'ACTIVE'
                      and ra.context_type = 'TEAM'
                      and ra.role in ('COACH_READ', 'TEAM_EDITOR', 'TEAM_MANAGER')
                  ) as is_team_staff,
                  exists (
                    select 1 from organization_membership om
                    where om.user_id = :viewerUserId and om.status = 'ACTIVE'
                      and om.role in ('OWNER', 'ADMINISTRATOR')
                  ) as is_org_manager
                """.trimIndent(),
            ).param("viewerUserId", viewerUserId)
            .query { rs, _ ->
                val guardian = rs.getBoolean("is_guardian")
                val teamStaff = rs.getBoolean("is_team_staff")
                val orgManager = rs.getBoolean("is_org_manager")
                AuditHistoryFilterAccess(
                    canFilterUser = guardian || teamStaff || orgManager,
                    canFilterTeam = teamStaff || orgManager,
                    canFilterOrganization = orgManager,
                )
            }.single()
    }

    fun search(
        viewerUserId: UUID,
        platformAdministrator: Boolean,
        query: AuditHistoryQuery,
    ): List<AuditHistoryItem> {
        val where = mutableListOf(VISIBILITY_CLAUSE)
        val params =
            mutableMapOf<String, Any>(
                "viewerUserId" to viewerUserId,
                "platformAdministrator" to platformAdministrator,
                "limit" to query.size + 1,
            )

        query.from?.let {
            where += "e.created_at >= :fromInstant"
            params["fromInstant"] = it.atOffset(ZoneOffset.UTC)
        }
        query.to?.let {
            where += "e.created_at < :toInstant"
            params["toInstant"] = it.atOffset(ZoneOffset.UTC)
        }
        query.action?.takeIf { it.isNotBlank() }?.let {
            where += "lower(e.action) = lower(:action)"
            params["action"] = it.trim()
        }
        query.result?.let {
            where += "e.result = :result"
            params["result"] = it.name
        }
        query.keyword?.takeIf { it.isNotBlank() }?.let {
            where += KEYWORD_CLAUSE
            params["keyword"] = "%${it.trim().lowercase()}%"
        }
        query.userQuery?.takeIf { it.isNotBlank() }?.let {
            where += USER_CLAUSE
            params["userQuery"] = "%${it.trim().lowercase()}%"
        }
        query.organizationId?.let {
            where += "e.organization_id = :organizationId"
            params["organizationId"] = it
        }
        query.teamId?.let {
            where += "e.team_id = :teamId"
            params["teamId"] = it
        }
        query.cursor?.let { cursor ->
            where += cursorClause(query.sortBy, query.direction)
            params["cursorCreatedAt"] = cursor.createdAt.atOffset(ZoneOffset.UTC)
            params["cursorId"] = cursor.id
            if (query.sortBy != AuditHistorySortField.DATE) params["cursorSortValue"] = cursor.sortValue
        }

        val sortExpression = sortExpression(query.sortBy)
        val direction = query.direction.name
        val orderBy =
            if (query.sortBy == AuditHistorySortField.DATE) {
                "e.created_at $direction, e.id $direction"
            } else {
                "$sortExpression $direction, e.created_at $direction, e.id $direction"
            }
        val sql =
            """
            select
              e.id, e.created_at, e.action, e.result, e.summary, e.actor_type,
              e.actor_user_id, actor.display_name as actor_display_name,
              e.target_user_id, target.display_name as target_display_name,
              e.organization_id, org.name as organization_name,
              e.team_id, team.name as team_name,
              e.household_id, household.display_name as household_name,
              e.participant_id,
              nullif(trim(concat_ws(' ', participant.first_name, participant.last_name)), '') as participant_display_name,
              e.entity_type, e.entity_id
            from audit_event e
            left join app_user actor on actor.id = e.actor_user_id
            left join app_user target on target.id = e.target_user_id
            left join organization org on org.id = e.organization_id
            left join team team on team.id = e.team_id
            left join household household on household.id = e.household_id
            left join participant participant on participant.id = e.participant_id
            where ${where.joinToString("\n  and ")}
            order by $orderBy
            limit :limit
            """.trimIndent()

        return jdbcClient
            .sql(sql)
            .params(params)
            .query(::mapRow)
            .list()
    }

    private fun mapRow(
        rs: java.sql.ResultSet,
        rowNum: Int,
    ): AuditHistoryItem =
        AuditHistoryItem(
            id = rs.getObject("id", UUID::class.java),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            action = rs.getString("action"),
            result = AuditResult.valueOf(rs.getString("result")),
            summary = rs.getString("summary"),
            actorType = AuditActorType.valueOf(rs.getString("actor_type")),
            actorUserId = rs.getObject("actor_user_id", UUID::class.java),
            actorDisplayName = rs.getString("actor_display_name"),
            targetUserId = rs.getObject("target_user_id", UUID::class.java),
            targetDisplayName = rs.getString("target_display_name"),
            organizationId = rs.getObject("organization_id", UUID::class.java),
            organizationName = rs.getString("organization_name"),
            teamId = rs.getObject("team_id", UUID::class.java),
            teamName = rs.getString("team_name"),
            householdId = rs.getObject("household_id", UUID::class.java),
            householdName = rs.getString("household_name"),
            participantId = rs.getObject("participant_id", UUID::class.java),
            participantDisplayName = rs.getString("participant_display_name"),
            entityType = rs.getString("entity_type"),
            entityId = rs.getObject("entity_id", UUID::class.java),
        )

    private fun sortExpression(sortBy: AuditHistorySortField): String =
        when (sortBy) {
            AuditHistorySortField.DATE -> "e.created_at"
            AuditHistorySortField.ACTION -> "lower(e.action)"
            AuditHistorySortField.RESULT -> "e.result"
        }

    private fun cursorClause(
        sortBy: AuditHistorySortField,
        direction: AuditHistorySortDirection,
    ): String {
        val comparator = if (direction == AuditHistorySortDirection.ASC) ">" else "<"
        if (sortBy == AuditHistorySortField.DATE) {
            return """
                (e.created_at $comparator :cursorCreatedAt
                 or (e.created_at = :cursorCreatedAt and e.id $comparator :cursorId))
                """.trimIndent()
        }
        val expression = sortExpression(sortBy)
        return """
            ($expression $comparator :cursorSortValue
             or ($expression = :cursorSortValue and e.created_at $comparator :cursorCreatedAt)
             or ($expression = :cursorSortValue and e.created_at = :cursorCreatedAt and e.id $comparator :cursorId))
            """.trimIndent()
    }

    private companion object {
        val VISIBILITY_CLAUSE =
            """
            (
              :platformAdministrator = true
              or e.actor_user_id = :viewerUserId
              or e.target_user_id = :viewerUserId
              or exists (
                select 1 from organization_membership om
                where om.user_id = :viewerUserId and om.status = 'ACTIVE'
                  and om.role in ('OWNER', 'ADMINISTRATOR')
                  and om.organization_id = e.organization_id
              )
              or exists (
                select 1 from role_assignment ra
                where ra.user_id = :viewerUserId and ra.status = 'ACTIVE'
                  and ra.context_type = 'TEAM'
                  and ra.role in ('COACH_READ', 'TEAM_EDITOR', 'TEAM_MANAGER')
                  and ra.resource_id = e.team_id
              )
              or exists (
                select 1 from guardian_relationship gr
                where gr.user_id = :viewerUserId and gr.status = 'ACTIVE'
                  and (
                    gr.household_id = e.household_id
                    or exists (
                      select 1 from participant gp
                      where gp.id = e.participant_id and gp.household_id = gr.household_id
                    )
                  )
              )
              or exists (
                select 1 from role_assignment athlete
                where athlete.user_id = :viewerUserId and athlete.status = 'ACTIVE'
                  and athlete.context_type = 'PARTICIPANT'
                  and athlete.role = 'ATHLETE_SELF'
                  and athlete.resource_id = e.participant_id
              )
            )
            """.trimIndent()

        val KEYWORD_CLAUSE =
            """
            (
              lower(coalesce(e.summary, '') || ' ' || coalesce(e.action, '') || ' ' || coalesce(e.entity_type, '')) like :keyword
              or lower(coalesce(actor.display_name, '')) like :keyword
              or lower(coalesce(target.display_name, '')) like :keyword
              or lower(coalesce(org.name, '')) like :keyword
              or lower(coalesce(team.name, '')) like :keyword
              or lower(coalesce(household.display_name, '')) like :keyword
              or lower(trim(concat_ws(' ', participant.first_name, participant.last_name))) like :keyword
            )
            """.trimIndent()

        val USER_CLAUSE =
            """
            (
              lower(coalesce(actor.display_name, '')) like :userQuery
              or lower(coalesce(target.display_name, '')) like :userQuery
              or lower(trim(concat_ws(' ', participant.first_name, participant.last_name))) like :userQuery
            )
            """.trimIndent()
    }
}
