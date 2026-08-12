package com.rally26.integration.core.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.rally26.audit.application.AuditService
import com.rally26.common.error.ValidationException
import com.rally26.config.IntegrationProperties
import com.rally26.config.IntegrationProviderRuntimeProperties
import com.rally26.integration.core.domain.IntegrationAdapterMode
import com.rally26.integration.core.domain.IntegrationAuthMode
import com.rally26.integration.core.domain.IntegrationCategory
import com.rally26.integration.core.domain.IntegrationConnection
import com.rally26.integration.core.domain.IntegrationConnectionStatus
import com.rally26.integration.core.domain.IntegrationCredentialKind
import com.rally26.integration.core.domain.IntegrationCredentialSecret
import com.rally26.integration.core.domain.IntegrationOAuthState
import com.rally26.integration.core.domain.IntegrationOwnerType
import com.rally26.integration.core.domain.IntegrationProvider
import com.rally26.integration.core.domain.IntegrationProviderDefinition
import com.rally26.integration.core.domain.IntegrationReadiness
import com.rally26.integration.core.persistence.IntegrationConnectionRepository
import com.rally26.integration.core.persistence.IntegrationCredentialRepository
import com.rally26.integration.core.persistence.IntegrationOAuthStateRepository
import com.rally26.membership.application.MembershipService
import com.rally26.subscription.application.PlanEntitlementService
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.Instant
import java.util.Base64
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class IntegrationOAuthServiceTest {
    private val catalogService = mockk<IntegrationCatalogService>()
    private val connectionRepository = mockk<IntegrationConnectionRepository>(relaxed = true)
    private val credentialRepository = mockk<IntegrationCredentialRepository>()
    private val oauthStateRepository = mockk<IntegrationOAuthStateRepository>()
    private val adapterRegistry = mockk<IntegrationAdapterRegistry>()
    private val membershipService = mockk<MembershipService>()
    private val auditService = mockk<AuditService>(relaxed = true)
    private val encryptionKey = Base64.getEncoder().encodeToString(ByteArray(32) { 9 })
    private val properties =
        IntegrationProperties(
            encryptionKey = encryptionKey,
            oauthCallbackBaseUrl = "http://localhost:8080/api/v1/integrations/oauth",
            providers =
                mapOf(
                    "quickbooks-online" to
                        IntegrationProviderRuntimeProperties(
                            enabled = true,
                            clientId = "client-id",
                            clientSecret = "client-secret",
                            tokenUri = "https://provider.invalid/token",
                        ),
                ),
        )
    private val cipher = CredentialCipher(properties)
    private val planEntitlementService = mockk<PlanEntitlementService>(relaxed = true)
    private val service =
        IntegrationOAuthService(
            catalogService,
            connectionRepository,
            credentialRepository,
            oauthStateRepository,
            adapterRegistry,
            membershipService,
            cipher,
            properties,
            ObjectMapper(),
            auditService,
            planEntitlementService,
        )

    @Test
    fun `callback rejects an unknown or replayed state before using an adapter`() {
        every { oauthStateRepository.consume(any()) } returns null

        assertFailsWith<ValidationException> {
            service.completeAuthorization(IntegrationProvider.QUICKBOOKS_ONLINE, "unknown-state", "code")
        }

        verify(exactly = 0) { adapterRegistry.find(any()) }
        verify(exactly = 0) { credentialRepository.insert(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `successful callback encrypts tokens before marking the connection connected`() {
        val actorId = UUID.randomUUID()
        val organizationId = UUID.randomUUID()
        val connection = connection(organizationId, actorId)
        val aad = "oauth-pkce:${IntegrationProvider.QUICKBOOKS_ONLINE.name}:${connection.id}:state-hash"
        val verifier = cipher.encrypt("pkce-verifier", aad)
        val state =
            IntegrationOAuthState(
                id = UUID.randomUUID(),
                provider = IntegrationProvider.QUICKBOOKS_ONLINE,
                connectionId = connection.id,
                ownerType = IntegrationOwnerType.ORGANIZATION,
                organizationId = organizationId,
                userId = null,
                stateHash = "state-hash",
                codeVerifierCiphertext = verifier.ciphertext,
                keyVersion = verifier.keyVersion,
                aadContext = aad,
                redirectUri = "http://localhost:8080/api/v1/integrations/oauth/quickbooks-online/callback",
                requestedScopes = listOf("com.intuit.quickbooks.accounting"),
                expiresAt = Instant.now().plusSeconds(600),
                consumedAt = Instant.now(),
                createdByUserId = actorId,
                createdAt = Instant.now(),
            )
        val adapter = mockk<IntegrationAuthorizationAdapter>()
        val tokenSet =
            ProviderTokenSet(
                accessToken = "provider-access-token",
                refreshToken = "provider-refresh-token",
                expiresAt = Instant.now().plusSeconds(3600),
                grantedScopes = state.requestedScopes,
                externalAccountId = "realm-123",
                externalAccountName = "Test Company",
            )
        val ciphertext = slot<String>()
        val stored =
            IntegrationCredentialSecret(
                id = UUID.randomUUID(),
                provider = connection.provider,
                ownerType = connection.ownerType,
                organizationId = organizationId,
                userId = null,
                credentialKind = IntegrationCredentialKind.OAUTH_TOKEN_SET,
                ciphertext = "encrypted",
                keyVersion = 1,
                aadContext = "credential-aad",
                rotatedFromId = null,
                createdByUserId = actorId,
                createdAt = Instant.now(),
                revokedAt = null,
            )
        val connected =
            connection.copy(
                status = IntegrationConnectionStatus.CONNECTED,
                credentialId = stored.id,
                grantedScopes = tokenSet.grantedScopes,
                externalAccountId = tokenSet.externalAccountId,
                externalAccountName = tokenSet.externalAccountName,
                accessTokenExpiresAt = tokenSet.expiresAt,
                connectedAt = Instant.now(),
            )

        every { oauthStateRepository.consume(any()) } returns state
        every { connectionRepository.findById(connection.id) } returns connection
        every { catalogService.requireDefinition(connection.provider) } returns definition()
        every { adapterRegistry.find(connection.provider) } returns adapter
        every { adapter.exchangeCode(any()) } returns tokenSet
        every {
            credentialRepository.insert(
                connection.provider,
                connection.ownerType,
                organizationId,
                null,
                IntegrationCredentialKind.OAUTH_TOKEN_SET,
                capture(ciphertext),
                1,
                any(),
                null,
                actorId,
            )
        } returns stored
        every {
            connectionRepository.markConnected(
                connection.id,
                stored.id,
                tokenSet.grantedScopes,
                tokenSet.externalAccountId,
                tokenSet.externalAccountName,
                tokenSet.expiresAt,
            )
        } returns connected

        val result = service.completeAuthorization(connection.provider, "valid-state", "valid-code")

        assertEquals(IntegrationConnectionStatus.CONNECTED, result.status)
        assertNotEquals(tokenSet.accessToken, ciphertext.captured)
        assertNotEquals(tokenSet.refreshToken, ciphertext.captured)
        verify(exactly = 1) { connectionRepository.markConnected(any(), any(), any(), any(), any(), any()) }
    }

    private fun definition() =
        IntegrationProviderDefinition(
            provider = IntegrationProvider.QUICKBOOKS_ONLINE,
            displayName = "QuickBooks Online",
            category = IntegrationCategory.ACCOUNTING,
            ownershipScope = IntegrationOwnerType.ORGANIZATION,
            primaryAuthMode = IntegrationAuthMode.OAUTH2,
            supportedAuthModes = listOf(IntegrationAuthMode.OAUTH2),
            baselineReadiness = IntegrationReadiness.NOT_CONFIGURED,
            adapterMode = IntegrationAdapterMode.OAUTH_SCAFFOLD,
            description = "Accounting scaffold",
            activationRequirement = "Phase 20 credentials",
            defaultScopes = listOf("com.intuit.quickbooks.accounting"),
            sortOrder = 1,
            visibleToCustomers = true,
        )

    private fun connection(
        organizationId: UUID,
        actorId: UUID,
    ) = IntegrationConnection(
        id = UUID.randomUUID(),
        provider = IntegrationProvider.QUICKBOOKS_ONLINE,
        category = IntegrationCategory.ACCOUNTING,
        ownerType = IntegrationOwnerType.ORGANIZATION,
        organizationId = organizationId,
        userId = null,
        authMode = IntegrationAuthMode.OAUTH2,
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
        createdByUserId = actorId,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
        connectedAt = null,
        revokedAt = null,
        disconnectedAt = null,
    )
}
