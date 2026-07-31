package com.leaguelift.platformadmin.persistence

import com.leaguelift.platformadmin.domain.PlatformSupportAccess
import com.leaguelift.platformadmin.domain.PlatformSupportAccessStatus
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Repository
class PlatformSupportAccessRepository(private val jdbcClient: JdbcClient) {

	fun create(platformAdminUserId: UUID, organizationId: UUID, reason: String, createdAt: Instant, expiresAt: Instant): PlatformSupportAccess {
		val id = UUID.randomUUID()
		jdbcClient.sql(
			"""
			insert into platform_support_access
			(id, platform_admin_user_id, organization_id, reason, status, expires_at, created_at)
			values (:id, :userId, :organizationId, :reason, 'ACTIVE', :expiresAt, :createdAt)
			""".trimIndent(),
		)
			.param("id", id)
			.param("userId", platformAdminUserId)
			.param("organizationId", organizationId)
			.param("reason", reason)
			.param("expiresAt", Timestamp.from(expiresAt))
			.param("createdAt", Timestamp.from(createdAt))
			.update()
		return findById(id) ?: error("Created platform support access $id could not be read")
	}

	fun findById(id: UUID): PlatformSupportAccess? =
		jdbcClient.sql(
			"""
			select psa.id, psa.platform_admin_user_id, psa.organization_id, o.name as organization_name,
			       psa.reason, psa.status, psa.expires_at, psa.ended_at, psa.created_at
			from platform_support_access psa
			join organization o on o.id = psa.organization_id
			where psa.id = :id
			""".trimIndent(),
		)
			.param("id", id)
			.query(::mapRow)
			.optional().orElse(null)

	fun findActiveForAdmin(platformAdminUserId: UUID): PlatformSupportAccess? =
		jdbcClient.sql(
			"""
			select psa.id, psa.platform_admin_user_id, psa.organization_id, o.name as organization_name,
			       psa.reason, psa.status, psa.expires_at, psa.ended_at, psa.created_at
			from platform_support_access psa
			join organization o on o.id = psa.organization_id
			where psa.platform_admin_user_id = :userId and psa.status = 'ACTIVE'
			order by psa.created_at desc
			limit 1
			""".trimIndent(),
		)
			.param("userId", platformAdminUserId)
			.query(::mapRow)
			.optional().orElse(null)

	fun endActiveForAdmin(platformAdminUserId: UUID, endedAt: Instant): Int =
		jdbcClient.sql(
			"""
			update platform_support_access
			set status = 'ENDED', ended_at = :endedAt
			where platform_admin_user_id = :userId and status = 'ACTIVE'
			""".trimIndent(),
		)
			.param("endedAt", Timestamp.from(endedAt))
			.param("userId", platformAdminUserId)
			.update()

	fun end(id: UUID, platformAdminUserId: UUID, endedAt: Instant): Int =
		jdbcClient.sql(
			"""
			update platform_support_access
			set status = 'ENDED', ended_at = :endedAt
			where id = :id and platform_admin_user_id = :userId and status = 'ACTIVE'
			""".trimIndent(),
		)
			.param("endedAt", Timestamp.from(endedAt))
			.param("id", id)
			.param("userId", platformAdminUserId)
			.update()

	fun expire(id: UUID, expiredAt: Instant): Int =
		jdbcClient.sql(
			"""
			update platform_support_access
			set status = 'EXPIRED', ended_at = :expiredAt
			where id = :id and status = 'ACTIVE'
			""".trimIndent(),
		)
			.param("expiredAt", Timestamp.from(expiredAt))
			.param("id", id)
			.update()

	private fun mapRow(rs: java.sql.ResultSet, rowNum: Int): PlatformSupportAccess =
		PlatformSupportAccess(
			id = rs.getObject("id", UUID::class.java),
			platformAdminUserId = rs.getObject("platform_admin_user_id", UUID::class.java),
			organizationId = rs.getObject("organization_id", UUID::class.java),
			organizationName = rs.getString("organization_name"),
			reason = rs.getString("reason"),
			status = PlatformSupportAccessStatus.valueOf(rs.getString("status")),
			expiresAt = rs.getTimestamp("expires_at").toInstant(),
			endedAt = rs.getTimestamp("ended_at")?.toInstant(),
			createdAt = rs.getTimestamp("created_at").toInstant(),
		)
}
