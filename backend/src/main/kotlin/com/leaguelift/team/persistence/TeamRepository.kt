package com.leaguelift.team.persistence

import com.leaguelift.team.domain.Team
import com.leaguelift.team.domain.TeamStatus
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

private const val TEAM_COLUMNS = "id, organization_id, name, sport, season, status, contact_email, created_at, updated_at"

@Repository
class TeamRepository(private val jdbcClient: JdbcClient) {

    fun findById(id: UUID, organizationId: UUID): Team? =
        jdbcClient.sql("select $TEAM_COLUMNS from team where id = :id and organization_id = :organizationId")
            .param("id", id)
            .param("organizationId", organizationId)
            .query(::mapRow)
            .optional()
            .orElse(null)

    fun findNameMatches(organizationId: UUID, name: String): List<Team> =
        jdbcClient.sql(
            "select $TEAM_COLUMNS from team where organization_id = :organizationId and lower(name) = lower(:name) and status = 'ACTIVE' order by created_at asc limit 2",
        )
            .param("organizationId", organizationId)
            .param("name", name)
            .query(::mapRow)
            .list()

    fun findAll(organizationId: UUID, offset: Int, limit: Int): List<Team> =
        jdbcClient.sql(
            """
            select $TEAM_COLUMNS from team
            where organization_id = :organizationId
            order by name asc
            offset :offset limit :limit
            """.trimIndent(),
        )
            .param("organizationId", organizationId)
            .param("offset", offset)
            .param("limit", limit)
            .query(::mapRow)
            .list()

    fun countAll(organizationId: UUID): Long =
        jdbcClient.sql("select count(*) from team where organization_id = :organizationId")
            .param("organizationId", organizationId)
            .query(Long::class.java)
            .single()

    fun insert(organizationId: UUID, name: String, sport: String, season: String?, contactEmail: String?): Team {
        val now = Instant.now()
        val id = UUID.randomUUID()
        jdbcClient.sql(
            """
            insert into team (id, organization_id, name, sport, season, status, contact_email, created_at, updated_at)
            values (:id, :organizationId, :name, :sport, :season, 'ACTIVE', :contactEmail, :now, :now)
            """.trimIndent(),
        )
            .param("id", id)
            .param("organizationId", organizationId)
            .param("name", name)
            .param("sport", sport)
            .param("season", season)
            .param("contactEmail", contactEmail)
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
            createdAt = now,
            updatedAt = now,
        )
    }

    fun update(
        id: UUID,
        organizationId: UUID,
        name: String?,
        sport: String?,
        season: String?,
        contactEmail: String?,
    ): Int {
        val now = Instant.now()
        return jdbcClient.sql(
            """
            update team
            set name          = coalesce(:name, name),
                sport         = coalesce(:sport, sport),
                season        = coalesce(:season, season),
                contact_email = coalesce(:contactEmail, contact_email),
                updated_at    = :now
            where id = :id and organization_id = :organizationId
            """.trimIndent(),
        )
            .param("name", name)
            .param("sport", sport)
            .param("season", season)
            .param("contactEmail", contactEmail)
            .param("now", Timestamp.from(now))
            .param("id", id)
            .param("organizationId", organizationId)
            .update()
    }

    fun archive(id: UUID, organizationId: UUID): Int {
        val now = Instant.now()
        return jdbcClient.sql(
            """
            update team set status = 'ARCHIVED', updated_at = :now
            where id = :id and organization_id = :organizationId
            """.trimIndent(),
        )
            .param("now", Timestamp.from(now))
            .param("id", id)
            .param("organizationId", organizationId)
            .update()
    }

    private fun mapRow(rs: java.sql.ResultSet, rowNum: Int): Team =
        Team(
            id = rs.getObject("id", UUID::class.java),
            organizationId = rs.getObject("organization_id", UUID::class.java),
            name = rs.getString("name"),
            sport = rs.getString("sport"),
            season = rs.getString("season"),
            status = TeamStatus.valueOf(rs.getString("status")),
            contactEmail = rs.getString("contact_email"),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant(),
        )
}
