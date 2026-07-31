package com.leaguelift.identity.persistence

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

data class PasswordResetTokenRecord(
	val id: UUID,
	val userId: UUID,
	val tokenHash: String,
	val expiresAt: Instant,
	val consumedAt: Instant?,
)

@Repository
class PasswordResetTokenRepository(private val jdbcClient: JdbcClient) {

	fun findByTokenHash(tokenHash: String): PasswordResetTokenRecord? =
		jdbcClient.sql(
			"""
			select id, user_id, token_hash, expires_at, consumed_at
			from password_reset_token
			where token_hash = :tokenHash
			""".trimIndent(),
		)
			.param("tokenHash", tokenHash)
			.query(::mapRow)
			.optional()
			.orElse(null)

	fun replaceActiveToken(userId: UUID, tokenHash: String, expiresAt: Instant): PasswordResetTokenRecord {
		val now = Instant.now()
		jdbcClient.sql("delete from password_reset_token where user_id = :userId and consumed_at is null")
			.param("userId", userId)
			.update()
		val id = UUID.randomUUID()
		jdbcClient.sql(
			"""
			insert into password_reset_token (id, user_id, token_hash, expires_at, created_at)
			values (:id, :userId, :tokenHash, :expiresAt, :createdAt)
			""".trimIndent(),
		)
			.param("id", id)
			.param("userId", userId)
			.param("tokenHash", tokenHash)
			.param("expiresAt", Timestamp.from(expiresAt))
			.param("createdAt", Timestamp.from(now))
			.update()
		return PasswordResetTokenRecord(id, userId, tokenHash, expiresAt, consumedAt = null)
	}

	fun consume(id: UUID, consumedAt: Instant = Instant.now()): Int =
		jdbcClient.sql(
			"""
			update password_reset_token
			set consumed_at = :consumedAt
			where id = :id and consumed_at is null
			""".trimIndent(),
		)
			.param("consumedAt", Timestamp.from(consumedAt))
			.param("id", id)
			.update()

	private fun mapRow(rs: java.sql.ResultSet, row: Int): PasswordResetTokenRecord =
		PasswordResetTokenRecord(
			id = rs.getObject("id", UUID::class.java),
			userId = rs.getObject("user_id", UUID::class.java),
			tokenHash = rs.getString("token_hash"),
			expiresAt = rs.getTimestamp("expires_at").toInstant(),
			consumedAt = rs.getTimestamp("consumed_at")?.toInstant(),
		)
}

