package com.rally26.integration.core.persistence

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.rally26.integration.core.domain.IntegrationOAuthState
import com.rally26.integration.core.domain.IntegrationOwnerType
import com.rally26.integration.core.domain.IntegrationProvider
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
class IntegrationOAuthStateRepository(
    private val jdbcClient: JdbcClient,
    private val objectMapper: ObjectMapper,
) {
    fun insert(
        provider: IntegrationProvider,
        connectionId: UUID,
        ownerType: IntegrationOwnerType,
        organizationId: UUID?,
        userId: UUID?,
        stateHash: String,
        codeVerifierCiphertext: String,
        keyVersion: Int,
        aadContext: String,
        redirectUri: String,
        requestedScopes: List<String>,
        expiresAt: Instant,
        createdByUserId: UUID,
    ): IntegrationOAuthState {
        val id = UUID.randomUUID()
        val now = Instant.now()
        jdbcClient.sql(
            """
            insert into integration_oauth_state
                (id, provider, connection_id, owner_type, organization_id, user_id,
                 state_hash, code_verifier_ciphertext, key_version, aad_context,
                 redirect_uri, requested_scopes, expires_at, created_by_user_id, created_at)
            values
                (:id, :provider, :connectionId, :ownerType, :organizationId, :userId,
                 :stateHash, :codeVerifierCiphertext, :keyVersion, :aadContext,
                 :redirectUri, cast(:requestedScopes as jsonb), :expiresAt, :createdByUserId, :createdAt)
            """.trimIndent(),
        )
            .param("id", id)
            .param("provider", provider.name)
            .param("connectionId", connectionId)
            .param("ownerType", ownerType.name)
            .param("organizationId", organizationId)
            .param("userId", userId)
            .param("stateHash", stateHash)
            .param("codeVerifierCiphertext", codeVerifierCiphertext)
            .param("keyVersion", keyVersion)
            .param("aadContext", aadContext)
            .param("redirectUri", redirectUri)
            .param("requestedScopes", objectMapper.writeValueAsString(requestedScopes))
            .param("expiresAt", java.sql.Timestamp.from(expiresAt))
            .param("createdByUserId", createdByUserId)
            .param("createdAt", java.sql.Timestamp.from(now))
            .update()
        return IntegrationOAuthState(
            id, provider, connectionId, ownerType, organizationId, userId, stateHash,
            codeVerifierCiphertext, keyVersion, aadContext, redirectUri, requestedScopes,
            expiresAt, null, createdByUserId, now,
        )
    }

    /** Atomic single-use consume; an expired, replayed, or unknown state returns null. */
    fun consume(stateHash: String): IntegrationOAuthState? =
        jdbcClient.sql(
            """
            update integration_oauth_state
            set consumed_at = now()
            where state_hash = :stateHash
              and consumed_at is null
              and expires_at > now()
            returning $COLUMNS
            """.trimIndent(),
        )
            .param("stateHash", stateHash)
            .query(::mapRow)
            .optional()
            .orElse(null)

    private fun mapRow(rs: java.sql.ResultSet, rowNum: Int) = IntegrationOAuthState(
        id = rs.getObject("id", UUID::class.java),
        provider = IntegrationProvider.valueOf(rs.getString("provider")),
        connectionId = rs.getObject("connection_id", UUID::class.java),
        ownerType = IntegrationOwnerType.valueOf(rs.getString("owner_type")),
        organizationId = rs.getObject("organization_id", UUID::class.java),
        userId = rs.getObject("user_id", UUID::class.java),
        stateHash = rs.getString("state_hash"),
        codeVerifierCiphertext = rs.getString("code_verifier_ciphertext"),
        keyVersion = rs.getInt("key_version"),
        aadContext = rs.getString("aad_context"),
        redirectUri = rs.getString("redirect_uri"),
        requestedScopes = objectMapper.readValue(rs.getString("requested_scopes"), object : TypeReference<List<String>>() {}),
        expiresAt = rs.getTimestamp("expires_at").toInstant(),
        consumedAt = rs.getTimestamp("consumed_at")?.toInstant(),
        createdByUserId = rs.getObject("created_by_user_id", UUID::class.java),
        createdAt = rs.getTimestamp("created_at").toInstant(),
    )

    private companion object {
        const val COLUMNS = "id, provider, connection_id, owner_type, organization_id, user_id, state_hash, code_verifier_ciphertext, key_version, aad_context, redirect_uri, requested_scopes, expires_at, consumed_at, created_by_user_id, created_at"
    }
}
