package com.rally26.team.persistence

import com.rally26.team.domain.Sport
import com.rally26.team.domain.Team
import com.rally26.team.domain.TeamGenderCategory
import com.rally26.team.domain.TeamSearchCriteria
import com.rally26.team.domain.TeamSearchSort
import com.rally26.team.domain.TeamStatus
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.util.UUID

private const val TEAM_COLUMNS =
    "id, organization_id, name, sport, season, status, contact_email, timezone_override, " +
        "age_group, gender_category, level, primary_color, secondary_color, created_at, updated_at"

/**
 * `/teams/search` — the frontend's team list has always called this endpoint
 * (`frontend/src/features/teams/searchApi.ts`), but no backend mapping for it ever
 * existed: only the plain unfiltered `TeamController.list()` (see LAUNCH-READINESS.md
 * LR-016). Every request 500'd, matched by Spring to `TeamController.get(teamId)`
 * trying (and failing) to parse the literal path segment "search" as a UUID.
 */
@Repository
class TeamSearchRepository(
    private val jdbcClient: JdbcClient,
) {
    fun search(
        organizationId: UUID,
        criteria: TeamSearchCriteria,
        offset: Int,
        limit: Int,
    ): List<Team> {
        val built = buildSql(organizationId, criteria, countOnly = false)
        var statement = jdbcClient.sql("${built.first} offset :offset limit :limit").param("offset", offset).param("limit", limit)
        built.second.forEach { (name, value) -> statement = statement.param(name, value) }
        return statement.query(::mapRow).list()
    }

    fun count(
        organizationId: UUID,
        criteria: TeamSearchCriteria,
    ): Long {
        val built = buildSql(organizationId, criteria, countOnly = true)
        var statement = jdbcClient.sql(built.first)
        built.second.forEach { (name, value) -> statement = statement.param(name, value) }
        return statement.query(Long::class.java).single()
    }

    private fun buildSql(
        organizationId: UUID,
        criteria: TeamSearchCriteria,
        countOnly: Boolean,
    ): Pair<String, Map<String, Any>> {
        val sql =
            StringBuilder(
                if (countOnly) {
                    "select count(*) from team where organization_id = :organizationId"
                } else {
                    "select $TEAM_COLUMNS from team where organization_id = :organizationId"
                },
            )
        val params = linkedMapOf<String, Any>("organizationId" to organizationId)

        criteria.status?.let {
            sql.append(" and status = :status")
            params["status"] = it.name
        }
        criteria.sport?.trim()?.takeIf { it.isNotEmpty() }?.let {
            sql.append(" and lower(sport) = lower(:sport)")
            params["sport"] = it
        }
        criteria.season?.trim()?.takeIf { it.isNotEmpty() }?.let {
            sql.append(" and lower(season) = lower(:season)")
            params["season"] = it
        }
        criteria.genderCategory?.let {
            sql.append(" and gender_category = :genderCategory")
            params["genderCategory"] = it.name
        }
        criteria.keyword?.trim()?.takeIf { it.isNotEmpty() }?.let { keyword ->
            sql.append(
                " and (" +
                    " lower(name) like :keyword" +
                    " or lower(sport) like :keyword" +
                    " or lower(coalesce(season, '')) like :keyword" +
                    " or lower(coalesce(age_group, '')) like :keyword" +
                    " or lower(coalesce(level, '')) like :keyword" +
                    " )",
            )
            params["keyword"] = "%${keyword.lowercase()}%"
        }

        if (!countOnly) {
            sql.append(
                when (criteria.sort) {
                    TeamSearchSort.NAME_ASC -> " order by lower(name) asc, created_at asc"
                    TeamSearchSort.NAME_DESC -> " order by lower(name) desc, created_at desc"
                    TeamSearchSort.SPORT_ASC -> " order by lower(sport) asc, lower(name) asc"
                    TeamSearchSort.NEWEST -> " order by created_at desc, lower(name) asc"
                    TeamSearchSort.OLDEST -> " order by created_at asc, lower(name) asc"
                },
            )
        }
        return sql.toString() to params
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
            createdAt = rs.getTimestamp("created_at").toInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant(),
        )
}
