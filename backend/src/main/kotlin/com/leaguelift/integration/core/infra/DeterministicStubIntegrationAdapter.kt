package com.leaguelift.integration.core.infra

import com.leaguelift.integration.core.application.IntegrationAuthorizationAdapter
import com.leaguelift.integration.core.application.OAuthAuthorizationRequest
import com.leaguelift.integration.core.application.OAuthCodeExchangeRequest
import com.leaguelift.integration.core.application.OAuthRefreshRequest
import com.leaguelift.integration.core.application.OAuthRevokeRequest
import com.leaguelift.integration.core.application.ProviderHealthResult
import com.leaguelift.integration.core.application.ProviderTokenSet
import com.leaguelift.common.error.ServiceUnavailableException
import com.leaguelift.common.error.ValidationException
import com.leaguelift.integration.core.domain.IntegrationProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.springframework.web.util.UriComponentsBuilder
import java.security.MessageDigest
import java.time.Instant

/**
 * Deterministic local/test adapter. RuntimeGuard makes this impossible in staging or
 * production. It simulates lifecycle behavior without claiming an official provider
 * contract or undocumented response field.
 */
@Component
@ConditionalOnProperty(prefix = "leaguelift.integrations", name = ["stub-mode"], havingValue = "true")
class DeterministicStubIntegrationAdapter : IntegrationAuthorizationAdapter {
    private val supported = setOf(
        IntegrationProvider.GOOGLE_CALENDAR,
        IntegrationProvider.QUICKBOOKS_ONLINE,
        IntegrationProvider.SPORTSENGINE,
    )

    override fun supports(provider: IntegrationProvider): Boolean = provider in supported

    override fun buildAuthorizationUrl(request: OAuthAuthorizationRequest): String =
        UriComponentsBuilder.fromUriString("https://integration-stub.invalid/authorize")
            .queryParam("provider", request.provider.name)
            .queryParam("client_id", request.clientId)
            .queryParam("redirect_uri", request.redirectUri)
            .queryParam("response_type", "code")
            .queryParam("state", request.state)
            .queryParam("code_challenge", request.codeChallenge)
            .queryParam("code_challenge_method", "S256")
            .queryParam("scope", request.scopes.joinToString(" "))
            .build(true)
            .toUriString()

    override fun exchangeCode(request: OAuthCodeExchangeRequest): ProviderTokenSet {
        if (request.code.isBlank() || request.code == "stub-rejected") {
            throw ValidationException("The local stub rejected the authorization code.")
        }
        if (request.code == "stub-rate-limit") {
            throw ServiceUnavailableException("INTEGRATION_PROVIDER_RATE_LIMITED", "The local stub simulated a provider rate limit.")
        }
        val seed = digest("${request.provider.name}:${request.code}:${request.codeVerifier}")
        val expired = request.code == "stub-expired"
        return ProviderTokenSet(
            accessToken = if (expired) "stub-expired-$seed" else "stub-access-$seed",
            refreshToken = "stub-refresh-$seed",
            expiresAt = if (expired) Instant.now().minusSeconds(60) else Instant.now().plusSeconds(3600),
            grantedScopes = request.requestedScopes,
            externalAccountId = "stub-${request.provider.configKey}-account",
            externalAccountName = "Local test account",
        )
    }

    override fun refresh(request: OAuthRefreshRequest): ProviderTokenSet {
        if (request.refreshToken.contains("rate-limit")) {
            throw ServiceUnavailableException("INTEGRATION_PROVIDER_RATE_LIMITED", "The local stub simulated a provider rate limit.")
        }
        if (request.refreshToken.contains("revoked")) {
            throw ValidationException("The local stub simulated a revoked refresh token.")
        }
        val seed = digest("${request.provider.name}:${request.refreshToken}:refresh")
        return ProviderTokenSet(
            accessToken = "stub-access-$seed",
            refreshToken = request.refreshToken,
            expiresAt = Instant.now().plusSeconds(3600),
            grantedScopes = request.currentScopes,
            externalAccountId = null,
            externalAccountName = null,
        )
    }

    override fun revoke(request: OAuthRevokeRequest) = Unit

    override fun checkHealth(provider: IntegrationProvider, accessToken: String): ProviderHealthResult =
        if (accessToken.startsWith("stub-access-")) {
            ProviderHealthResult(healthy = true, latencyMs = 1)
        } else if (accessToken.startsWith("stub-expired-")) {
            ProviderHealthResult(healthy = false, errorCode = "TOKEN_EXPIRED", errorMessage = "The local stub token is expired.")
        } else {
            ProviderHealthResult(healthy = false, errorCode = "STUB_TOKEN_REJECTED", errorMessage = "The local stub rejected the token.")
        }

    private fun digest(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(24)
}
