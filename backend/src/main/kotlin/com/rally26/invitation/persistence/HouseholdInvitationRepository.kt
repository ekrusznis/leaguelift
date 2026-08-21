package com.rally26.invitation.persistence

import com.rally26.invitation.domain.HouseholdInvitation
import com.rally26.invitation.domain.HouseholdInvitationKind
import com.rally26.invitation.domain.HouseholdInvitationStatus
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

private const val COLUMNS =
    "id, organization_id, household_id, kind, household_adult_id, participant_id, email, status, " +
        "invited_by_user_id, expires_at, accepted_at, created_at, updated_at"

@Repository
class HouseholdInvitationRepository(
    private val jdbcClient: JdbcClient,
) {
    fun findById(id: UUID): HouseholdInvitation? =
        jdbcClient
            .sql("select $COLUMNS from household_invitation where id = :id")
            .param("id", id)
            .query(::mapRow)
            .optional()
            .orElse(null)

    fun findByTokenHash(tokenHash: String): HouseholdInvitation? =
        jdbcClient
            .sql("select $COLUMNS from household_invitation where token_hash = :tokenHash")
            .param("tokenHash", tokenHash)
            .query(::mapRow)
            .optional()
            .orElse(null)

    fun listPendingForHousehold(
        householdId: UUID,
        organizationId: UUID,
    ): List<HouseholdInvitation> =
        jdbcClient
            .sql(
                """
                select $COLUMNS from household_invitation
                where household_id = :householdId and organization_id = :organizationId and status = 'PENDING'
                order by created_at desc
                """.trimIndent(),
            ).param("householdId", householdId)
            .param("organizationId", organizationId)
            .query(::mapRow)
            .list()

    fun insert(
        organizationId: UUID,
        householdId: UUID,
        kind: HouseholdInvitationKind,
        householdAdultId: UUID?,
        participantId: UUID,
        email: String,
        invitedByUserId: UUID,
        tokenHash: String,
        expiresAt: Instant,
    ): HouseholdInvitation {
        val now = Instant.now()
        val id = UUID.randomUUID()
        jdbcClient
            .sql(
                """
                insert into household_invitation
                    (id, organization_id, household_id, kind, household_adult_id, participant_id, email, status,
                     invited_by_user_id, token_hash, expires_at, created_at, updated_at)
                values
                    (:id, :organizationId, :householdId, :kind, :householdAdultId, :participantId, :email, 'PENDING',
                     :invitedByUserId, :tokenHash, :expiresAt, :now, :now)
                """.trimIndent(),
            ).param("id", id)
            .param("organizationId", organizationId)
            .param("householdId", householdId)
            .param("kind", kind.name)
            .param("householdAdultId", householdAdultId)
            .param("participantId", participantId)
            .param("email", email)
            .param("invitedByUserId", invitedByUserId)
            .param("tokenHash", tokenHash)
            .param("expiresAt", Timestamp.from(expiresAt))
            .param("now", Timestamp.from(now))
            .update()
        return HouseholdInvitation(
            id = id,
            organizationId = organizationId,
            householdId = householdId,
            kind = kind,
            householdAdultId = householdAdultId,
            participantId = participantId,
            email = email,
            status = HouseholdInvitationStatus.PENDING,
            invitedByUserId = invitedByUserId,
            expiresAt = expiresAt,
            acceptedAt = null,
            createdAt = now,
            updatedAt = now,
        )
    }

    fun rotateToken(
        id: UUID,
        tokenHash: String,
        expiresAt: Instant,
    ): Int {
        val now = Instant.now()
        return jdbcClient
            .sql(
                """
                update household_invitation
                set token_hash = :tokenHash, expires_at = :expiresAt, updated_at = :now
                where id = :id
                """.trimIndent(),
            ).param("tokenHash", tokenHash)
            .param("expiresAt", Timestamp.from(expiresAt))
            .param("now", Timestamp.from(now))
            .param("id", id)
            .update()
    }

    fun markStatus(
        id: UUID,
        status: HouseholdInvitationStatus,
        acceptedAt: Instant? = null,
    ): Int {
        val now = Instant.now()
        return jdbcClient
            .sql(
                """
                update household_invitation
                set status = :status, accepted_at = coalesce(:acceptedAt, accepted_at), updated_at = :now
                where id = :id
                """.trimIndent(),
            ).param("status", status.name)
            .param("acceptedAt", acceptedAt?.let { Timestamp.from(it) })
            .param("now", Timestamp.from(now))
            .param("id", id)
            .update()
    }

    private fun mapRow(
        rs: java.sql.ResultSet,
        rowNum: Int,
    ): HouseholdInvitation =
        HouseholdInvitation(
            id = rs.getObject("id", UUID::class.java),
            organizationId = rs.getObject("organization_id", UUID::class.java),
            householdId = rs.getObject("household_id", UUID::class.java),
            kind = HouseholdInvitationKind.valueOf(rs.getString("kind")),
            householdAdultId = rs.getObject("household_adult_id", UUID::class.java),
            participantId = rs.getObject("participant_id", UUID::class.java),
            email = rs.getString("email"),
            status = HouseholdInvitationStatus.valueOf(rs.getString("status")),
            invitedByUserId = rs.getObject("invited_by_user_id", UUID::class.java),
            expiresAt = rs.getTimestamp("expires_at").toInstant(),
            acceptedAt = rs.getTimestamp("accepted_at")?.toInstant(),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant(),
        )
}
