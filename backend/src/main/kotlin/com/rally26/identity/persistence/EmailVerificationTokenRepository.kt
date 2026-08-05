package com.rally26.identity.persistence

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

data class EmailVerificationTokenRecord(
    val id: UUID,
    val userId: UUID,
    val tokenHash: String,
    val expiresAt: Instant,
    val consumedAt: Instant?,
)

@Repository
class EmailVerificationTokenRepository(
    private val jdbcClient: JdbcClient,
) {
    fun findByTokenHash(tokenHash: String): EmailVerificationTokenRecord? =
        jdbcClient
            .sql(
                """
                select id, user_id, token_hash, expires_at, consumed_at
                from email_verification_token
                where token_hash = :tokenHash
                """.trimIndent(),
            ).param("tokenHash", tokenHash)
            .query(::mapRow)
            .optional()
            .orElse(null)

    fun replaceActiveToken(
        userId: UUID,
        tokenHash: String,
        expiresAt: Instant,
    ): EmailVerificationTokenRecord {
        val now = Instant.now()
        jdbcClient
            .sql("delete from email_verification_token where user_id = :userId and consumed_at is null")
            .param("userId", userId)
            .update()
        val id = UUID.randomUUID()
        jdbcClient
            .sql(
                """
                insert into email_verification_token (id, user_id, token_hash, expires_at, created_at)
                values (:id, :userId, :tokenHash, :expiresAt, :createdAt)
                """.trimIndent(),
            ).param("id", id)
            .param("userId", userId)
            .param("tokenHash", tokenHash)
            .param("expiresAt", Timestamp.from(expiresAt))
            .param("createdAt", Timestamp.from(now))
            .update()
        return EmailVerificationTokenRecord(id, userId, tokenHash, expiresAt, consumedAt = null)
    }

    fun consume(
        id: UUID,
        consumedAt: Instant = Instant.now(),
    ): Int =
        jdbcClient
            .sql(
                """
                update email_verification_token
                set consumed_at = :consumedAt
                where id = :id and consumed_at is null
                """.trimIndent(),
            ).param("consumedAt", Timestamp.from(consumedAt))
            .param("id", id)
            .update()

    private fun mapRow(
        rs: java.sql.ResultSet,
        row: Int,
    ): EmailVerificationTokenRecord =
        EmailVerificationTokenRecord(
            id = rs.getObject("id", UUID::class.java),
            userId = rs.getObject("user_id", UUID::class.java),
            tokenHash = rs.getString("token_hash"),
            expiresAt = rs.getTimestamp("expires_at").toInstant(),
            consumedAt = rs.getTimestamp("consumed_at")?.toInstant(),
        )
}
