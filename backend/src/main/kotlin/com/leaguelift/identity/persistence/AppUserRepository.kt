package com.leaguelift.identity.persistence

import com.leaguelift.identity.domain.AppUser
import com.leaguelift.identity.domain.AppUserStatus
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
class AppUserRepository(private val jdbcClient: JdbcClient) {

	fun findByExternalSubject(externalSubject: String): AppUser? =
		jdbcClient.sql(
			"""
			select id, external_subject, email, display_name, status, created_at, updated_at
			from app_user
			where external_subject = :externalSubject
			""".trimIndent(),
		)
			.param("externalSubject", externalSubject)
			.query(::mapRow)
			.optional()
			.orElse(null)

	fun findById(id: UUID): AppUser? =
		jdbcClient.sql(
			"""
			select id, external_subject, email, display_name, status, created_at, updated_at
			from app_user
			where id = :id
			""".trimIndent(),
		)
			.param("id", id)
			.query(::mapRow)
			.optional()
			.orElse(null)

	/**
	 * Creates a new app_user. Callers are responsible for first checking
	 * [findByExternalSubject] within the same transaction/service method to keep
	 * provisioning idempotent (DESIGN-DOC.md section 27.2 — outbox/idempotency habits
	 * apply broadly, even outside the strict outbox mechanism).
	 */
	fun insert(externalSubject: String, email: String, displayName: String): AppUser {
		val now = Instant.now()
		val id = UUID.randomUUID()
		jdbcClient.sql(
			"""
			insert into app_user (id, external_subject, email, display_name, status, created_at, updated_at)
			values (:id, :externalSubject, :email, :displayName, 'ACTIVE', :now, :now)
			""".trimIndent(),
		)
			.param("id", id)
			.param("externalSubject", externalSubject)
			.param("email", email)
			.param("displayName", displayName)
			.param("now", now)
			.update()
		return AppUser(id, externalSubject, email, displayName, AppUserStatus.ACTIVE, now, now)
	}

	private fun mapRow(rs: java.sql.ResultSet, rowNum: Int): AppUser =
		AppUser(
			id = rs.getObject("id", UUID::class.java),
			externalSubject = rs.getString("external_subject"),
			email = rs.getString("email"),
			displayName = rs.getString("display_name"),
			status = AppUserStatus.valueOf(rs.getString("status")),
			createdAt = rs.getTimestamp("created_at").toInstant(),
			updatedAt = rs.getTimestamp("updated_at").toInstant(),
		)
}
