package com.rally26.tournament.persistence

import com.rally26.tournament.domain.Tournament
import com.rally26.tournament.domain.TournamentStatus
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.Date
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

private const val TOURNAMENT_COLUMNS =
    "id, organization_id, name, sport, status, start_date, end_date, location, contact_email, created_at, updated_at"

@Repository
class TournamentRepository(private val jdbcClient: JdbcClient) {

    fun findById(id: UUID, organizationId: UUID): Tournament? =
        jdbcClient.sql(
            "select $TOURNAMENT_COLUMNS from tournament where id = :id and organization_id = :organizationId",
        )
            .param("id", id)
            .param("organizationId", organizationId)
            .query(::mapRow)
            .optional()
            .orElse(null)

    fun findAll(organizationId: UUID, offset: Int, limit: Int): List<Tournament> =
        jdbcClient.sql(
            """
            select $TOURNAMENT_COLUMNS from tournament
            where organization_id = :organizationId
            order by coalesce(start_date, '9999-12-31') asc, name asc
            offset :offset limit :limit
            """.trimIndent(),
        )
            .param("organizationId", organizationId)
            .param("offset", offset)
            .param("limit", limit)
            .query(::mapRow)
            .list()

    fun countAll(organizationId: UUID): Long =
        jdbcClient.sql("select count(*) from tournament where organization_id = :organizationId")
            .param("organizationId", organizationId)
            .query(Long::class.java)
            .single()

    /** Tournaments that haven't concluded yet (no end date, or end date in the future). */
    fun countUpcoming(organizationId: UUID): Long =
        jdbcClient.sql(
            """
            select count(*) from tournament
            where organization_id = :organizationId and status = 'ACTIVE'
              and (end_date is null or end_date >= current_date)
            """.trimIndent(),
        )
            .param("organizationId", organizationId)
            .query(Long::class.java)
            .single()

    fun insert(
        organizationId: UUID,
        name: String,
        sport: String?,
        startDate: LocalDate?,
        endDate: LocalDate?,
        location: String?,
        contactEmail: String?,
    ): Tournament {
        val now = Instant.now()
        val id = UUID.randomUUID()
        jdbcClient.sql(
            """
            insert into tournament
                (id, organization_id, name, sport, status, start_date, end_date, location, contact_email, created_at, updated_at)
            values
                (:id, :organizationId, :name, :sport, 'ACTIVE', :startDate, :endDate, :location, :contactEmail, :now, :now)
            """.trimIndent(),
        )
            .param("id", id)
            .param("organizationId", organizationId)
            .param("name", name)
            .param("sport", sport)
            .param("startDate", startDate?.let { Date.valueOf(it) })
            .param("endDate", endDate?.let { Date.valueOf(it) })
            .param("location", location)
            .param("contactEmail", contactEmail)
            .param("now", Timestamp.from(now))
            .update()
        return Tournament(
            id = id,
            organizationId = organizationId,
            name = name,
            sport = sport,
            status = TournamentStatus.ACTIVE,
            startDate = startDate,
            endDate = endDate,
            location = location,
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
        startDate: LocalDate?,
        endDate: LocalDate?,
        location: String?,
        contactEmail: String?,
    ): Int {
        val now = Instant.now()
        return jdbcClient.sql(
            """
            update tournament
            set name          = coalesce(:name, name),
                sport         = coalesce(:sport, sport),
                start_date    = coalesce(:startDate, start_date),
                end_date      = coalesce(:endDate, end_date),
                location      = coalesce(:location, location),
                contact_email = coalesce(:contactEmail, contact_email),
                updated_at    = :now
            where id = :id and organization_id = :organizationId
            """.trimIndent(),
        )
            .param("name", name)
            .param("sport", sport)
            .param("startDate", startDate?.let { Date.valueOf(it) })
            .param("endDate", endDate?.let { Date.valueOf(it) })
            .param("location", location)
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
            update tournament set status = 'ARCHIVED', updated_at = :now
            where id = :id and organization_id = :organizationId
            """.trimIndent(),
        )
            .param("now", Timestamp.from(now))
            .param("id", id)
            .param("organizationId", organizationId)
            .update()
    }

    private fun mapRow(rs: java.sql.ResultSet, rowNum: Int): Tournament =
        Tournament(
            id = rs.getObject("id", UUID::class.java),
            organizationId = rs.getObject("organization_id", UUID::class.java),
            name = rs.getString("name"),
            sport = rs.getString("sport"),
            status = TournamentStatus.valueOf(rs.getString("status")),
            startDate = rs.getDate("start_date")?.toLocalDate(),
            endDate = rs.getDate("end_date")?.toLocalDate(),
            location = rs.getString("location"),
            contactEmail = rs.getString("contact_email"),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant(),
        )
}
