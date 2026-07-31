package com.leaguelift.participant.persistence

import com.leaguelift.participant.domain.Participant
import com.leaguelift.participant.domain.ParticipantStatus
import com.leaguelift.participant.domain.ParticipantTeamAssignment
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.Date
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

private const val P_COLS =
    "id, household_id, organization_id, first_name, last_name, date_of_birth, notes, status, created_at, updated_at"

private const val PT_COLS =
    "id, participant_id, team_id, organization_id, status, joined_at, created_at, updated_at"

@Repository
class ParticipantRepository(private val jdbcClient: JdbcClient) {

    fun findById(id: UUID, organizationId: UUID): Participant? =
        jdbcClient.sql("select $P_COLS from participant where id = :id and organization_id = :organizationId")
            .param("id", id).param("organizationId", organizationId)
            .query(::mapParticipant).optional().orElse(null)

    fun countActiveForOrganization(organizationId: UUID): Long =
        jdbcClient.sql("select count(*) from participant where organization_id = :organizationId and status = 'ACTIVE'")
            .param("organizationId", organizationId)
            .query(Long::class.java).single()

    fun countActiveForTeam(teamId: UUID, organizationId: UUID): Long =
        jdbcClient.sql(
            """
            select count(*) from participant_team pt
            join participant p on p.id = pt.participant_id
            where pt.team_id = :teamId and pt.organization_id = :organizationId
              and pt.status = 'ACTIVE' and p.status = 'ACTIVE'
            """.trimIndent(),
        )
            .param("teamId", teamId).param("organizationId", organizationId)
            .query(Long::class.java).single()

    /** Active participants assigned to a team — used for guardian/athlete event-detail authorization. */
    fun findActiveByTeam(teamId: UUID, organizationId: UUID): List<Participant> =
        jdbcClient.sql(
            """
            select p.id, p.household_id, p.organization_id, p.first_name, p.last_name, p.date_of_birth, p.notes, p.status, p.created_at, p.updated_at from participant p
            join participant_team pt on pt.participant_id = p.id and pt.organization_id = p.organization_id
            where pt.team_id = :teamId and pt.organization_id = :organizationId
              and pt.status = 'ACTIVE' and p.status = 'ACTIVE'
            order by p.last_name asc, p.first_name asc
            """.trimIndent(),
        )
            .param("teamId", teamId).param("organizationId", organizationId)
            .query(::mapParticipant).list()

    fun findByHousehold(householdId: UUID, organizationId: UUID): List<Participant> =
        jdbcClient.sql(
            """
            select $P_COLS from participant
            where household_id = :householdId and organization_id = :organizationId and status != 'ARCHIVED'
            order by last_name asc, first_name asc
            """.trimIndent(),
        )
            .param("householdId", householdId).param("organizationId", organizationId)
            .query(::mapParticipant).list()

    fun insert(
        householdId: UUID,
        organizationId: UUID,
        firstName: String,
        lastName: String,
        dateOfBirth: LocalDate?,
        notes: String?,
    ): Participant {
        val now = Instant.now()
        val id = UUID.randomUUID()
        jdbcClient.sql(
            """
            insert into participant (id, household_id, organization_id, first_name, last_name, date_of_birth, notes, status, created_at, updated_at)
            values (:id, :householdId, :organizationId, :firstName, :lastName, :dateOfBirth, :notes, 'ACTIVE', :now, :now)
            """.trimIndent(),
        )
            .param("id", id).param("householdId", householdId).param("organizationId", organizationId)
            .param("firstName", firstName).param("lastName", lastName)
            .param("dateOfBirth", dateOfBirth?.let { Date.valueOf(it) })
            .param("notes", notes).param("now", Timestamp.from(now)).update()
        return Participant(id, householdId, organizationId, firstName, lastName, dateOfBirth, notes, ParticipantStatus.ACTIVE, now, now)
    }

    fun update(id: UUID, organizationId: UUID, firstName: String?, lastName: String?, dateOfBirth: LocalDate?, notes: String?): Int {
        val now = Instant.now()
        return jdbcClient.sql(
            """
            update participant
            set first_name    = coalesce(:firstName, first_name),
                last_name     = coalesce(:lastName, last_name),
                date_of_birth = coalesce(:dateOfBirth, date_of_birth),
                notes         = coalesce(:notes, notes),
                updated_at    = :now
            where id = :id and organization_id = :organizationId
            """.trimIndent(),
        )
            .param("firstName", firstName).param("lastName", lastName)
            .param("dateOfBirth", dateOfBirth?.let { Date.valueOf(it) })
            .param("notes", notes).param("now", Timestamp.from(now))
            .param("id", id).param("organizationId", organizationId).update()
    }

    fun listTeamAssignments(participantId: UUID, organizationId: UUID): List<ParticipantTeamAssignment> =
        jdbcClient.sql(
            """
            select $PT_COLS from participant_team
            where participant_id = :participantId and organization_id = :organizationId and status = 'ACTIVE'
            """.trimIndent(),
        )
            .param("participantId", participantId).param("organizationId", organizationId)
            .query(::mapAssignment).list()

    fun assignToTeam(participantId: UUID, teamId: UUID, organizationId: UUID, joinedAt: LocalDate?): ParticipantTeamAssignment {
        val now = Instant.now()
        val id = UUID.randomUUID()
        jdbcClient.sql(
            """
            insert into participant_team (id, participant_id, team_id, organization_id, status, joined_at, created_at, updated_at)
            values (:id, :participantId, :teamId, :organizationId, 'ACTIVE', :joinedAt, :now, :now)
            """.trimIndent(),
        )
            .param("id", id).param("participantId", participantId).param("teamId", teamId)
            .param("organizationId", organizationId).param("joinedAt", joinedAt?.let { Date.valueOf(it) })
            .param("now", Timestamp.from(now)).update()
        return ParticipantTeamAssignment(id, participantId, teamId, organizationId, "ACTIVE", joinedAt, now, now)
    }

    fun removeFromTeam(participantId: UUID, teamId: UUID, organizationId: UUID): Int {
        val now = Instant.now()
        return jdbcClient.sql(
            """
            update participant_team set status = 'INACTIVE', updated_at = :now
            where participant_id = :participantId and team_id = :teamId and organization_id = :organizationId
            """.trimIndent(),
        )
            .param("now", Timestamp.from(now)).param("participantId", participantId)
            .param("teamId", teamId).param("organizationId", organizationId).update()
    }

    private fun mapParticipant(rs: java.sql.ResultSet, row: Int) = Participant(
        id = rs.getObject("id", UUID::class.java),
        householdId = rs.getObject("household_id", UUID::class.java),
        organizationId = rs.getObject("organization_id", UUID::class.java),
        firstName = rs.getString("first_name"),
        lastName = rs.getString("last_name"),
        dateOfBirth = rs.getDate("date_of_birth")?.toLocalDate(),
        notes = rs.getString("notes"),
        status = ParticipantStatus.valueOf(rs.getString("status")),
        createdAt = rs.getTimestamp("created_at").toInstant(),
        updatedAt = rs.getTimestamp("updated_at").toInstant(),
    )

    private fun mapAssignment(rs: java.sql.ResultSet, row: Int) = ParticipantTeamAssignment(
        id = rs.getObject("id", UUID::class.java),
        participantId = rs.getObject("participant_id", UUID::class.java),
        teamId = rs.getObject("team_id", UUID::class.java),
        organizationId = rs.getObject("organization_id", UUID::class.java),
        status = rs.getString("status"),
        joinedAt = rs.getDate("joined_at")?.toLocalDate(),
        createdAt = rs.getTimestamp("created_at").toInstant(),
        updatedAt = rs.getTimestamp("updated_at").toInstant(),
    )
}
