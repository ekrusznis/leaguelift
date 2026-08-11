package com.rally26.eligibility.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import com.rally26.eligibility.domain.EligibilityRequirement
import com.rally26.eligibility.domain.EvidenceClassification
import com.rally26.eligibility.domain.ExpirationRule
import com.rally26.eligibility.domain.GuardianActionMode
import com.rally26.eligibility.domain.RequirementMode
import com.rally26.eligibility.domain.RequirementStatus
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

private const val REQUIREMENT_COLUMNS =
    "id, organization_id, requirement_group_id, version, title, mode, content, content_hash, sport, program, season, " +
        "team_id, guardian_action_mode, athlete_self_sign_allowed, external_evidence_accepted, accepted_evidence_levels, " +
        "effective_start, effective_end, expiration_rule, expiration_days, status, created_at, created_by_user_id"

@Repository
class EligibilityRequirementRepository(
    private val jdbcClient: JdbcClient,
    private val objectMapper: ObjectMapper,
) {
    fun findById(
        id: UUID,
        organizationId: UUID,
    ): EligibilityRequirement? =
        jdbcClient
            .sql("select $REQUIREMENT_COLUMNS from eligibility_requirement where id = :id and organization_id = :organizationId")
            .param("id", id)
            .param("organizationId", organizationId)
            .query(::mapRow)
            .optional()
            .orElse(null)

    /** The current ACTIVE version of a requirement family, or null if the whole family has been archived. */
    fun findActiveByGroup(
        requirementGroupId: UUID,
        organizationId: UUID,
    ): EligibilityRequirement? =
        jdbcClient
            .sql(
                """
                select $REQUIREMENT_COLUMNS from eligibility_requirement
                where requirement_group_id = :requirementGroupId and organization_id = :organizationId and status = 'ACTIVE'
                """.trimIndent(),
            ).param("requirementGroupId", requirementGroupId)
            .param("organizationId", organizationId)
            .query(::mapRow)
            .optional()
            .orElse(null)

    /**
     * Every ACTIVE requirement whose scope could apply to a team assignment in this
     * sport/program/season — org-wide (team_id null) plus this specific team. Scope
     * narrowing (sport/program/season match) is the caller's job since a requirement's
     * own null fields mean "applies regardless of that dimension," not "excludes it."
     */
    fun listActiveForScope(
        organizationId: UUID,
        teamId: UUID,
    ): List<EligibilityRequirement> =
        jdbcClient
            .sql(
                """
                select $REQUIREMENT_COLUMNS from eligibility_requirement
                where organization_id = :organizationId and status = 'ACTIVE'
                  and (team_id is null or team_id = :teamId)
                order by created_at asc
                """.trimIndent(),
            ).param("organizationId", organizationId)
            .param("teamId", teamId)
            .query(::mapRow)
            .list()

    fun listActiveForOrganization(organizationId: UUID): List<EligibilityRequirement> =
        jdbcClient
            .sql(
                "select $REQUIREMENT_COLUMNS from eligibility_requirement where organization_id = :organizationId and status = 'ACTIVE' order by created_at asc",
            ).param("organizationId", organizationId)
            .query(::mapRow)
            .list()

    /** First version of a brand-new requirement family. Use [insertNewVersion] for a subsequent version of an existing family. */
    fun insertFirstVersion(
        organizationId: UUID,
        title: String,
        mode: RequirementMode,
        content: String,
        contentHash: String,
        sport: String?,
        program: String?,
        season: String?,
        teamId: UUID?,
        guardianActionMode: GuardianActionMode,
        athleteSelfSignAllowed: Boolean,
        externalEvidenceAccepted: Boolean,
        acceptedEvidenceLevels: Set<EvidenceClassification>,
        effectiveStart: LocalDate,
        effectiveEnd: LocalDate?,
        expirationRule: ExpirationRule,
        expirationDays: Int?,
        createdByUserId: UUID,
    ): EligibilityRequirement =
        insert(
            organizationId = organizationId,
            requirementGroupId = UUID.randomUUID(),
            version = 1,
            title = title,
            mode = mode,
            content = content,
            contentHash = contentHash,
            sport = sport,
            program = program,
            season = season,
            teamId = teamId,
            guardianActionMode = guardianActionMode,
            athleteSelfSignAllowed = athleteSelfSignAllowed,
            externalEvidenceAccepted = externalEvidenceAccepted,
            acceptedEvidenceLevels = acceptedEvidenceLevels,
            effectiveStart = effectiveStart,
            effectiveEnd = effectiveEnd,
            expirationRule = expirationRule,
            expirationDays = expirationDays,
            createdByUserId = createdByUserId,
        )

    /**
     * A new version of an existing requirement family — never mutates [previous]. The
     * caller (service layer, inside a transaction) is responsible for archiving
     * [previous] via [archive] so the unique-active-per-group index doesn't reject this
     * insert.
     */
    fun insertNewVersion(
        previous: EligibilityRequirement,
        title: String,
        content: String,
        contentHash: String,
        effectiveStart: LocalDate,
        effectiveEnd: LocalDate?,
        createdByUserId: UUID,
    ): EligibilityRequirement =
        insert(
            organizationId = previous.organizationId,
            requirementGroupId = previous.requirementGroupId,
            version = previous.version + 1,
            title = title,
            mode = previous.mode,
            content = content,
            contentHash = contentHash,
            sport = previous.sport,
            program = previous.program,
            season = previous.season,
            teamId = previous.teamId,
            guardianActionMode = previous.guardianActionMode,
            athleteSelfSignAllowed = previous.athleteSelfSignAllowed,
            externalEvidenceAccepted = previous.externalEvidenceAccepted,
            acceptedEvidenceLevels = previous.acceptedEvidenceLevels,
            effectiveStart = effectiveStart,
            effectiveEnd = effectiveEnd,
            expirationRule = previous.expirationRule,
            expirationDays = previous.expirationDays,
            createdByUserId = createdByUserId,
        )

    private fun insert(
        organizationId: UUID,
        requirementGroupId: UUID,
        version: Int,
        title: String,
        mode: RequirementMode,
        content: String,
        contentHash: String,
        sport: String?,
        program: String?,
        season: String?,
        teamId: UUID?,
        guardianActionMode: GuardianActionMode,
        athleteSelfSignAllowed: Boolean,
        externalEvidenceAccepted: Boolean,
        acceptedEvidenceLevels: Set<EvidenceClassification>,
        effectiveStart: LocalDate,
        effectiveEnd: LocalDate?,
        expirationRule: ExpirationRule,
        expirationDays: Int?,
        createdByUserId: UUID,
    ): EligibilityRequirement {
        val id = UUID.randomUUID()
        val now = Instant.now()
        val levelsJson = objectMapper.writeValueAsString(acceptedEvidenceLevels.map { it.name })
        jdbcClient
            .sql(
                """
                insert into eligibility_requirement (
                    id, organization_id, requirement_group_id, version, title, mode, content, content_hash,
                    sport, program, season, team_id, guardian_action_mode, athlete_self_sign_allowed,
                    external_evidence_accepted, accepted_evidence_levels, effective_start, effective_end,
                    expiration_rule, expiration_days, status, created_at, created_by_user_id
                ) values (
                    :id, :organizationId, :requirementGroupId, :version, :title, :mode, :content, :contentHash,
                    :sport, :program, :season, :teamId, :guardianActionMode, :athleteSelfSignAllowed,
                    :externalEvidenceAccepted, cast(:acceptedEvidenceLevels as jsonb), :effectiveStart, :effectiveEnd,
                    :expirationRule, :expirationDays, 'ACTIVE', :now, :createdByUserId
                )
                """.trimIndent(),
            ).param("id", id)
            .param("organizationId", organizationId)
            .param("requirementGroupId", requirementGroupId)
            .param("version", version)
            .param("title", title)
            .param("mode", mode.name)
            .param("content", content)
            .param("contentHash", contentHash)
            .param("sport", sport)
            .param("program", program)
            .param("season", season)
            .param("teamId", teamId)
            .param("guardianActionMode", guardianActionMode.name)
            .param("athleteSelfSignAllowed", athleteSelfSignAllowed)
            .param("externalEvidenceAccepted", externalEvidenceAccepted)
            .param("acceptedEvidenceLevels", levelsJson)
            .param("effectiveStart", effectiveStart)
            .param("effectiveEnd", effectiveEnd)
            .param("expirationRule", expirationRule.name)
            .param("expirationDays", expirationDays)
            .param("now", Timestamp.from(now))
            .param("createdByUserId", createdByUserId)
            .update()
        return EligibilityRequirement(
            id = id,
            organizationId = organizationId,
            requirementGroupId = requirementGroupId,
            version = version,
            title = title,
            mode = mode,
            content = content,
            contentHash = contentHash,
            sport = sport,
            program = program,
            season = season,
            teamId = teamId,
            guardianActionMode = guardianActionMode,
            athleteSelfSignAllowed = athleteSelfSignAllowed,
            externalEvidenceAccepted = externalEvidenceAccepted,
            acceptedEvidenceLevels = acceptedEvidenceLevels,
            effectiveStart = effectiveStart,
            effectiveEnd = effectiveEnd,
            expirationRule = expirationRule,
            expirationDays = expirationDays,
            status = RequirementStatus.ACTIVE,
            createdAt = now,
            createdByUserId = createdByUserId,
        )
    }

    fun archive(
        id: UUID,
        organizationId: UUID,
    ): Int =
        jdbcClient
            .sql("update eligibility_requirement set status = 'ARCHIVED' where id = :id and organization_id = :organizationId")
            .param("id", id)
            .param("organizationId", organizationId)
            .update()

    private fun mapRow(
        rs: ResultSet,
        rowNum: Int,
    ): EligibilityRequirement {
        val levels: List<String> = objectMapper.readValue(rs.getString("accepted_evidence_levels"), Array<String>::class.java).toList()
        return EligibilityRequirement(
            id = rs.getObject("id", UUID::class.java),
            organizationId = rs.getObject("organization_id", UUID::class.java),
            requirementGroupId = rs.getObject("requirement_group_id", UUID::class.java),
            version = rs.getInt("version"),
            title = rs.getString("title"),
            mode = RequirementMode.valueOf(rs.getString("mode")),
            content = rs.getString("content"),
            contentHash = rs.getString("content_hash"),
            sport = rs.getString("sport"),
            program = rs.getString("program"),
            season = rs.getString("season"),
            teamId = rs.getObject("team_id", UUID::class.java),
            guardianActionMode = GuardianActionMode.valueOf(rs.getString("guardian_action_mode")),
            athleteSelfSignAllowed = rs.getBoolean("athlete_self_sign_allowed"),
            externalEvidenceAccepted = rs.getBoolean("external_evidence_accepted"),
            acceptedEvidenceLevels = levels.map(EvidenceClassification::valueOf).toSet(),
            effectiveStart = rs.getObject("effective_start", LocalDate::class.java),
            effectiveEnd = rs.getObject("effective_end", LocalDate::class.java),
            expirationRule = ExpirationRule.valueOf(rs.getString("expiration_rule")),
            expirationDays = rs.getObject("expiration_days") as Int?,
            status = RequirementStatus.valueOf(rs.getString("status")),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            createdByUserId = rs.getObject("created_by_user_id", UUID::class.java),
        )
    }
}
