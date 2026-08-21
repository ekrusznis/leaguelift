package com.rally26.team.persistence

import com.rally26.team.domain.Sport
import com.rally26.team.domain.Team
import com.rally26.team.domain.TeamGenderCategory
import com.rally26.team.domain.TeamStatus
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

private const val TEAM_COLUMNS =
    "id, organization_id, name, sport, season, status, contact_email, timezone_override, " +
        "age_group, gender_category, level, primary_color, secondary_color, sport_other_label, created_at, updated_at"

@Repository
class TeamRepository(
    private val jdbcClient: JdbcClient,
) {
    fun findById(
        id: UUID,
        organizationId: UUID,
    ): Team? =
        jdbcClient
            .sql("select $TEAM_COLUMNS from team where id = :id and organization_id = :organizationId")
            .param("id", id)
            .param("organizationId", organizationId)
            .query(::mapRow)
            .optional()
            .orElse(null)

    fun findNameMatches(
        organizationId: UUID,
        name: String,
    ): List<Team> =
        jdbcClient
            .sql(
                "select $TEAM_COLUMNS from team where organization_id = :organizationId and lower(name) = lower(:name) and status = 'ACTIVE' order by created_at asc limit 2",
            ).param("organizationId", organizationId)
            .param("name", name)
            .query(::mapRow)
            .list()

    fun findAll(
        organizationId: UUID,
        offset: Int,
        limit: Int,
    ): List<Team> =
        jdbcClient
            .sql(
                """
                select $TEAM_COLUMNS from team
                where organization_id = :organizationId
                order by name asc
                offset :offset limit :limit
                """.trimIndent(),
            ).param("organizationId", organizationId)
            .param("offset", offset)
            .param("limit", limit)
            .query(::mapRow)
            .list()

    fun countAll(organizationId: UUID): Long =
        jdbcClient
            .sql("select count(*) from team where organization_id = :organizationId")
            .param("organizationId", organizationId)
            .query(Long::class.java)
            .single()

    fun insert(
        organizationId: UUID,
        name: String,
        sport: Sport,
        season: String?,
        contactEmail: String?,
        ageGroup: String?,
        genderCategory: TeamGenderCategory?,
        level: String?,
        sportOtherLabel: String? = null,
    ): Team {
        val now = Instant.now()
        val id = UUID.randomUUID()
        jdbcClient
            .sql(
                """
                insert into team (
                    id, organization_id, name, sport, season, status, contact_email,
                    age_group, gender_category, level, sport_other_label, created_at, updated_at
                )
                values (
                    :id, :organizationId, :name, :sport, :season, 'ACTIVE', :contactEmail,
                    :ageGroup, :genderCategory, :level, :sportOtherLabel, :now, :now
                )
                """.trimIndent(),
            ).param("id", id)
            .param("organizationId", organizationId)
            .param("name", name)
            .param("sport", sport.name)
            .param("season", season)
            .param("contactEmail", contactEmail)
            .param("ageGroup", ageGroup)
            .param("genderCategory", genderCategory?.name)
            .param("level", level)
            .param("sportOtherLabel", sportOtherLabel)
            .param("now", Timestamp.from(now))
            .update()
        return Team(
            id = id,
            organizationId = organizationId,
            name = name,
            sport = sport,
            season = season,
            status = TeamStatus.ACTIVE,
            contactEmail = contactEmail,
            ageGroup = ageGroup,
            genderCategory = genderCategory,
            level = level,
            sportOtherLabel = sportOtherLabel,
            createdAt = now,
            updatedAt = now,
        )
    }

    fun update(
        id: UUID,
        organizationId: UUID,
        name: String?,
        sport: Sport?,
        season: String?,
        contactEmail: String?,
        ageGroup: String?,
        genderCategory: TeamGenderCategory?,
        level: String?,
        sportOtherLabel: String? = null,
    ): Int {
        val now = Instant.now()
        return jdbcClient
            .sql(
                """
                update team
                set name              = coalesce(:name, name),
                    sport             = coalesce(:sport, sport),
                    season            = coalesce(:season, season),
                    contact_email     = coalesce(:contactEmail, contact_email),
                    age_group         = coalesce(:ageGroup, age_group),
                    gender_category   = coalesce(:genderCategory, gender_category),
                    level             = coalesce(:level, level),
                    sport_other_label = case when :sport is not null then :sportOtherLabel else sport_other_label end,
                    updated_at        = :now
                where id = :id and organization_id = :organizationId
                """.trimIndent(),
            ).param("name", name)
            .param("sport", sport?.name)
            .param("season", season)
            .param("contactEmail", contactEmail)
            .param("ageGroup", ageGroup)
            .param("genderCategory", genderCategory?.name)
            .param("level", level)
            .param("sportOtherLabel", sportOtherLabel)
            .param("now", Timestamp.from(now))
            .param("id", id)
            .param("organizationId", organizationId)
            .update()
    }

    fun archive(
        id: UUID,
        organizationId: UUID,
    ): Int {
        val now = Instant.now()
        return jdbcClient
            .sql(
                """
                update team set status = 'ARCHIVED', updated_at = :now
                where id = :id and organization_id = :organizationId
                """.trimIndent(),
            ).param("now", Timestamp.from(now))
            .param("id", id)
            .param("organizationId", organizationId)
            .update()
    }

    /** Phase 24 slice 24.5 (ADR-071): explicit set/clear, not coalesce — [timezoneOverride] null must actually clear back to "inherit organization default." */
    fun updateTimezoneOverride(
        id: UUID,
        organizationId: UUID,
        timezoneOverride: String?,
    ): Int {
        val now = Instant.now()
        return jdbcClient
            .sql(
                """
                update team set timezone_override = :timezoneOverride, updated_at = :now
                where id = :id and organization_id = :organizationId
                """.trimIndent(),
            ).param("timezoneOverride", timezoneOverride)
            .param("now", Timestamp.from(now))
            .param("id", id)
            .param("organizationId", organizationId)
            .update()
    }

    /** Phase 35 (ADR-099): explicit set/clear, not coalesce — null must actually clear back to Rally26's default brand color. */
    fun updateColors(
        id: UUID,
        organizationId: UUID,
        primaryColor: String?,
        secondaryColor: String?,
    ): Int {
        val now = Instant.now()
        return jdbcClient
            .sql(
                """
                update team set primary_color = :primaryColor, secondary_color = :secondaryColor, updated_at = :now
                where id = :id and organization_id = :organizationId
                """.trimIndent(),
            ).param("primaryColor", primaryColor)
            .param("secondaryColor", secondaryColor)
            .param("now", Timestamp.from(now))
            .param("id", id)
            .param("organizationId", organizationId)
            .update()
    }

    private fun mapRow(
        rs: java.sql.ResultSet,
        rowNum: Int,
    ): Team =
        Team(
            id = rs.getObject("id", UUID::class.java),
            organizationId = rs.getObject("organization_id", UUID::class.java),
            name = rs.getString("name"),
            sport = Sport.valueOf(rs.getString("sport")),
            season = rs.getString("season"),
            status = TeamStatus.valueOf(rs.getString("status")),
            contactEmail = rs.getString("contact_email"),
            timezoneOverride = rs.getString("timezone_override"),
            ageGroup = rs.getString("age_group"),
            genderCategory = rs.getString("gender_category")?.let(TeamGenderCategory::valueOf),
            level = rs.getString("level"),
            primaryColor = rs.getString("primary_color"),
            secondaryColor = rs.getString("secondary_color"),
            sportOtherLabel = rs.getString("sport_other_label"),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant(),
        )
}
