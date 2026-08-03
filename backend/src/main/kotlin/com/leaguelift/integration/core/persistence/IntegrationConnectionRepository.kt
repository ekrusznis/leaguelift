package com.leaguelift.integration.core.persistence

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.leaguelift.integration.core.domain.IntegrationAuthMode
import com.leaguelift.integration.core.domain.IntegrationCategory
import com.leaguelift.integration.core.domain.IntegrationConnection
import com.leaguelift.integration.core.domain.IntegrationConnectionStatus
import com.leaguelift.integration.core.domain.IntegrationHealthCheck
import com.leaguelift.integration.core.domain.IntegrationHealthStatus
import com.leaguelift.integration.core.domain.IntegrationOwnerType
import com.leaguelift.integration.core.domain.IntegrationProvider
import com.leaguelift.integration.core.domain.IntegrationProviderDefinition
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
class IntegrationConnectionRepository(
    private val jdbcClient: JdbcClient,
    private val objectMapper: ObjectMapper,
) {
    fun listForOrganization(organizationId: UUID): List<IntegrationConnection> =
        jdbcClient.sql(
            "select $COLUMNS from integration_connection where organization_id = :organizationId order by updated_at desc",
        )
            .param("organizationId", organizationId)
            .query(::mapRow)
            .list()

    fun listForUser(userId: UUID): List<IntegrationConnection> =
        jdbcClient.sql("select $COLUMNS from integration_connection where user_id = :userId order by updated_at desc")
            .param("userId", userId)
            .query(::mapRow)
            .list()

    fun findById(id: UUID): IntegrationConnection? =
        jdbcClient.sql("select $COLUMNS from integration_connection where id = :id")
            .param("id", id)
            .query(::mapRow)
            .optional()
            .orElse(null)

    fun findByIdForOrganization(id: UUID, organizationId: UUID): IntegrationConnection? =
        jdbcClient.sql("select $COLUMNS from integration_connection where id = :id and organization_id = :organizationId")
            .param("id", id)
            .param("organizationId", organizationId)
            .query(::mapRow)
            .optional()
            .orElse(null)

    fun findByIdForUser(id: UUID, userId: UUID): IntegrationConnection? =
        jdbcClient.sql("select $COLUMNS from integration_connection where id = :id and user_id = :userId")
            .param("id", id)
            .param("userId", userId)
            .query(::mapRow)
            .optional()
            .orElse(null)

    fun findActiveForOrganization(organizationId: UUID, provider: IntegrationProvider): IntegrationConnection? =
        jdbcClient.sql(
            """
            select $COLUMNS from integration_connection
            where organization_id = :organizationId and provider = :provider
              and status in ('AUTHORIZATION_PENDING', 'CONNECTED', 'DEGRADED')
            order by updated_at desc limit 1
            """.trimIndent(),
        )
            .param("organizationId", organizationId)
            .param("provider", provider.name)
            .query(::mapRow)
            .optional()
            .orElse(null)

    fun findActiveForUser(userId: UUID, provider: IntegrationProvider): IntegrationConnection? =
        jdbcClient.sql(
            """
            select $COLUMNS from integration_connection
            where user_id = :userId and provider = :provider
              and status in ('AUTHORIZATION_PENDING', 'CONNECTED', 'DEGRADED')
            order by updated_at desc limit 1
            """.trimIndent(),
        )
            .param("userId", userId)
            .param("provider", provider.name)
            .query(::mapRow)
            .optional()
            .orElse(null)

    fun insertAuthorizationPending(
        definition: IntegrationProviderDefinition,
        ownerType: IntegrationOwnerType,
        organizationId: UUID?,
        userId: UUID?,
        createdByUserId: UUID,
    ): IntegrationConnection {
        val id = UUID.randomUUID()
        val now = Instant.now()
        jdbcClient.sql(
            """
            insert into integration_connection
                (id, provider, category, owner_type, organization_id, user_id, auth_mode,
                 status, created_by_user_id, created_at, updated_at)
            values
                (:id, :provider, :category, :ownerType, :organizationId, :userId, :authMode,
                 'AUTHORIZATION_PENDING', :createdByUserId, :createdAt, :createdAt)
            """.trimIndent(),
        )
            .param("id", id)
            .param("provider", definition.provider.name)
            .param("category", definition.category.name)
            .param("ownerType", ownerType.name)
            .param("organizationId", organizationId)
            .param("userId", userId)
            .param("authMode", definition.primaryAuthMode.name)
            .param("createdByUserId", createdByUserId)
            .param("createdAt", java.sql.Timestamp.from(now))
            .update()
        return IntegrationConnection(
            id = id,
            provider = definition.provider,
            category = definition.category,
            ownerType = ownerType,
            organizationId = organizationId,
            userId = userId,
            authMode = definition.primaryAuthMode,
            status = IntegrationConnectionStatus.AUTHORIZATION_PENDING,
            grantedScopes = emptyList(),
            externalAccountId = null,
            externalAccountName = null,
            credentialId = null,
            accessTokenExpiresAt = null,
            refreshLockedAt = null,
            refreshLockedByUserId = null,
            lastSuccessfulSyncAt = null,
            lastHealthCheckAt = null,
            lastErrorCode = null,
            lastErrorMessage = null,
            legacyResourceType = null,
            legacyResourceId = null,
            createdByUserId = createdByUserId,
            createdAt = now,
            updatedAt = now,
            connectedAt = null,
            revokedAt = null,
            disconnectedAt = null,
        )
    }

    fun markAuthorizationPending(id: UUID): IntegrationConnection? {
        jdbcClient.sql(
            """
            update integration_connection
            set status = 'AUTHORIZATION_PENDING', last_error_code = null, last_error_message = null,
                revoked_at = null, disconnected_at = null, updated_at = now()
            where id = :id
            """.trimIndent(),
        ).param("id", id).update()
        return findById(id)
    }

    fun markConnected(
        id: UUID,
        credentialId: UUID,
        grantedScopes: List<String>,
        externalAccountId: String?,
        externalAccountName: String?,
        accessTokenExpiresAt: Instant?,
    ): IntegrationConnection? {
        jdbcClient.sql(
            """
            update integration_connection
            set status = 'CONNECTED', credential_id = :credentialId,
                granted_scopes = cast(:grantedScopes as jsonb),
                external_account_id = coalesce(:externalAccountId, external_account_id),
                external_account_name = coalesce(:externalAccountName, external_account_name),
                access_token_expires_at = :accessTokenExpiresAt,
                refresh_locked_at = null, refresh_locked_by_user_id = null,
                last_error_code = null, last_error_message = null,
                connected_at = coalesce(connected_at, now()), revoked_at = null,
                disconnected_at = null, updated_at = now()
            where id = :id
            """.trimIndent(),
        )
            .param("credentialId", credentialId)
            .param("grantedScopes", objectMapper.writeValueAsString(grantedScopes))
            .param("externalAccountId", externalAccountId)
            .param("externalAccountName", externalAccountName)
            .param("accessTokenExpiresAt", accessTokenExpiresAt?.let(java.sql.Timestamp::from))
            .param("id", id)
            .update()
        return findById(id)
    }

    fun markAuthorizationFailed(id: UUID, errorCode: String, errorMessage: String): IntegrationConnection? {
        jdbcClient.sql(
            """
            update integration_connection
            set status = 'DISCONNECTED', disconnected_at = now(),
                last_error_code = :errorCode, last_error_message = :errorMessage,
                refresh_locked_at = null, refresh_locked_by_user_id = null, updated_at = now()
            where id = :id
            """.trimIndent(),
        )
            .param("errorCode", errorCode.take(120))
            .param("errorMessage", errorMessage.take(500))
            .param("id", id)
            .update()
        return findById(id)
    }

    fun markDegraded(id: UUID, errorCode: String, errorMessage: String): IntegrationConnection? {
        jdbcClient.sql(
            """
            update integration_connection
            set status = 'DEGRADED', last_error_code = :errorCode,
                last_error_message = :errorMessage, refresh_locked_at = null,
                refresh_locked_by_user_id = null, updated_at = now()
            where id = :id
            """.trimIndent(),
        )
            .param("errorCode", errorCode.take(120))
            .param("errorMessage", errorMessage.take(500))
            .param("id", id)
            .update()
        return findById(id)
    }

    fun markDisconnected(id: UUID): IntegrationConnection? {
        jdbcClient.sql(
            """
            update integration_connection
            set status = 'DISCONNECTED', disconnected_at = now(),
                refresh_locked_at = null, refresh_locked_by_user_id = null, updated_at = now()
            where id = :id
            """.trimIndent(),
        ).param("id", id).update()
        return findById(id)
    }

    fun markRevoked(id: UUID): IntegrationConnection? {
        jdbcClient.sql(
            """
            update integration_connection
            set status = 'REVOKED', revoked_at = now(),
                refresh_locked_at = null, refresh_locked_by_user_id = null, updated_at = now()
            where id = :id
            """.trimIndent(),
        ).param("id", id).update()
        return findById(id)
    }

    fun acquireRefreshLock(id: UUID, actorUserId: UUID): IntegrationConnection? =
        jdbcClient.sql(
            """
            update integration_connection
            set refresh_locked_at = now(), refresh_locked_by_user_id = :actorUserId, updated_at = now()
            where id = :id
              and status in ('CONNECTED', 'DEGRADED')
              and (refresh_locked_at is null or refresh_locked_at < now() - interval '5 minutes')
            returning $COLUMNS
            """.trimIndent(),
        )
            .param("actorUserId", actorUserId)
            .param("id", id)
            .query(::mapRow)
            .optional()
            .orElse(null)

    fun releaseRefreshLock(id: UUID): Int =
        jdbcClient.sql(
            "update integration_connection set refresh_locked_at = null, refresh_locked_by_user_id = null, updated_at = now() where id = :id",
        ).param("id", id).update()

    fun insertEvent(
        connection: IntegrationConnection,
        eventType: String,
        statusFrom: IntegrationConnectionStatus?,
        statusTo: IntegrationConnectionStatus?,
        actorUserId: UUID?,
        metadata: Map<String, Any?> = emptyMap(),
    ) {
        jdbcClient.sql(
            """
            insert into integration_connection_event
                (id, connection_id, organization_id, user_id, event_type, status_from,
                 status_to, actor_user_id, metadata, created_at)
            values
                (:id, :connectionId, :organizationId, :userId, :eventType, :statusFrom,
                 :statusTo, :actorUserId, cast(:metadata as jsonb), now())
            """.trimIndent(),
        )
            .param("id", UUID.randomUUID())
            .param("connectionId", connection.id)
            .param("organizationId", connection.organizationId)
            .param("userId", connection.userId)
            .param("eventType", eventType)
            .param("statusFrom", statusFrom?.name)
            .param("statusTo", statusTo?.name)
            .param("actorUserId", actorUserId)
            .param("metadata", objectMapper.writeValueAsString(metadata))
            .update()
    }

    fun insertHealthCheck(
        connectionId: UUID,
        status: IntegrationHealthStatus,
        latencyMs: Long?,
        errorCode: String?,
        errorMessage: String?,
        checkedByUserId: UUID?,
    ): IntegrationHealthCheck {
        val id = UUID.randomUUID()
        val now = Instant.now()
        jdbcClient.sql(
            """
            insert into integration_connection_health_check
                (id, connection_id, status, latency_ms, error_code, error_message, checked_by_user_id, checked_at)
            values
                (:id, :connectionId, :status, :latencyMs, :errorCode, :errorMessage, :checkedByUserId, :checkedAt)
            """.trimIndent(),
        )
            .param("id", id)
            .param("connectionId", connectionId)
            .param("status", status.name)
            .param("latencyMs", latencyMs)
            .param("errorCode", errorCode?.take(120))
            .param("errorMessage", errorMessage?.take(500))
            .param("checkedByUserId", checkedByUserId)
            .param("checkedAt", java.sql.Timestamp.from(now))
            .update()
        jdbcClient.sql(
            """
            update integration_connection
            set last_health_check_at = :checkedAt,
                status = case when :status = 'HEALTHY' then 'CONNECTED' else 'DEGRADED' end,
                last_error_code = :errorCode, last_error_message = :errorMessage,
                updated_at = :checkedAt
            where id = :connectionId
            """.trimIndent(),
        )
            .param("checkedAt", java.sql.Timestamp.from(now))
            .param("status", status.name)
            .param("errorCode", errorCode?.take(120))
            .param("errorMessage", errorMessage?.take(500))
            .param("connectionId", connectionId)
            .update()
        return IntegrationHealthCheck(id, connectionId, status, latencyMs, errorCode, errorMessage, checkedByUserId, now)
    }

    private fun mapRow(rs: java.sql.ResultSet, rowNum: Int) = IntegrationConnection(
        id = rs.getObject("id", UUID::class.java),
        provider = IntegrationProvider.valueOf(rs.getString("provider")),
        category = IntegrationCategory.valueOf(rs.getString("category")),
        ownerType = IntegrationOwnerType.valueOf(rs.getString("owner_type")),
        organizationId = rs.getObject("organization_id", UUID::class.java),
        userId = rs.getObject("user_id", UUID::class.java),
        authMode = IntegrationAuthMode.valueOf(rs.getString("auth_mode")),
        status = IntegrationConnectionStatus.valueOf(rs.getString("status")),
        grantedScopes = objectMapper.readValue(rs.getString("granted_scopes"), object : TypeReference<List<String>>() {}),
        externalAccountId = rs.getString("external_account_id"),
        externalAccountName = rs.getString("external_account_name"),
        credentialId = rs.getObject("credential_id", UUID::class.java),
        accessTokenExpiresAt = rs.getTimestamp("access_token_expires_at")?.toInstant(),
        refreshLockedAt = rs.getTimestamp("refresh_locked_at")?.toInstant(),
        refreshLockedByUserId = rs.getObject("refresh_locked_by_user_id", UUID::class.java),
        lastSuccessfulSyncAt = rs.getTimestamp("last_successful_sync_at")?.toInstant(),
        lastHealthCheckAt = rs.getTimestamp("last_health_check_at")?.toInstant(),
        lastErrorCode = rs.getString("last_error_code"),
        lastErrorMessage = rs.getString("last_error_message"),
        legacyResourceType = rs.getString("legacy_resource_type"),
        legacyResourceId = rs.getObject("legacy_resource_id", UUID::class.java),
        createdByUserId = rs.getObject("created_by_user_id", UUID::class.java),
        createdAt = rs.getTimestamp("created_at").toInstant(),
        updatedAt = rs.getTimestamp("updated_at").toInstant(),
        connectedAt = rs.getTimestamp("connected_at")?.toInstant(),
        revokedAt = rs.getTimestamp("revoked_at")?.toInstant(),
        disconnectedAt = rs.getTimestamp("disconnected_at")?.toInstant(),
    )

    private companion object {
        const val COLUMNS = "id, provider, category, owner_type, organization_id, user_id, auth_mode, status, granted_scopes, external_account_id, external_account_name, credential_id, access_token_expires_at, refresh_locked_at, refresh_locked_by_user_id, last_successful_sync_at, last_health_check_at, last_error_code, last_error_message, legacy_resource_type, legacy_resource_id, created_by_user_id, created_at, updated_at, connected_at, revoked_at, disconnected_at"
    }
}
