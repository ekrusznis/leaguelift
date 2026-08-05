package com.rally26.integration.core.application

import com.rally26.integration.core.domain.IntegrationProvider
import java.time.Instant

data class OAuthAuthorizationRequest(
    val provider: IntegrationProvider,
    val clientId: String,
    val authorizationUri: String,
    val redirectUri: String,
    val state: String,
    val codeChallenge: String,
    val scopes: List<String>,
)

data class OAuthCodeExchangeRequest(
    val provider: IntegrationProvider,
    val clientId: String,
    val clientSecret: String,
    val tokenUri: String,
    val redirectUri: String,
    val code: String,
    val codeVerifier: String,
    val requestedScopes: List<String>,
)

data class OAuthRefreshRequest(
    val provider: IntegrationProvider,
    val clientId: String,
    val clientSecret: String,
    val tokenUri: String,
    val refreshToken: String,
    val currentScopes: List<String>,
)

data class OAuthRevokeRequest(
    val provider: IntegrationProvider,
    val clientId: String,
    val clientSecret: String,
    val revocationUri: String,
    val accessToken: String,
    val refreshToken: String?,
)

data class ProviderTokenSet(
    val accessToken: String,
    val refreshToken: String?,
    val tokenType: String = "Bearer",
    val expiresAt: Instant?,
    val grantedScopes: List<String>,
    val externalAccountId: String?,
    val externalAccountName: String?,
)

data class ProviderHealthResult(
    val healthy: Boolean,
    val degraded: Boolean = false,
    val latencyMs: Long? = null,
    val errorCode: String? = null,
    val errorMessage: String? = null,
)

/**
 * Provider-neutral OAuth seam. Phase 19 contains no undocumented production HTTP
 * implementation; official adapters are added and contract-verified provider by
 * provider before activation.
 */
interface IntegrationAuthorizationAdapter {
    fun supports(provider: IntegrationProvider): Boolean

    fun buildAuthorizationUrl(request: OAuthAuthorizationRequest): String

    fun exchangeCode(request: OAuthCodeExchangeRequest): ProviderTokenSet

    fun refresh(request: OAuthRefreshRequest): ProviderTokenSet

    fun revoke(request: OAuthRevokeRequest)

    fun checkHealth(
        provider: IntegrationProvider,
        accessToken: String,
    ): ProviderHealthResult
}
