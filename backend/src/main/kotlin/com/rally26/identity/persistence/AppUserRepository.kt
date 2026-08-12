package com.rally26.identity.persistence

import com.rally26.identity.domain.AppUser
import com.rally26.identity.domain.AppUserStatus
import com.rally26.identity.domain.OAuthProvider
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

private const val APP_USER_COLUMNS = "id, email, display_name, status, password_hash, created_at, updated_at, provider, provider_subject"

@Repository
class AppUserRepository(
    private val jdbcClient: JdbcClient,
) {
    fun findByEmail(email: String): AppUser? =
        jdbcClient
            .sql(
                """
                select $APP_USER_COLUMNS
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
                select $APP_USER_COLUMNS
                from app_user
                where id = :id
                """.trimIndent(),
            ).param("id", id)
            .query(::mapRow)
            .optional()
            .orElse(null)

    /** Phase 37 — the fast path for a returning provider sign-in, keyed on the provider's own stable subject id rather than email (which a user can change on the provider's side). */
    fun findByProvider(
        provider: OAuthProvider,
        providerSubject: String,
    ): AppUser? =
        jdbcClient
            .sql(
                """
                select $APP_USER_COLUMNS
                from app_user
                where provider = :provider and provider_subject = :providerSubject
                """.trimIndent(),
            ).param("provider", provider.name)
            .param("providerSubject", providerSubject)
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

    /**
     * Phase 37 — a brand-new account created from a provider sign-in. Always ACTIVE and
     * password-less: the provider already verified this email, so there is no
     * PENDING_EMAIL_VERIFICATION step to go through the way password registration has.
     */
    fun insertOAuthUser(
        email: String,
        displayName: String,
        provider: OAuthProvider,
        providerSubject: String,
    ): AppUser {
        val now = Instant.now()
        val id = UUID.randomUUID()
        jdbcClient
            .sql(
                """
                insert into app_user (id, email, display_name, status, password_hash, created_at, updated_at, provider, provider_subject)
                values (:id, :email, :displayName, 'ACTIVE', null, :now, :now, :provider, :providerSubject)
                """.trimIndent(),
            ).param("id", id)
            .param("email", email)
            .param("displayName", displayName)
            .param("now", Timestamp.from(now))
            .param("provider", provider.name)
            .param("providerSubject", providerSubject)
            .update()
        return AppUser(id, email, displayName, AppUserStatus.ACTIVE, null, now, now, provider, providerSubject)
    }

    /** Phase 37 — adds a provider identity to an already-ACTIVE, already-verified account. Never changes password_hash/status; this only adds a second sign-in method to a real, established account. */
    fun linkProvider(
        id: UUID,
        provider: OAuthProvider,
        providerSubject: String,
    ): Int {
        val now = Instant.now()
        return jdbcClient
            .sql("update app_user set provider = :provider, provider_subject = :providerSubject, updated_at = :now where id = :id")
            .param("provider", provider.name)
            .param("providerSubject", providerSubject)
            .param("now", Timestamp.from(now))
            .param("id", id)
            .update()
    }

    /**
     * Phase 37 — a provider sign-in "claims" a still-unverified password registration
     * for the same email. The provider has just cryptographically proven the real
     * person owns this email; whoever set the original password has not. Clearing
     * password_hash here closes a real pre-account-hijacking risk (an attacker
     * registering a victim's email first, then either waiting for the victim to verify
     * it via a password reset they don't control, or hoping the victim signs in with a
     * password the attacker already knows) rather than silently linking the provider
     * identity onto a row whose password credential is not trusted.
     */
    fun claimViaProvider(
        id: UUID,
        provider: OAuthProvider,
        providerSubject: String,
    ): Int {
        val now = Instant.now()
        return jdbcClient
            .sql(
                """
                update app_user
                set provider = :provider, provider_subject = :providerSubject, password_hash = null, status = 'ACTIVE', updated_at = :now
                where id = :id
                """.trimIndent(),
            ).param("provider", provider.name)
            .param("providerSubject", providerSubject)
            .param("now", Timestamp.from(now))
            .param("id", id)
            .update()
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
            provider = rs.getString("provider")?.let { OAuthProvider.valueOf(it) },
            providerSubject = rs.getString("provider_subject"),
        )
}
