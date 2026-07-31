package com.leaguelift.invitation.persistence

import com.leaguelift.invitation.domain.Invitation
import com.leaguelift.invitation.domain.InvitationStatus
import com.leaguelift.membership.domain.MembershipRole
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

private const val INVITATION_COLUMNS =
	"id, organization_id, email, role, status, invited_by_user_id, token, token_hash, expires_at, accepted_at, created_at, updated_at"

@Repository
class InvitationRepository(private val jdbcClient: JdbcClient) {

	fun findById(id: UUID): Invitation? =
		jdbcClient.sql("select $INVITATION_COLUMNS from invitation where id = :id")
			.param("id", id)
			.query(::mapRow)
			.optional()
			.orElse(null)

	fun findByTokenHash(tokenHash: String): Invitation? =
		jdbcClient.sql("select $INVITATION_COLUMNS from invitation where token_hash = :tokenHash")
			.param("tokenHash", tokenHash)
			.query(::mapRow)
			.optional()
			.orElse(null)

	fun listPendingForOrganization(organizationId: UUID, offset: Int, limit: Int): List<Invitation> =
		jdbcClient.sql(
			"""
			select $INVITATION_COLUMNS from invitation
			where organization_id = :organizationId and status = 'PENDING'
			order by created_at desc
			offset :offset limit :limit
			""".trimIndent(),
		)
			.param("organizationId", organizationId)
			.param("offset", offset)
			.param("limit", limit)
			.query(::mapRow)
			.list()

	fun countPendingForOrganization(organizationId: UUID): Long =
		jdbcClient.sql("select count(*) from invitation where organization_id = :organizationId and status = 'PENDING'")
			.param("organizationId", organizationId)
			.query(Long::class.java)
			.single()

	fun findPendingForOrganizationAndEmail(organizationId: UUID, email: String): Invitation? =
		jdbcClient.sql(
			"""
			select $INVITATION_COLUMNS from invitation
			where organization_id = :organizationId and lower(email) = lower(:email) and status = 'PENDING'
			limit 1
			""".trimIndent(),
		)
			.param("organizationId", organizationId)
			.param("email", email)
			.query(::mapRow)
			.optional().orElse(null)

	fun insert(
		organizationId: UUID,
		email: String,
		role: MembershipRole,
		invitedByUserId: UUID,
		tokenReference: String,
		tokenHash: String,
		expiresAt: Instant,
	): Invitation {
		val now = Instant.now()
		val id = UUID.randomUUID()
		jdbcClient.sql(
			"""
			insert into invitation
				(id, organization_id, email, role, status, invited_by_user_id, token, token_hash, expires_at, created_at, updated_at)
			values
				(:id, :organizationId, :email, :role, 'PENDING', :invitedByUserId, :tokenReference, :tokenHash, :expiresAt, :now, :now)
			""".trimIndent(),
		)
			.param("id", id)
			.param("organizationId", organizationId)
			.param("email", email)
			.param("role", role.name)
			.param("invitedByUserId", invitedByUserId)
			.param("tokenReference", tokenReference)
			.param("tokenHash", tokenHash)
			.param("expiresAt", Timestamp.from(expiresAt))
			.param("now", Timestamp.from(now))
			.update()
		return Invitation(
			id = id,
			organizationId = organizationId,
			email = email,
			role = role,
			status = InvitationStatus.PENDING,
			invitedByUserId = invitedByUserId,
			token = tokenReference,
			expiresAt = expiresAt,
			acceptedAt = null,
			createdAt = now,
			updatedAt = now,
		)
	}

	fun rotateToken(id: UUID, tokenReference: String, tokenHash: String, expiresAt: Instant): Int {
		val now = Instant.now()
		return jdbcClient.sql(
			"""
			update invitation
			set token = :tokenReference,
			    token_hash = :tokenHash,
			    expires_at = :expiresAt,
			    updated_at = :now
			where id = :id
			""".trimIndent(),
		)
			.param("tokenReference", tokenReference)
			.param("tokenHash", tokenHash)
			.param("expiresAt", Timestamp.from(expiresAt))
			.param("now", Timestamp.from(now))
			.param("id", id)
			.update()
	}

	fun markStatus(id: UUID, status: InvitationStatus, acceptedAt: Instant? = null): Int {
		val now = Instant.now()
		return jdbcClient.sql(
			"""
			update invitation
			set status = :status, accepted_at = coalesce(:acceptedAt, accepted_at), updated_at = :now
			where id = :id
			""".trimIndent(),
		)
			.param("status", status.name)
			.param("acceptedAt", acceptedAt?.let { Timestamp.from(it) })
			.param("now", Timestamp.from(now))
			.param("id", id)
			.update()
	}

	private fun mapRow(rs: java.sql.ResultSet, rowNum: Int): Invitation =
		Invitation(
			id = rs.getObject("id", UUID::class.java),
			organizationId = rs.getObject("organization_id", UUID::class.java),
			email = rs.getString("email"),
			role = MembershipRole.valueOf(rs.getString("role")),
			status = InvitationStatus.valueOf(rs.getString("status")),
			invitedByUserId = rs.getObject("invited_by_user_id", UUID::class.java),
			token = rs.getString("token"),
			expiresAt = rs.getTimestamp("expires_at").toInstant(),
			acceptedAt = rs.getTimestamp("accepted_at")?.toInstant(),
			createdAt = rs.getTimestamp("created_at").toInstant(),
			updatedAt = rs.getTimestamp("updated_at").toInstant(),
		)
}
