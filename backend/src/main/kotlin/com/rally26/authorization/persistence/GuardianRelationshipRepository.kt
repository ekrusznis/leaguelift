package com.rally26.authorization.persistence

import com.rally26.authorization.domain.GuardianRelationship
import com.rally26.authorization.domain.GuardianRelationshipStatus
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

private const val COLUMNS =
    "id, organization_id, household_id, household_adult_id, user_id, status, created_at, updated_at"

@Repository
class GuardianRelationshipRepository(
    private val jdbcClient: JdbcClient,
) {
    fun findActiveForUser(userId: UUID): List<GuardianRelationship> =
        jdbcClient
            .sql("select $COLUMNS from guardian_relationship where user_id = :userId and status = 'ACTIVE'")
            .param("userId", userId)
            .query(::mapRow)
            .list()

    fun findActiveForHousehold(
        userId: UUID,
        householdId: UUID,
    ): GuardianRelationship? =
        jdbcClient
            .sql(
                """
                select $COLUMNS from guardian_relationship
                where user_id = :userId and household_id = :householdId and status = 'ACTIVE'
                """.trimIndent(),
            ).param("userId", userId)
            .param("householdId", householdId)
            .query(::mapRow)
            .optional()
            .orElse(null)

    fun findActiveForAdult(
        userId: UUID,
        householdAdultId: UUID,
    ): GuardianRelationship? =
        jdbcClient
            .sql(
                """
                select $COLUMNS from guardian_relationship
                where user_id = :userId and household_adult_id = :householdAdultId and status = 'ACTIVE'
                """.trimIndent(),
            ).param("userId", userId)
            .param("householdAdultId", householdAdultId)
            .query(::mapRow)
            .optional()
            .orElse(null)

    /**
     * True only when this guardian has an active relationship to a household with an
     * active participant assigned to the requested team. This keeps guardian-created
     * team fundraisers scoped to teams their household actually participates in.
     */
    fun hasActiveForTeam(
        userId: UUID,
        organizationId: UUID,
        teamId: UUID,
    ): Boolean =
        jdbcClient
            .sql(
                """
                select exists(
                    select 1
                    from guardian_relationship gr
                    join participant p
                      on p.household_id = gr.household_id
                     and p.organization_id = gr.organization_id
                     and p.status = 'ACTIVE'
                    join participant_team pt
                      on pt.participant_id = p.id
                     and pt.organization_id = p.organization_id
                     and pt.status = 'ACTIVE'
                    where gr.user_id = :userId
                      and gr.organization_id = :organizationId
                      and gr.status = 'ACTIVE'
                      and pt.team_id = :teamId
                )
                """.trimIndent(),
            ).param("userId", userId)
            .param("organizationId", organizationId)
            .param("teamId", teamId)
            .query(Boolean::class.java)
            .single()

    fun insert(
        organizationId: UUID,
        householdId: UUID,
        householdAdultId: UUID,
        userId: UUID,
    ): GuardianRelationship {
        val now = Instant.now()
        val id = UUID.randomUUID()
        jdbcClient
            .sql(
                """
                insert into guardian_relationship (id, organization_id, household_id, household_adult_id, user_id, status, created_at, updated_at)
                values (:id, :organizationId, :householdId, :householdAdultId, :userId, 'ACTIVE', :now, :now)
                """.trimIndent(),
            ).param("id", id)
            .param("organizationId", organizationId)
            .param("householdId", householdId)
            .param("householdAdultId", householdAdultId)
            .param("userId", userId)
            .param("now", Timestamp.from(now))
            .update()
        return GuardianRelationship(id, organizationId, householdId, householdAdultId, userId, GuardianRelationshipStatus.ACTIVE, now, now)
    }

    private fun mapRow(
        rs: java.sql.ResultSet,
        rowNum: Int,
    ): GuardianRelationship =
        GuardianRelationship(
            id = rs.getObject("id", UUID::class.java),
            organizationId = rs.getObject("organization_id", UUID::class.java),
            householdId = rs.getObject("household_id", UUID::class.java),
            householdAdultId = rs.getObject("household_adult_id", UUID::class.java),
            userId = rs.getObject("user_id", UUID::class.java),
            status = GuardianRelationshipStatus.valueOf(rs.getString("status")),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant(),
        )
}
