package com.leaguelift.integration.core.persistence

import com.leaguelift.integration.core.domain.IntegrationCredentialKind
import com.leaguelift.integration.core.domain.IntegrationCredentialSecret
import com.leaguelift.integration.core.domain.IntegrationOwnerType
import com.leaguelift.integration.core.domain.IntegrationProvider
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
class IntegrationCredentialRepository(private val jdbcClient: JdbcClient) {
    fun insert(
        provider: IntegrationProvider,
        ownerType: IntegrationOwnerType,
        organizationId: UUID?,
        userId: UUID?,
        credentialKind: IntegrationCredentialKind,
        ciphertext: String,
        keyVersion: Int,
        aadContext: String,
        rotatedFromId: UUID?,
        createdByUserId: UUID?,
    ): IntegrationCredentialSecret {
        val id = UUID.randomUUID()
        val now = Instant.now()
        jdbcClient.sql(
            """
            insert into integration_credential_secret
                (id, provider, owner_type, organization_id, user_id, credential_kind,
                 ciphertext, key_version, aad_context, rotated_from_id, created_by_user_id, created_at)
            values
                (:id, :provider, :ownerType, :organizationId, :userId, :credentialKind,
                 :ciphertext, :keyVersion, :aadContext, :rotatedFromId, :createdByUserId, :createdAt)
            """.trimIndent(),
        )
            .param("id", id)
            .param("provider", provider.name)
            .param("ownerType", ownerType.name)
            .param("organizationId", organizationId)
            .param("userId", userId)
            .param("credentialKind", credentialKind.name)
            .param("ciphertext", ciphertext)
            .param("keyVersion", keyVersion)
            .param("aadContext", aadContext)
            .param("rotatedFromId", rotatedFromId)
            .param("createdByUserId", createdByUserId)
            .param("createdAt", java.sql.Timestamp.from(now))
            .update()
        return IntegrationCredentialSecret(
            id, provider, ownerType, organizationId, userId, credentialKind, ciphertext,
            keyVersion, aadContext, rotatedFromId, createdByUserId, now, null,
        )
    }

    fun findById(id: UUID): IntegrationCredentialSecret? =
        jdbcClient.sql("select $COLUMNS from integration_credential_secret where id = :id")
            .param("id", id)
            .query(::mapRow)
            .optional()
            .orElse(null)

    fun revoke(id: UUID): Int =
        jdbcClient.sql("update integration_credential_secret set revoked_at = coalesce(revoked_at, now()) where id = :id")
            .param("id", id)
            .update()

    private fun mapRow(rs: java.sql.ResultSet, rowNum: Int) = IntegrationCredentialSecret(
        id = rs.getObject("id", UUID::class.java),
        provider = IntegrationProvider.valueOf(rs.getString("provider")),
        ownerType = IntegrationOwnerType.valueOf(rs.getString("owner_type")),
        organizationId = rs.getObject("organization_id", UUID::class.java),
        userId = rs.getObject("user_id", UUID::class.java),
        credentialKind = IntegrationCredentialKind.valueOf(rs.getString("credential_kind")),
        ciphertext = rs.getString("ciphertext"),
        keyVersion = rs.getInt("key_version"),
        aadContext = rs.getString("aad_context"),
        rotatedFromId = rs.getObject("rotated_from_id", UUID::class.java),
        createdByUserId = rs.getObject("created_by_user_id", UUID::class.java),
        createdAt = rs.getTimestamp("created_at").toInstant(),
        revokedAt = rs.getTimestamp("revoked_at")?.toInstant(),
    )

    private companion object {
        const val COLUMNS = "id, provider, owner_type, organization_id, user_id, credential_kind, ciphertext, key_version, aad_context, rotated_from_id, created_by_user_id, created_at, revoked_at"
    }
}
