package com.rally26.identity.persistence

import com.rally26.identity.domain.AppUser
import com.rally26.identity.domain.AppUserStatus
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Repository
class AppUserRepository(
    private val jdbcClient: JdbcClient,
) {
    fun findByEmail(email: String): AppUser? =
        jdbcClient
            .sql(
                """
                select id, email, display_name, status, password_hash, created_at, updated_at
                from app_user
                where email = :email
                """.trimIndent(),
            ).param("email", email)
            .query(::mapRow)
            .optional()
            .orElse(null)

    fun findById(id: UUID): AppUser? =
        jdbcClient
            .sql(
                """
                select id, email, display_name, status, password_hash, created_at, updated_at
                from app_user
                where id = :id
                """.trimIndent(),
            ).param("id", id)
            .query(::mapRow)
            .optional()
            .orElse(null)

    /** Platform-admin-only aggregate (DESIGN-DOC.md section 10.2's Platform Admin "Users" nav item). */
    fun countAll(): Long = jdbcClient.sql("select count(*) from app_user").query(Long::class.java).single()

    /**
     * Creates a new app_user with an already-hashed password. Throws
     * [org.springframework.dao.DuplicateKeyException] if `email` is already taken —
     * callers translate that to a client-facing 409 (see `AuthController`).
     */
    fun insert(
        email: String,
        displayName: String,
        passwordHash: String?,
        status: AppUserStatus = AppUserStatus.ACTIVE,
    ): AppUser {
        val now = Instant.now()
        val id = UUID.randomUUID()
        jdbcClient
            .sql(
                """
                insert into app_user (id, email, display_name, status, password_hash, created_at, updated_at)
                values (:id, :email, :displayName, :status, :passwordHash, :now, :now)
                """.trimIndent(),
            ).param("id", id)
            .param("email", email)
            .param("displayName", displayName)
            .param("status", status.name)
            .param("passwordHash", passwordHash)
            .param("now", Timestamp.from(now))
            .update()
        return AppUser(id, email, displayName, status, passwordHash, now, now)
    }

    fun markActive(id: UUID): Int {
        val now = Instant.now()
        return jdbcClient
            .sql("update app_user set status = 'ACTIVE', updated_at = :now where id = :id")
            .param("now", Timestamp.from(now))
            .param("id", id)
            .update()
    }

    fun updatePasswordHash(
        id: UUID,
        passwordHash: String,
    ): Int {
        val now = Instant.now()
        return jdbcClient
            .sql(
                "update app_user set password_hash = :passwordHash, updated_at = :now where id = :id",
            ).param("passwordHash", passwordHash)
            .param("now", Timestamp.from(now))
            .param("id", id)
            .update()
    }

    private fun mapRow(
        rs: java.sql.ResultSet,
        rowNum: Int,
    ): AppUser =
        AppUser(
            id = rs.getObject("id", UUID::class.java),
            email = rs.getString("email"),
            displayName = rs.getString("display_name"),
            status = AppUserStatus.valueOf(rs.getString("status")),
            passwordHash = rs.getString("password_hash"),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant(),
        )
}
