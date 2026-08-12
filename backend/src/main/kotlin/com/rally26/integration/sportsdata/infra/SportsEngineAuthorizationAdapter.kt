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
import java.time.Instant

private val log = LoggerFactory.getLogger(SportsEngineAuthorizationAdapter::class.java)

private data class SportsEngineTokenRequest(
    @JsonProperty("client_id") val clientId: String,
    @JsonProperty("client_secret") val clientSecret: String,
    @JsonProperty("grant_type") val grantType: String = "client_credentials",
)

private data class SportsEngineTokenResponse(
    @JsonProperty("access_token") val accessToken: String?,
    @JsonProperty("expires_in") val expiresIn: Long?,
)

/**
 * Real adapter against SportsEngine's documented "Organization Authorization Flow"
 * (help.sportsengine.com articles 8225304/8891727, researched 2026-08-12 — not
 * guessed). This is genuinely different from a standard OAuth2 authorization-code
 * flow, which is why it needs its own adapter rather than reusing generic PKCE
 * logic: the authorize step uses `response_type=organization_grant` and the
 * callback returns an `organization_grant` value (the approved organization's id)
 * instead of a `code` — `IntegrationController.callback` accepts that as an
 * alternate query param and forwards it through the existing generic `code`
 * plumbing unchanged. The token step is a plain `client_credentials` grant (not a
 * code exchange), since SportsEngine's own docs show no `code` parameter in that
 * request body. [OAuthAuthorizationRequest.authorizationUri]/[OAuthCodeExchangeRequest.tokenUri]
 * come from `rally26.integrations.providers.sportsengine.*` config (this
 * codebase's existing provider-neutral convention, `IntegrationProviderRuntimeProperties`)
 * rather than being hardcoded here, so real values only need a config change once
 * the founder has registered API Settings credentials — no code change.
 */
@Component
class SportsEngineAuthorizationAdapter : IntegrationAuthorizationAdapter {
    private val restClient = RestClient.create()

    override fun supports(provider: IntegrationProvider): Boolean = provider == IntegrationProvider.SPORTSENGINE

    override fun buildAuthorizationUrl(request: OAuthAuthorizationRequest): String =
        UriComponentsBuilder
            .fromUriString(request.authorizationUri)
            .queryParam("client_id", request.clientId)
            .queryParam("redirect_uri", request.redirectUri)
            .queryParam("scope", "organization_grant")
            .queryParam("response_type", "organization_grant")
            .queryParam("state", request.state)
            .build(true)
            .toUriString()

    /**
     * [OAuthCodeExchangeRequest.code] here actually carries the `organization_grant`
     * value the callback received (see class doc) — SportsEngine's token endpoint
     * doesn't take it as input at all; it's threaded through only as
     * [ProviderTokenSet.externalAccountId] so the connection records which
     * organization was actually granted.
     */
    override fun exchangeCode(request: OAuthCodeExchangeRequest): ProviderTokenSet {
        if (request.tokenUri.isBlank()) {
            throw ServiceUnavailableException(
                "SPORTSENGINE_NOT_CONFIGURED",
                "SportsEngine token endpoint is not configured yet.",
            )
        }
        val response =
            try {
                restClient
                    .post()
                    .uri(request.tokenUri)
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .body(SportsEngineTokenRequest(request.clientId, request.clientSecret))
                    .retrieve()
                    .body(SportsEngineTokenResponse::class.java)
            } catch (ex: RestClientException) {
                log.warn("SportsEngine token exchange failed: {}", ex.message)
                throw ServiceUnavailableException("SPORTSENGINE_TOKEN_EXCHANGE_FAILED", "SportsEngine did not accept the authorization.")
            }
        val accessToken =
            response?.accessToken ?: throw ServiceUnavailableException(
                "SPORTSENGINE_TOKEN_EXCHANGE_FAILED",
                "SportsEngine did not return an access token.",
            )
        return ProviderTokenSet(
            accessToken = accessToken,
            // Org-grant access tokens aren't refreshable per SportsEngine's docs — see [refresh].
            refreshToken = null,
            expiresAt = response.expiresIn?.let { Instant.now().plusSeconds(it) },
            grantedScopes = listOf("organization_grant"),
            externalAccountId = request.code,
            externalAccountName = null,
        )
    }

    /** SportsEngine's Organization Authorization Flow issues non-refreshable tokens (no refresh_token in the documented response) — re-authorization, not refresh, is the only documented path. */
    override fun refresh(request: OAuthRefreshRequest): ProviderTokenSet =
        throw ServiceUnavailableException(
            "SPORTSENGINE_TOKEN_NOT_REFRESHABLE",
            "SportsEngine organization-grant tokens can't be refreshed — reconnect this integration instead.",
        )

    /** No revoke endpoint is documented for this flow; nothing to call. Matches how DeterministicStubIntegrationAdapter also treats revoke as a no-op. */
    override fun revoke(request: OAuthRevokeRequest) = Unit

    /**
     * The one endpoint SportsEngine's docs give an exact, verified path for:
     * `GET https://user.sportsengine.com/oauth/me`. Only the HTTP status is used —
     * this pass didn't verify the response body's field shape, so nothing here
     * depends on it.
     */
    override fun checkHealth(
        provider: IntegrationProvider,
        accessToken: String,
    ): ProviderHealthResult =
        try {
            restClient
                .get()
                .uri("https://user.sportsengine.com/oauth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .retrieve()
                .toBodilessEntity()
            ProviderHealthResult(healthy = true)
        } catch (ex: RestClientException) {
            ProviderHealthResult(healthy = false, errorCode = "SPORTSENGINE_TOKEN_REJECTED", errorMessage = ex.message)
        }
}
