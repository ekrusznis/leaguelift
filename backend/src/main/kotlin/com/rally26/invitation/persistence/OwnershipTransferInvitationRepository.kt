package com.rally26.invitation.persistence

import com.rally26.invitation.domain.OwnershipTransferInvitation
import com.rally26.invitation.domain.OwnershipTransferInvitationStatus
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

private const val COLUMNS =
    "id, organization_id, email, status, invited_by_user_id, expires_at, accepted_at, created_at, updated_at"

@Repository
class OwnershipTransferInvitationRepository(
    private val jdbcClient: JdbcClient,
) {
    fun findById(id: UUID): OwnershipTransferInvitation? =
        jdbcClient
            .sql("select $COLUMNS from ownership_transfer_invitation where id = :id")
            .param("id", id)
            .query(::mapRow)
            .optional()
            .orElse(null)

    fun findByTokenHash(tokenHash: String): OwnershipTransferInvitation? =
        jdbcClient
            .sql("select $COLUMNS from ownership_transfer_invitation where token_hash = :tokenHash")
            .param("tokenHash", tokenHash)
            .query(::mapRow)
            .optional()
            .orElse(null)

    fun findPendingForOrganization(organizationId: UUID): OwnershipTransferInvitation? =
        jdbcClient
            .sql(
                "select $COLUMNS from ownership_transfer_invitation where organization_id = :organizationId and status = 'PENDING'",
            ).param("organizationId", organizationId)
            .query(::mapRow)
            .optional()
            .orElse(null)

    fun insert(
        organizationId: UUID,
        email: String,
        invitedByUserId: UUID,
        tokenHash: String,
        expiresAt: Instant,
    ): OwnershipTransferInvitation {
        val now = Instant.now()
        val id = UUID.randomUUID()
        jdbcClient
            .sql(
                """
                insert into ownership_transfer_invitation
                    (id, organization_id, email, status, invited_by_user_id, token_hash, expires_at, created_at, updated_at)
                values
                    (:id, :organizationId, :email, 'PENDING', :invitedByUserId, :tokenHash, :expiresAt, :now, :now)
                """.trimIndent(),
            ).param("id", id)
            .param("organizationId", organizationId)
            .param("email", email)
            .param("invitedByUserId", invitedByUserId)
            .param("tokenHash", tokenHash)
            .param("expiresAt", Timestamp.from(expiresAt))
            .param("now", Timestamp.from(now))
            .update()
        return OwnershipTransferInvitation(
            id = id,
            organizationId = organizationId,
            email = email,
            status = OwnershipTransferInvitationStatus.PENDING,
            invitedByUserId = invitedByUserId,
            expiresAt = expiresAt,
            acceptedAt = null,
            createdAt = now,
            updatedAt = now,
        )
    }

    fun markStatus(
        id: UUID,
        status: OwnershipTransferInvitationStatus,
        acceptedAt: Instant? = null,
    ): Int {
        val now = Instant.now()
        return jdbcClient
            .sql(
                """
                update ownership_transfer_invitation
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
    ): OwnershipTransferInvitation =
        OwnershipTransferInvitation(
            id = rs.getObject("id", UUID::class.java),
            organizationId = rs.getObject("organization_id", UUID::class.java),
            email = rs.getString("email"),
            status = OwnershipTransferInvitationStatus.valueOf(rs.getString("status")),
            invitedByUserId = rs.getObject("invited_by_user_id", UUID::class.java),
            expiresAt = rs.getTimestamp("expires_at").toInstant(),
            acceptedAt = rs.getTimestamp("accepted_at")?.toInstant(),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant(),
        )
}
