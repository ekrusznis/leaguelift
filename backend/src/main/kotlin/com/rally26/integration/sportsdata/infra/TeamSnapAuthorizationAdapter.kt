package com.rally26.integration.sportsdata.infra

import com.fasterxml.jackson.annotation.JsonProperty
import com.rally26.common.error.ServiceUnavailableException
import com.rally26.integration.core.application.IntegrationAuthorizationAdapter
import com.rally26.integration.core.application.OAuthAuthorizationRequest
import com.rally26.integration.core.application.OAuthCodeExchangeRequest
import com.rally26.integration.core.application.OAuthRefreshRequest
import com.rally26.integration.core.application.OAuthRevokeRequest
import com.rally26.integration.core.application.ProviderHealthResult
import com.rally26.integration.core.application.ProviderTokenSet
import com.rally26.integration.core.domain.IntegrationProvider
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.util.UriComponentsBuilder

private val log = LoggerFactory.getLogger(TeamSnapAuthorizationAdapter::class.java)

/** TeamSnap's real APIv3 base — confirmed from the official `teamsnap_rb` SDK's `DEFAULT_URL` constant, not guessed. Only used for the [checkHealth] probe here; the data-fetch client (`TeamSnapDataClient`) has its own copy since it belongs to a different concern. */
private const val TEAMSNAP_API_BASE_URL = "https://apiv3.teamsnap.com"

private data class TeamSnapTokenExchangeRequest(
    @JsonProperty("grant_type") val grantType: String = "authorization_code",
    @JsonProperty("client_id") val clientId: String,
    @JsonProperty("client_secret") val clientSecret: String,
    @JsonProperty("code") val code: String,
    @JsonProperty("redirect_uri") val redirectUri: String,
)

private data class TeamSnapRefreshRequest(
    @JsonProperty("grant_type") val grantType: String = "refresh_token",
    @JsonProperty("client_id") val clientId: String,
    @JsonProperty("client_secret") val clientSecret: String,
    @JsonProperty("refresh_token") val refreshToken: String,
)

private data class TeamSnapTokenResponse(
    @JsonProperty("access_token") val accessToken: String?,
    @JsonProperty("refresh_token") val refreshToken: String?,
    @JsonProperty("expires_in") val expiresIn: Long?,
)

/**
 * Real adapter against TeamSnap's APIv3 OAuth2 (researched 2026-08-12, not guessed):
 * app registration is self-service at `auth.teamsnap.com` (client_id/client_secret,
 * redirect URI); TeamSnap's own docs describe it as standard OAuth2 ("assumes you
 * know how to work with OAuth 2") rather than a custom variant like SportsEngine's
 * organization-grant flow, so this is a conventional authorization-code exchange
 * and refresh-token flow. [OAuthAuthorizationRequest.authorizationUri]/
 * [OAuthCodeExchangeRequest.tokenUri] come from
 * `rally26.integrations.providers.teamsnap.*` config, not hardcoded — real values
 * only need a config change once the founder registers an app, no code change.
 * The exact authorize/token endpoint paths on `auth.teamsnap.com` weren't
 * independently confirmed this pass (TeamSnap's public docs pages 404'd/redirected
 * during research) — spot-check the exact `authorization-uri`/`token-uri` values
 * against the real developer-portal app settings before activation.
 */
@Component
class TeamSnapAuthorizationAdapter : IntegrationAuthorizationAdapter {
    private val restClient = RestClient.create()

    override fun supports(provider: IntegrationProvider): Boolean = provider == IntegrationProvider.TEAMSNAP

    override fun buildAuthorizationUrl(request: OAuthAuthorizationRequest): String =
        UriComponentsBuilder
            .fromUriString(request.authorizationUri)
            .queryParam("client_id", request.clientId)
            .queryParam("redirect_uri", request.redirectUri)
            .queryParam("response_type", "code")
            .queryParam("state", request.state)
            .apply { if (request.scopes.isNotEmpty()) queryParam("scope", request.scopes.joinToString(" ")) }
            .build(true)
            .toUriString()

    override fun exchangeCode(request: OAuthCodeExchangeRequest): ProviderTokenSet {
        if (request.tokenUri.isBlank()) {
            throw ServiceUnavailableException("TEAMSNAP_NOT_CONFIGURED", "TeamSnap token endpoint is not configured yet.")
        }
        val response =
            try {
                restClient
                    .post()
                    .uri(request.tokenUri)
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .body(TeamSnapTokenExchangeRequest(clientId = request.clientId, clientSecret = request.clientSecret, code = request.code, redirectUri = request.redirectUri))
                    .retrieve()
                    .body(TeamSnapTokenResponse::class.java)
            } catch (ex: RestClientException) {
                log.warn("TeamSnap token exchange failed: {}", ex.message)
                throw ServiceUnavailableException("TEAMSNAP_TOKEN_EXCHANGE_FAILED", "TeamSnap did not accept the authorization.")
            }
        val accessToken =
            response?.accessToken ?: throw ServiceUnavailableException(
                "TEAMSNAP_TOKEN_EXCHANGE_FAILED",
                "TeamSnap did not return an access token.",
            )
        return ProviderTokenSet(
            accessToken = accessToken,
            refreshToken = response.refreshToken,
            expiresAt = response.expiresIn?.let { java.time.Instant.now().plusSeconds(it) },
            grantedScopes = request.requestedScopes,
            externalAccountId = null,
            externalAccountName = null,
        )
    }

    override fun refresh(request: OAuthRefreshRequest): ProviderTokenSet {
        if (request.tokenUri.isBlank()) {
            throw ServiceUnavailableException("TEAMSNAP_NOT_CONFIGURED", "TeamSnap token endpoint is not configured yet.")
        }
        val response =
            try {
                restClient
                    .post()
                    .uri(request.tokenUri)
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .body(TeamSnapRefreshRequest(clientId = request.clientId, clientSecret = request.clientSecret, refreshToken = request.refreshToken))
                    .retrieve()
                    .body(TeamSnapTokenResponse::class.java)
            } catch (ex: RestClientException) {
                log.warn("TeamSnap token refresh failed: {}", ex.message)
                throw ServiceUnavailableException("TEAMSNAP_TOKEN_REFRESH_FAILED", "TeamSnap did not accept the refresh token.")
            }
        val accessToken =
            response?.accessToken ?: throw ServiceUnavailableException(
                "TEAMSNAP_TOKEN_REFRESH_FAILED",
                "TeamSnap did not return an access token.",
            )
        return ProviderTokenSet(
            accessToken = accessToken,
            refreshToken = response.refreshToken ?: request.refreshToken,
            expiresAt = response.expiresIn?.let { java.time.Instant.now().plusSeconds(it) },
            grantedScopes = request.currentScopes,
            externalAccountId = null,
            externalAccountName = null,
        )
    }

    /** No revoke endpoint was found documented for TeamSnap; nothing to call. Matches how DeterministicStubIntegrationAdapter also treats revoke as a no-op. */
    override fun revoke(request: OAuthRevokeRequest) = Unit

    /**
     * Least-destructive authenticated call available: list teams. Only the HTTP
     * status is used — response-body field shapes weren't independently verified
     * this pass (see class doc), so nothing here depends on them.
     */
    override fun checkHealth(
        provider: IntegrationProvider,
        accessToken: String,
    ): ProviderHealthResult =
        try {
            restClient
                .get()
                .uri("$TEAMSNAP_API_BASE_URL/teams")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .retrieve()
                .toBodilessEntity()
            ProviderHealthResult(healthy = true)
        } catch (ex: RestClientException) {
            ProviderHealthResult(healthy = false, errorCode = "TEAMSNAP_TOKEN_REJECTED", errorMessage = ex.message)
        }
}
