package com.rally26.integration.core.domain

import java.time.Instant
import java.util.UUID

enum class IntegrationProvider(val configKey: String) {
    GOOGLE_CALENDAR("google-calendar"),
    QUICKBOOKS_ONLINE("quickbooks-online"),
    SPORTSENGINE("sportsengine"),
    GAMECHANGER("gamechanger"),
    MAXPREPS("maxpreps"),
    ICS_FEED("ics-feed"),
    CSV_IMPORT("csv-import"),
    STRIPE("stripe"),
    PRINTIFY("printify"),
    RESEND("resend"),
    TWILIO("twilio"),
    DIGITALOCEAN_SPACES("digitalocean-spaces"),
    GOOGLE_MAPS("google-maps"),
}

enum class IntegrationCategory { PAYMENTS, FULFILLMENT, COMMUNICATIONS, STORAGE, CALENDAR, ACCOUNTING, SPORTS_DATA, MAPS }
enum class IntegrationOwnerType { PLATFORM, ORGANIZATION, USER }
enum class IntegrationAuthMode { NONE, PLATFORM_SECRET, OAUTH2, API_TOKEN, FILE_IMPORT, URL, WEBHOOK }
enum class IntegrationReadiness { AVAILABLE, NOT_CONFIGURED, PARTNER_PENDING, PLATFORM_MANAGED, UNSUPPORTED }
enum class IntegrationAdapterMode { EXISTING, OAUTH_SCAFFOLD, FILE_IMPORT, PARTNER_PENDING, PLATFORM_MANAGED }
enum class IntegrationConnectionStatus { NOT_CONFIGURED, AVAILABLE, AUTHORIZATION_PENDING, CONNECTED, DEGRADED, REVOKED, DISCONNECTED, UNSUPPORTED }
enum class IntegrationHealthStatus { HEALTHY, DEGRADED, FAILED }
enum class IntegrationCredentialKind { OAUTH_TOKEN_SET, API_TOKEN, OAUTH_PKCE_VERIFIER }

data class IntegrationProviderDefinition(
    val provider: IntegrationProvider,
    val displayName: String,
    val category: IntegrationCategory,
    val ownershipScope: IntegrationOwnerType,
    val primaryAuthMode: IntegrationAuthMode,
    val supportedAuthModes: List<IntegrationAuthMode>,
    val baselineReadiness: IntegrationReadiness,
    val adapterMode: IntegrationAdapterMode,
    val description: String,
    val activationRequirement: String,
    val defaultScopes: List<String>,
    val sortOrder: Int,
    val visibleToCustomers: Boolean,
)

data class IntegrationConnection(
    val id: UUID,
    val provider: IntegrationProvider,
    val category: IntegrationCategory,
    val ownerType: IntegrationOwnerType,
    val organizationId: UUID?,
    val userId: UUID?,
    val authMode: IntegrationAuthMode,
    val status: IntegrationConnectionStatus,
    val grantedScopes: List<String>,
    val externalAccountId: String?,
    val externalAccountName: String?,
    val credentialId: UUID?,
    val accessTokenExpiresAt: Instant?,
    val refreshLockedAt: Instant?,
    val refreshLockedByUserId: UUID?,
    val lastSuccessfulSyncAt: Instant?,
    val lastHealthCheckAt: Instant?,
    val lastErrorCode: String?,
    val lastErrorMessage: String?,
    val legacyResourceType: String?,
    val legacyResourceId: UUID?,
    val createdByUserId: UUID?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val connectedAt: Instant?,
    val revokedAt: Instant?,
    val disconnectedAt: Instant?,
)

data class IntegrationCredentialSecret(
    val id: UUID,
    val provider: IntegrationProvider,
    val ownerType: IntegrationOwnerType,
    val organizationId: UUID?,
    val userId: UUID?,
    val credentialKind: IntegrationCredentialKind,
    val ciphertext: String,
    val keyVersion: Int,
    val aadContext: String,
    val rotatedFromId: UUID?,
    val createdByUserId: UUID?,
    val createdAt: Instant,
    val revokedAt: Instant?,
)

data class IntegrationOAuthState(
    val id: UUID,
    val provider: IntegrationProvider,
    val connectionId: UUID,
    val ownerType: IntegrationOwnerType,
    val organizationId: UUID?,
    val userId: UUID?,
    val stateHash: String,
    val codeVerifierCiphertext: String,
    val keyVersion: Int,
    val aadContext: String,
    val redirectUri: String,
    val requestedScopes: List<String>,
    val expiresAt: Instant,
    val consumedAt: Instant?,
    val createdByUserId: UUID,
    val createdAt: Instant,
)

data class IntegrationHealthCheck(
    val id: UUID,
    val connectionId: UUID,
    val status: IntegrationHealthStatus,
    val latencyMs: Long?,
    val errorCode: String?,
    val errorMessage: String?,
    val checkedByUserId: UUID?,
    val checkedAt: Instant,
)
