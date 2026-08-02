package com.leaguelift.integration.core.web

import com.leaguelift.integration.core.application.AuthorizationStartResult
import com.leaguelift.integration.core.application.IntegrationCatalogItem
import com.leaguelift.integration.core.domain.IntegrationConnection
import com.leaguelift.integration.core.domain.IntegrationConnectionStatus
import com.leaguelift.integration.core.domain.IntegrationHealthCheck
import java.time.Instant
import java.util.UUID

data class IntegrationCatalogResponse(
    val provider: String,
    val displayName: String,
    val category: String,
    val ownerType: String,
    val authMode: String,
    val supportedAuthModes: List<String>,
    val readiness: String,
    val adapterMode: String,
    val description: String,
    val activationRequirement: String,
    val defaultScopes: List<String>,
    val stub: Boolean,
    val connection: IntegrationConnectionResponse?,
)

data class IntegrationConnectionResponse(
    val id: UUID,
    val provider: String,
    val category: String,
    val ownerType: String,
    val organizationId: UUID?,
    val userId: UUID?,
    val authMode: String,
    val status: String,
    val grantedScopes: List<String>,
    val externalAccountId: String?,
    val externalAccountName: String?,
    val hasStoredCredential: Boolean,
    val accessTokenExpiresAt: Instant?,
    val lastSuccessfulSyncAt: Instant?,
    val lastHealthCheckAt: Instant?,
    val lastErrorCode: String?,
    val lastErrorMessage: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val connectedAt: Instant?,
    val revokedAt: Instant?,
    val disconnectedAt: Instant?,
)

data class AuthorizationStartResponse(
    val provider: String,
    val connectionId: UUID,
    val authorizationUrl: String,
    val expiresAt: Instant,
)

data class AuthorizationCallbackResponse(
    val connection: IntegrationConnectionResponse,
)

data class IntegrationHealthResponse(
    val id: UUID,
    val connectionId: UUID,
    val status: String,
    val latencyMs: Long?,
    val errorCode: String?,
    val errorMessage: String?,
    val checkedAt: Instant,
)

fun IntegrationCatalogItem.toResponse() = IntegrationCatalogResponse(
    provider = definition.provider.name,
    displayName = definition.displayName,
    category = definition.category.name,
    ownerType = definition.ownershipScope.name,
    authMode = definition.primaryAuthMode.name,
    supportedAuthModes = definition.supportedAuthModes.map { it.name },
    readiness = readiness.name,
    adapterMode = definition.adapterMode.name,
    description = definition.description,
    activationRequirement = definition.activationRequirement,
    defaultScopes = definition.defaultScopes,
    stub = stub,
    connection = connection?.toResponse(),
)

fun IntegrationConnection.toResponse() = IntegrationConnectionResponse(
    id = id,
    provider = provider.name,
    category = category.name,
    ownerType = ownerType.name,
    organizationId = organizationId,
    userId = userId,
    authMode = authMode.name,
    status = status.name,
    grantedScopes = grantedScopes,
    externalAccountId = externalAccountId,
    externalAccountName = externalAccountName,
    hasStoredCredential = credentialId != null &&
        status !in setOf(IntegrationConnectionStatus.REVOKED, IntegrationConnectionStatus.DISCONNECTED),
    accessTokenExpiresAt = accessTokenExpiresAt,
    lastSuccessfulSyncAt = lastSuccessfulSyncAt,
    lastHealthCheckAt = lastHealthCheckAt,
    lastErrorCode = lastErrorCode,
    lastErrorMessage = lastErrorMessage,
    createdAt = createdAt,
    updatedAt = updatedAt,
    connectedAt = connectedAt,
    revokedAt = revokedAt,
    disconnectedAt = disconnectedAt,
)

fun AuthorizationStartResult.toResponse() = AuthorizationStartResponse(
    provider = connection.provider.name,
    connectionId = connection.id,
    authorizationUrl = authorizationUrl,
    expiresAt = expiresAt,
)

fun IntegrationHealthCheck.toResponse() = IntegrationHealthResponse(
    id = id,
    connectionId = connectionId,
    status = status.name,
    latencyMs = latencyMs,
    errorCode = errorCode,
    errorMessage = errorMessage,
    checkedAt = checkedAt,
)
