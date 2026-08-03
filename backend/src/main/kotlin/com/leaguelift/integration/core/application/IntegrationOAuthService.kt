package com.leaguelift.integration.core.application

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.leaguelift.audit.application.AuditService
import com.leaguelift.common.error.ConflictException
import com.leaguelift.common.error.NotFoundException
import com.leaguelift.common.error.ServiceUnavailableException
import com.leaguelift.common.error.ValidationException
import com.leaguelift.common.web.CurrentUser
import com.leaguelift.config.IntegrationProperties
import com.leaguelift.integration.core.domain.IntegrationAuthMode
import com.leaguelift.integration.core.domain.IntegrationConnection
import com.leaguelift.integration.core.domain.IntegrationConnectionStatus
import com.leaguelift.integration.core.domain.IntegrationCredentialKind
import com.leaguelift.integration.core.domain.IntegrationHealthCheck
import com.leaguelift.integration.core.domain.IntegrationHealthStatus
import com.leaguelift.integration.core.domain.IntegrationOwnerType
import com.leaguelift.integration.core.domain.IntegrationProvider
import com.leaguelift.integration.core.domain.IntegrationReadiness
import com.leaguelift.integration.core.persistence.IntegrationConnectionRepository
import com.leaguelift.integration.core.persistence.IntegrationCredentialRepository
import com.leaguelift.integration.core.persistence.IntegrationOAuthStateRepository
import com.leaguelift.membership.application.MembershipService
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.UUID

private data class StoredOAuthTokenSet(
    val accessToken: String,
    val refreshToken: String?,
    val tokenType: String,
    val grantedScopes: List<String>,
)

data class AuthorizationStartResult(
    val connection: IntegrationConnection,
    val authorizationUrl: String,
    val expiresAt: Instant,
)

data class IntegrationAccessToken(
    val connection: IntegrationConnection,
    val accessToken: String,
)

@Service
class IntegrationOAuthService(
    private val catalogService: IntegrationCatalogService,
    private val connectionRepository: IntegrationConnectionRepository,
    private val credentialRepository: IntegrationCredentialRepository,
    private val oauthStateRepository: IntegrationOAuthStateRepository,
    private val adapterRegistry: IntegrationAdapterRegistry,
    private val membershipService: MembershipService,
    private val credentialCipher: CredentialCipher,
    private val properties: IntegrationProperties,
    private val objectMapper: ObjectMapper,
    private val auditService: AuditService,
) {
    private val secureRandom = SecureRandom()

    fun listOrganizationConnections(organizationId: UUID, currentUser: CurrentUser): List<IntegrationConnection> {
        membershipService.requireManagerRole(organizationId, currentUser)
        return connectionRepository.listForOrganization(organizationId)
    }

    fun listUserConnections(currentUser: CurrentUser): List<IntegrationConnection> =
        connectionRepository.listForUser(currentUser.userId)

    fun startOrganizationAuthorization(
        organizationId: UUID,
        provider: IntegrationProvider,
        currentUser: CurrentUser,
    ): AuthorizationStartResult {
        membershipService.requireManagerRole(organizationId, currentUser)
        return startAuthorization(provider, IntegrationOwnerType.ORGANIZATION, organizationId, null, currentUser.userId)
    }

    fun startUserAuthorization(provider: IntegrationProvider, currentUser: CurrentUser): AuthorizationStartResult =
        startAuthorization(provider, IntegrationOwnerType.USER, null, currentUser.userId, currentUser.userId)

    fun completeAuthorization(provider: IntegrationProvider, rawState: String, code: String): IntegrationConnection {
        if (rawState.isBlank() || code.isBlank()) throw ValidationException("The provider callback is missing required authorization values.")
        val state = oauthStateRepository.consume(sha256(rawState))
            ?: throw ValidationException("The authorization state is invalid, expired, or already used.")
        if (state.provider != provider) throw ValidationException("The authorization state does not match this provider.")

        val connection = connectionRepository.findById(state.connectionId)
            ?: throw NotFoundException("INTEGRATION_CONNECTION_NOT_FOUND", "The integration connection could not be found.")
        val definition = catalogService.requireDefinition(provider)
        val runtime = properties.provider(provider)
        val adapter = adapterRegistry.find(provider)
            ?: throw ServiceUnavailableException("INTEGRATION_ADAPTER_NOT_CONFIGURED", "This integration is not configured for authorization.")
        val verifier = credentialCipher.decrypt(state.codeVerifierCiphertext, state.aadContext, state.keyVersion)

        return try {
            val tokens = adapter.exchangeCode(
                OAuthCodeExchangeRequest(
                    provider = provider,
                    clientId = runtime.clientId,
                    clientSecret = runtime.clientSecret,
                    tokenUri = runtime.tokenUri,
                    redirectUri = state.redirectUri,
                    code = code,
                    codeVerifier = verifier,
                    requestedScopes = state.requestedScopes,
                ),
            )
            val credential = storeTokenSet(connection, tokens, connection.credentialId, state.createdByUserId)
            val connected = connectionRepository.markConnected(
                id = connection.id,
                credentialId = credential.id,
                grantedScopes = tokens.grantedScopes,
                externalAccountId = tokens.externalAccountId,
                externalAccountName = tokens.externalAccountName,
                accessTokenExpiresAt = tokens.expiresAt,
            ) ?: throw NotFoundException("INTEGRATION_CONNECTION_NOT_FOUND", "The integration connection could not be found.")
            connection.credentialId?.takeIf { it != credential.id }?.let(credentialRepository::revoke)
            connectionRepository.insertEvent(
                connected,
                "AUTHORIZATION_COMPLETED",
                connection.status,
                IntegrationConnectionStatus.CONNECTED,
                state.createdByUserId,
                mapOf("provider" to provider.name, "scopes" to tokens.grantedScopes),
            )
            auditService.record(
                state.createdByUserId,
                state.organizationId,
                "integration.authorization_completed",
                "integration_connection",
                connected.id,
            )
            connected
        } catch (ex: Exception) {
            val failed = connectionRepository.markAuthorizationFailed(
                connection.id,
                "AUTHORIZATION_EXCHANGE_FAILED",
                "The provider authorization could not be completed.",
            ) ?: connection
            connection.credentialId?.let(credentialRepository::revoke)
            connectionRepository.insertEvent(
                failed,
                "AUTHORIZATION_FAILED",
                connection.status,
                IntegrationConnectionStatus.DISCONNECTED,
                state.createdByUserId,
                mapOf("provider" to definition.provider.name),
            )
            if (ex is ServiceUnavailableException || ex is ValidationException) throw ex
            throw ServiceUnavailableException("INTEGRATION_AUTHORIZATION_FAILED", "The provider authorization could not be completed.")
        }
    }

    fun failAuthorization(provider: IntegrationProvider, rawState: String?, providerError: String): IntegrationConnection? {
        if (rawState.isNullOrBlank()) return null
        val state = oauthStateRepository.consume(sha256(rawState)) ?: return null
        if (state.provider != provider) return null
        val connection = connectionRepository.findById(state.connectionId) ?: return null
        val failed = connectionRepository.markAuthorizationFailed(
            connection.id,
            "PROVIDER_AUTHORIZATION_DENIED",
            "The provider did not authorize the connection.",
        ) ?: return connection
        connection.credentialId?.let(credentialRepository::revoke)
        connectionRepository.insertEvent(
            failed,
            "AUTHORIZATION_DENIED",
            connection.status,
            IntegrationConnectionStatus.DISCONNECTED,
            state.createdByUserId,
            mapOf("provider" to provider.name, "providerError" to providerError.take(120)),
        )
        auditService.record(
            state.createdByUserId,
            state.organizationId,
            "integration.authorization_denied",
            "integration_connection",
            failed.id,
        )
        return failed
    }

    fun refreshOrganizationConnection(
        organizationId: UUID,
        connectionId: UUID,
        currentUser: CurrentUser,
    ): IntegrationConnection {
        membershipService.requireManagerRole(organizationId, currentUser)
        val existing = connectionRepository.findByIdForOrganization(connectionId, organizationId)
            ?: throw NotFoundException("INTEGRATION_CONNECTION_NOT_FOUND", "The integration connection could not be found.")
        val locked = connectionRepository.acquireRefreshLock(connectionId, currentUser.userId)
            ?: throw ConflictException("INTEGRATION_REFRESH_IN_PROGRESS", "This integration is already refreshing or is not refreshable.")
        return refreshLocked(existing, locked, currentUser.userId)
    }

    fun disconnectOrganizationConnection(
        organizationId: UUID,
        connectionId: UUID,
        currentUser: CurrentUser,
    ): IntegrationConnection {
        membershipService.requireManagerRole(organizationId, currentUser)
        val existing = connectionRepository.findByIdForOrganization(connectionId, organizationId)
            ?: throw NotFoundException("INTEGRATION_CONNECTION_NOT_FOUND", "The integration connection could not be found.")
        val disconnected = connectionRepository.markDisconnected(connectionId)!!
        existing.credentialId?.let(credentialRepository::revoke)
        connectionRepository.insertEvent(
            disconnected,
            "DISCONNECTED_LOCALLY",
            existing.status,
            IntegrationConnectionStatus.DISCONNECTED,
            currentUser.userId,
        )
        auditService.record(currentUser.userId, organizationId, "integration.disconnected", "integration_connection", connectionId)
        return disconnected
    }

    fun revokeOrganizationConnection(
        organizationId: UUID,
        connectionId: UUID,
        currentUser: CurrentUser,
    ): IntegrationConnection {
        membershipService.requireManagerRole(organizationId, currentUser)
        val existing = connectionRepository.findByIdForOrganization(connectionId, organizationId)
            ?: throw NotFoundException("INTEGRATION_CONNECTION_NOT_FOUND", "The integration connection could not be found.")
        val runtime = properties.provider(existing.provider)
        if (runtime.revocationUri.isBlank() && !properties.stubMode) {
            throw ServiceUnavailableException("INTEGRATION_REVOCATION_NOT_CONFIGURED", "Provider revocation is not configured for this integration.")
        }
        val adapter = adapterRegistry.find(existing.provider)
            ?: throw ServiceUnavailableException("INTEGRATION_ADAPTER_NOT_CONFIGURED", "This integration provider is not configured.")
        val credential = requireCredential(existing)
        val tokenSet = decryptTokenSet(credential)
        try {
            adapter.revoke(
                OAuthRevokeRequest(
                    provider = existing.provider,
                    clientId = runtime.clientId,
                    clientSecret = runtime.clientSecret,
                    revocationUri = runtime.revocationUri,
                    accessToken = tokenSet.accessToken,
                    refreshToken = tokenSet.refreshToken,
                ),
            )
        } catch (_: Exception) {
            throw ServiceUnavailableException("INTEGRATION_REVOCATION_FAILED", "The provider did not confirm credential revocation.")
        }
        val revoked = connectionRepository.markRevoked(connectionId)!!
        credentialRepository.revoke(credential.id)
        connectionRepository.insertEvent(
            revoked,
            "PROVIDER_REVOKED",
            existing.status,
            IntegrationConnectionStatus.REVOKED,
            currentUser.userId,
        )
        auditService.record(currentUser.userId, organizationId, "integration.revoked", "integration_connection", connectionId)
        return revoked
    }

    fun checkOrganizationHealth(
        organizationId: UUID,
        connectionId: UUID,
        currentUser: CurrentUser,
    ): IntegrationHealthCheck {
        membershipService.requireManagerRole(organizationId, currentUser)
        val connection = connectionRepository.findByIdForOrganization(connectionId, organizationId)
            ?: throw NotFoundException("INTEGRATION_CONNECTION_NOT_FOUND", "The integration connection could not be found.")
        return checkHealth(connection, currentUser.userId)
    }

    fun refreshUserConnection(connectionId: UUID, currentUser: CurrentUser): IntegrationConnection {
        val existing = connectionRepository.findByIdForUser(connectionId, currentUser.userId)
            ?: throw NotFoundException("INTEGRATION_CONNECTION_NOT_FOUND", "The integration connection could not be found.")
        val locked = connectionRepository.acquireRefreshLock(connectionId, currentUser.userId)
            ?: throw ConflictException("INTEGRATION_REFRESH_IN_PROGRESS", "This integration is already refreshing or is not refreshable.")
        return refreshLocked(existing, locked, currentUser.userId)
    }

    fun disconnectUserConnection(connectionId: UUID, currentUser: CurrentUser): IntegrationConnection {
        val existing = connectionRepository.findByIdForUser(connectionId, currentUser.userId)
            ?: throw NotFoundException("INTEGRATION_CONNECTION_NOT_FOUND", "The integration connection could not be found.")
        val disconnected = connectionRepository.markDisconnected(connectionId)!!
        existing.credentialId?.let(credentialRepository::revoke)
        connectionRepository.insertEvent(
            disconnected,
            "DISCONNECTED_LOCALLY",
            existing.status,
            IntegrationConnectionStatus.DISCONNECTED,
            currentUser.userId,
        )
        auditService.record(currentUser.userId, null, "integration.disconnected", "integration_connection", connectionId)
        return disconnected
    }

    fun revokeUserConnection(connectionId: UUID, currentUser: CurrentUser): IntegrationConnection {
        val existing = connectionRepository.findByIdForUser(connectionId, currentUser.userId)
            ?: throw NotFoundException("INTEGRATION_CONNECTION_NOT_FOUND", "The integration connection could not be found.")
        val runtime = properties.provider(existing.provider)
        if (runtime.revocationUri.isBlank()) {
            throw ServiceUnavailableException("INTEGRATION_REVOCATION_NOT_CONFIGURED", "Provider revocation is not configured. Disconnect the integration locally instead.")
        }
        val adapter = adapterRegistry.find(existing.provider)
            ?: throw ServiceUnavailableException("INTEGRATION_ADAPTER_NOT_CONFIGURED", "This integration provider is not configured.")
        val credential = requireCredential(existing)
        val tokenSet = decryptTokenSet(credential)
        try {
            adapter.revoke(
                OAuthRevokeRequest(
                    provider = existing.provider,
                    clientId = runtime.clientId,
                    clientSecret = runtime.clientSecret,
                    revocationUri = runtime.revocationUri,
                    accessToken = tokenSet.accessToken,
                    refreshToken = tokenSet.refreshToken,
                ),
            )
        } catch (_: Exception) {
            throw ServiceUnavailableException("INTEGRATION_REVOCATION_FAILED", "The provider did not confirm credential revocation.")
        }
        val revoked = connectionRepository.markRevoked(connectionId)!!
        credentialRepository.revoke(credential.id)
        connectionRepository.insertEvent(
            revoked,
            "PROVIDER_REVOKED",
            existing.status,
            IntegrationConnectionStatus.REVOKED,
            currentUser.userId,
        )
        auditService.record(currentUser.userId, null, "integration.revoked", "integration_connection", connectionId)
        return revoked
    }

    fun checkUserHealth(connectionId: UUID, currentUser: CurrentUser): IntegrationHealthCheck {
        val connection = connectionRepository.findByIdForUser(connectionId, currentUser.userId)
            ?: throw NotFoundException("INTEGRATION_CONNECTION_NOT_FOUND", "The integration connection could not be found.")
        return checkHealth(connection, currentUser.userId)
    }

    fun accessTokenForUserConnection(connectionId: UUID, currentUser: CurrentUser): IntegrationAccessToken {
        val connection = connectionRepository.findByIdForUser(connectionId, currentUser.userId)
            ?: throw NotFoundException("INTEGRATION_CONNECTION_NOT_FOUND", "The integration connection could not be found.")
        if (connection.status !in setOf(IntegrationConnectionStatus.CONNECTED, IntegrationConnectionStatus.DEGRADED)) {
            throw ValidationException("This integration is not connected.")
        }
        return IntegrationAccessToken(connection, decryptTokenSet(requireCredential(connection)).accessToken)
    }

    private fun checkHealth(connection: IntegrationConnection, actorUserId: UUID): IntegrationHealthCheck {
        val adapter = adapterRegistry.find(connection.provider)
            ?: throw ServiceUnavailableException("INTEGRATION_ADAPTER_NOT_CONFIGURED", "This integration provider is not configured.")
        val tokenSet = decryptTokenSet(requireCredential(connection))
        val started = System.nanoTime()
        val result = try {
            adapter.checkHealth(connection.provider, tokenSet.accessToken)
        } catch (_: Exception) {
            ProviderHealthResult(false, errorCode = "HEALTH_CHECK_FAILED", errorMessage = "The provider health check failed.")
        }
        val elapsed = (System.nanoTime() - started) / 1_000_000
        val status = when {
            result.healthy -> IntegrationHealthStatus.HEALTHY
            result.degraded -> IntegrationHealthStatus.DEGRADED
            else -> IntegrationHealthStatus.FAILED
        }
        val nextStatus = if (status == IntegrationHealthStatus.HEALTHY) {
            IntegrationConnectionStatus.CONNECTED
        } else {
            IntegrationConnectionStatus.DEGRADED
        }
        if (status == IntegrationHealthStatus.HEALTHY) {
            connectionRepository.markConnected(
                connection.id,
                requireNotNull(connection.credentialId),
                connection.grantedScopes,
                connection.externalAccountId,
                connection.externalAccountName,
                connection.accessTokenExpiresAt,
            )
        } else {
            connectionRepository.markDegraded(
                connection.id,
                result.errorCode ?: "HEALTH_CHECK_FAILED",
                result.errorMessage ?: "The provider health check failed.",
            )
        }
        val health = connectionRepository.insertHealthCheck(
            connection.id,
            status,
            result.latencyMs ?: elapsed,
            result.errorCode,
            result.errorMessage,
            actorUserId,
        )
        connectionRepository.insertEvent(
            connection,
            "HEALTH_CHECKED",
            connection.status,
            nextStatus,
            actorUserId,
            mapOf("health" to status.name),
        )
        return health
    }

    private fun startAuthorization(
        provider: IntegrationProvider,
        ownerType: IntegrationOwnerType,
        organizationId: UUID?,
        userId: UUID?,
        actorUserId: UUID,
    ): AuthorizationStartResult {
        val definition = catalogService.requireDefinition(provider)
        if (definition.ownershipScope != ownerType || definition.primaryAuthMode != IntegrationAuthMode.OAUTH2) {
            throw ValidationException("This provider does not support this authorization flow.")
        }
        if (catalogService.readiness(definition) != IntegrationReadiness.AVAILABLE) {
            throw ServiceUnavailableException("INTEGRATION_NOT_CONFIGURED", "This integration is not configured for authorization yet.")
        }
        val runtime = properties.provider(provider)
        val adapter = adapterRegistry.find(provider)
            ?: throw ServiceUnavailableException("INTEGRATION_ADAPTER_NOT_CONFIGURED", "This integration is not configured for authorization.")
        val existing = when (ownerType) {
            IntegrationOwnerType.ORGANIZATION -> connectionRepository.findActiveForOrganization(organizationId!!, provider)
            IntegrationOwnerType.USER -> connectionRepository.findActiveForUser(userId!!, provider)
            IntegrationOwnerType.PLATFORM -> null
        }
        if (existing?.status == IntegrationConnectionStatus.CONNECTED) {
            throw ConflictException("INTEGRATION_ALREADY_CONNECTED", "This provider is already connected.")
        }
        val connection = if (existing == null) {
            connectionRepository.insertAuthorizationPending(definition, ownerType, organizationId, userId, actorUserId)
        } else {
            connectionRepository.markAuthorizationPending(existing.id)!!
        }

        val state = randomUrlSafe(32)
        val verifier = randomUrlSafe(64)
        val challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)),
        )
        val stateHash = sha256(state)
        val aad = "oauth-pkce:${provider.name}:${connection.id}:$stateHash"
        val encryptedVerifier = credentialCipher.encrypt(verifier, aad)
        val redirectUri = "${properties.oauthCallbackBaseUrl.trimEnd('/')}/${provider.configKey}/callback"
        val scopes = runtime.scopes.ifEmpty { definition.defaultScopes }
        val authorizationUrl = adapter.buildAuthorizationUrl(
            OAuthAuthorizationRequest(
                provider = provider,
                clientId = runtime.clientId,
                authorizationUri = runtime.authorizationUri,
                redirectUri = redirectUri,
                state = state,
                codeChallenge = challenge,
                scopes = scopes,
            ),
        )
        val expiresAt = Instant.now().plus(properties.oauthStateTtlMinutes, ChronoUnit.MINUTES)
        oauthStateRepository.insert(
            provider = provider,
            connectionId = connection.id,
            ownerType = ownerType,
            organizationId = organizationId,
            userId = userId,
            stateHash = stateHash,
            codeVerifierCiphertext = encryptedVerifier.ciphertext,
            keyVersion = encryptedVerifier.keyVersion,
            aadContext = aad,
            redirectUri = redirectUri,
            requestedScopes = scopes,
            expiresAt = expiresAt,
            createdByUserId = actorUserId,
        )
        connectionRepository.insertEvent(
            connection,
            "AUTHORIZATION_STARTED",
            existing?.status,
            IntegrationConnectionStatus.AUTHORIZATION_PENDING,
            actorUserId,
            mapOf("provider" to provider.name, "expiresAt" to expiresAt.toString()),
        )
        auditService.record(actorUserId, organizationId, "integration.authorization_started", "integration_connection", connection.id)
        return AuthorizationStartResult(connection, authorizationUrl, expiresAt)
    }

    private fun refreshLocked(existing: IntegrationConnection, locked: IntegrationConnection, actorUserId: UUID): IntegrationConnection {
        val runtime = properties.provider(locked.provider)
        val adapter = adapterRegistry.find(locked.provider)
            ?: run {
                connectionRepository.releaseRefreshLock(locked.id)
                throw ServiceUnavailableException("INTEGRATION_ADAPTER_NOT_CONFIGURED", "This integration provider is not configured.")
            }
        val credential = requireCredential(locked)
        val currentTokens = decryptTokenSet(credential)
        val refreshToken = currentTokens.refreshToken
            ?: run {
                connectionRepository.releaseRefreshLock(locked.id)
                throw ValidationException("This integration does not have a refresh token. Reauthorize it instead.")
            }
        return try {
            val refreshed = adapter.refresh(
                OAuthRefreshRequest(
                    provider = locked.provider,
                    clientId = runtime.clientId,
                    clientSecret = runtime.clientSecret,
                    tokenUri = runtime.tokenUri,
                    refreshToken = refreshToken,
                    currentScopes = locked.grantedScopes,
                ),
            )
            val newCredential = storeTokenSet(locked, refreshed, credential.id, actorUserId)
            val connected = connectionRepository.markConnected(
                locked.id,
                newCredential.id,
                refreshed.grantedScopes,
                refreshed.externalAccountId,
                refreshed.externalAccountName,
                refreshed.expiresAt,
            )!!
            credentialRepository.revoke(credential.id)
            connectionRepository.insertEvent(
                connected,
                "TOKEN_REFRESHED",
                existing.status,
                IntegrationConnectionStatus.CONNECTED,
                actorUserId,
            )
            auditService.record(actorUserId, connected.organizationId, "integration.token_refreshed", "integration_connection", connected.id)
            connected
        } catch (ex: Exception) {
            connectionRepository.markDegraded(locked.id, "TOKEN_REFRESH_FAILED", "The provider token could not be refreshed.")
            if (ex is ServiceUnavailableException || ex is ValidationException) throw ex
            throw ServiceUnavailableException("INTEGRATION_REFRESH_FAILED", "The provider token could not be refreshed.")
        }
    }

    private fun storeTokenSet(
        connection: IntegrationConnection,
        tokenSet: ProviderTokenSet,
        rotatedFromId: UUID?,
        actorUserId: UUID,
    ) = run {
        val payload = StoredOAuthTokenSet(
            accessToken = tokenSet.accessToken,
            refreshToken = tokenSet.refreshToken,
            tokenType = tokenSet.tokenType,
            grantedScopes = tokenSet.grantedScopes,
        )
        val aad = credentialAad(connection)
        val encrypted = credentialCipher.encrypt(objectMapper.writeValueAsString(payload), aad)
        credentialRepository.insert(
            provider = connection.provider,
            ownerType = connection.ownerType,
            organizationId = connection.organizationId,
            userId = connection.userId,
            credentialKind = IntegrationCredentialKind.OAUTH_TOKEN_SET,
            ciphertext = encrypted.ciphertext,
            keyVersion = encrypted.keyVersion,
            aadContext = aad,
            rotatedFromId = rotatedFromId,
            createdByUserId = actorUserId,
        )
    }

    private fun requireCredential(connection: IntegrationConnection) =
        connection.credentialId?.let(credentialRepository::findById)
            ?.takeIf { it.revokedAt == null }
            ?: throw ServiceUnavailableException("INTEGRATION_CREDENTIAL_UNAVAILABLE", "This integration does not have an active credential.")

    private fun decryptTokenSet(credential: com.leaguelift.integration.core.domain.IntegrationCredentialSecret): StoredOAuthTokenSet =
        objectMapper.readValue(
            credentialCipher.decrypt(credential.ciphertext, credential.aadContext, credential.keyVersion),
            object : TypeReference<StoredOAuthTokenSet>() {},
        )

    private fun credentialAad(connection: IntegrationConnection): String {
        val ownerId = connection.organizationId ?: connection.userId ?: "platform"
        return "integration-credential:${connection.provider.name}:${connection.ownerType.name}:$ownerId:${connection.id}"
    }

    private fun randomUrlSafe(bytes: Int): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(bytes).also(secureRandom::nextBytes))

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
