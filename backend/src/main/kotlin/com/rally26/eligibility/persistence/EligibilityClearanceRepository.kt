package com.rally26.eligibility.persistence

import com.rally26.eligibility.domain.ClearanceStatus
import com.rally26.eligibility.domain.EligibilityClearance
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

private const val CLEARANCE_COLUMNS = "id, organization_id, participant_id, team_id, status, unmet_requirement_count, computed_at"

@Repository
class EligibilityClearanceRepository(
    private val jdbcClient: JdbcClient,
) {
    fun find(
        participantId: UUID,
        teamId: UUID,
        organizationId: UUID,
    ): EligibilityClearance? =
        jdbcClient
            .sql(
                "select $CLEARANCE_COLUMNS from eligibility_clearance where participant_id = :participantId and team_id = :teamId and organization_id = :organizationId",
            ).param("participantId", participantId)
            .param("teamId", teamId)
            .param("organizationId", organizationId)
            .query(::mapRow)
            .optional()
            .orElse(null)

    /**
     * Every clearance snapshot for a team's roster — what Coach's roster view reads
     * (operational status only, never the underlying evidence). [statusFilter] lets a
     * coach narrow the view to e.g. just INELIGIBLE players without fetching the whole
     * roster's clearance and filtering client-side.
     */
    fun listForTeam(
        teamId: UUID,
        organizationId: UUID,
        statusFilter: ClearanceStatus? = null,
    ): List<EligibilityClearance> =
        jdbcClient
            .sql(
                """
                select $CLEARANCE_COLUMNS from eligibility_clearance
                where team_id = :teamId and organization_id = :organizationId
                  and (:statusFilter::text is null or status = :statusFilter::text)
                """.trimIndent(),
            ).param("teamId", teamId)
            .param("organizationId", organizationId)
            .param("statusFilter", statusFilter?.name)
            .query(::mapRow)
            .list()

    /**
     * A household's participants can each be on zero, one, or several teams, so there's
     * no single [teamId] to scope by the way [listForTeam] does — this returns every
     * (participant, team) clearance row for a caller-resolved set of participant ids
     * (see EligibilityService.listClearanceForHousehold), one row per team a
     * participant is actually rostered on. Returns an empty list without querying when
     * [participantIds] is empty (an empty SQL `in (...)` is invalid).
     */
    fun listForParticipants(
        participantIds: List<UUID>,
        organizationId: UUID,
    ): List<EligibilityClearance> {
        if (participantIds.isEmpty()) return emptyList()
        return jdbcClient
            .sql(
                "select $CLEARANCE_COLUMNS from eligibility_clearance where organization_id = :organizationId and participant_id in (:participantIds)",
            ).param("organizationId", organizationId)
            .param("participantIds", participantIds)
            .query(::mapRow)
            .list()
    }

    /**
     * The recomputation service's only write path — always a full replace of the
     * (participant, team) snapshot, never a partial field update, so this table can
     * never drift out of sync with what the evidence/requirement tables actually say
     * (DESIGN-DOC.md 14.1L acceptance criterion #8).
     */
    fun upsert(
        organizationId: UUID,
        participantId: UUID,
        teamId: UUID,
        status: ClearanceStatus,
        unmetRequirementCount: Int,
    ): EligibilityClearance {
        val now = Instant.now()
        val existing = find(participantId, teamId, organizationId)
        val id = existing?.id ?: UUID.randomUUID()
        jdbcClient
            .sql(
                """
                insert into eligibility_clearance (id, organization_id, participant_id, team_id, status, unmet_requirement_count, computed_at)
                values (:id, :organizationId, :participantId, :teamId, :status, :unmetRequirementCount, :now)
                on conflict (participant_id, team_id)
                do update set status = :status, unmet_requirement_count = :unmetRequirementCount, computed_at = :now
                """.trimIndent(),
            ).param("id", id)
            .param("organizationId", organizationId)
            .param("participantId", participantId)
            .param("teamId", teamId)
            .param("status", status.name)
            .param("unmetRequirementCount", unmetRequirementCount)
            .param("now", Timestamp.from(now))
            .update()
        return EligibilityClearance(
            id = id,
            organizationId = organizationId,
            participantId = participantId,
            teamId = teamId,
            status = status,
            unmetRequirementCount = unmetRequirementCount,
            computedAt = now,
        )
    }

    private fun mapRow(
        rs: ResultSet,
        rowNum: Int,
    ): EligibilityClearance =
        EligibilityClearance(
            id = rs.getObject("id", UUID::class.java),
            organizationId = rs.getObject("organization_id", UUID::class.java),
            participantId = rs.getObject("participant_id", UUID::class.java),
            teamId = rs.getObject("team_id", UUID::class.java),
            status = ClearanceStatus.valueOf(rs.getString("status")),
            unmetRequirementCount = rs.getInt("unmet_requirement_count"),
            computedAt = rs.getTimestamp("computed_at").toInstant(),
        )
}
