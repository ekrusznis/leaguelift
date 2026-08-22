package com.rally26.integration.social.infra

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
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.util.UriComponentsBuilder
import java.time.Instant
import java.util.Base64

private val log = LoggerFactory.getLogger(XAuthorizationAdapter::class.java)

/** X's real OAuth2/API v2 base, per X's official developer docs (docs.x.com/fundamentals/authentication/oauth-2-0), researched 2026-08-22. Shared with [XPublishingAdapter]. */
internal const val X_API_BASE_URL = "https://api.x.com/2"

private data class XTokenResponse(
    @JsonProperty("access_token") val accessToken: String?,
    @JsonProperty("refresh_token") val refreshToken: String?,
    @JsonProperty("expires_in") val expiresIn: Long?,
)

/**
 * Real X API v2 OAuth2 + mandatory PKCE (researched 2026-08-22, not guessed):
 * authorize at `x.com/i/oauth2/authorize`, token/refresh at
 * `api.x.com/2/oauth2/token`, both real per X's official developer docs. X issues a
 * real OAuth2 refresh token when `offline.access` is among the granted scopes, so
 * (unlike Meta) [refresh] is a real, supported call here. X requires confidential
 * clients (Rally26 holds a client secret) to authenticate token/revoke calls with
 * HTTP Basic auth (`client_id:client_secret`), not body params — this differs from
 * every other adapter in this codebase, which all use body/query params instead.
 * `externalAccountId`/`externalAccountName` aren't resolved during [exchangeCode]
 * this pass (would need an extra `GET /2/users/me` call) — [checkHealth] already
 * proves that call works; wiring it into the stored connection is a small follow-up,
 * not blocking Slice 1 while X credentials themselves don't exist yet. The revoke
 * endpoint path (`/2/oauth2/revoke`) matches X's documented token/revoke pairing
 * convention but wasn't independently confirmed against a real, live X app this pass
 * — spot-check before activation, same caveat every other adapter here carries for
 * whichever detail wasn't hands-on verified.
 */
@Component
class XAuthorizationAdapter : IntegrationAuthorizationAdapter {
    private val restClient = RestClient.create()

    override fun supports(provider: IntegrationProvider): Boolean = provider == IntegrationProvider.X

    override fun buildAuthorizationUrl(request: OAuthAuthorizationRequest): String =
        UriComponentsBuilder
            .fromUriString(request.authorizationUri)
            .queryParam("client_id", request.clientId)
            .queryParam("redirect_uri", request.redirectUri)
            .queryParam("response_type", "code")
            .queryParam("state", request.state)
            .queryParam("code_challenge", request.codeChallenge)
            .queryParam("code_challenge_method", "S256")
            .apply { if (request.scopes.isNotEmpty()) queryParam("scope", request.scopes.joinToString(" ")) }
            .build(false)
            .encode()
            .toUriString()

    override fun exchangeCode(request: OAuthCodeExchangeRequest): ProviderTokenSet {
        if (request.tokenUri.isBlank()) {
            throw ServiceUnavailableException("X_NOT_CONFIGURED", "X token endpoint is not configured yet.")
        }
        val body = LinkedMultiValueMap<String, String>()
        body.add("grant_type", "authorization_code")
        body.add("client_id", request.clientId)
        body.add("redirect_uri", request.redirectUri)
        body.add("code", request.code)
        body.add("code_verifier", request.codeVerifier)
        val response = postTokenRequest(request.tokenUri, request.clientId, request.clientSecret, body, "X_TOKEN_EXCHANGE_FAILED")
        return ProviderTokenSet(
            accessToken =
                response.accessToken ?: throw ServiceUnavailableException("X_TOKEN_EXCHANGE_FAILED", "X did not return an access token."),
            refreshToken = response.refreshToken,
            expiresAt = response.expiresIn?.let { Instant.now().plusSeconds(it) },
            grantedScopes = request.requestedScopes,
            externalAccountId = null,
            externalAccountName = null,
        )
    }

    override fun refresh(request: OAuthRefreshRequest): ProviderTokenSet {
        if (request.tokenUri.isBlank()) {
            throw ServiceUnavailableException("X_NOT_CONFIGURED", "X token endpoint is not configured yet.")
        }
        val body = LinkedMultiValueMap<String, String>()
        body.add("grant_type", "refresh_token")
        body.add("client_id", request.clientId)
        body.add("refresh_token", request.refreshToken)
        val response = postTokenRequest(request.tokenUri, request.clientId, request.clientSecret, body, "X_TOKEN_REFRESH_FAILED")
        return ProviderTokenSet(
            accessToken =
                response.accessToken ?: throw ServiceUnavailableException("X_TOKEN_REFRESH_FAILED", "X did not return an access token."),
            refreshToken = response.refreshToken ?: request.refreshToken,
            expiresAt = response.expiresIn?.let { Instant.now().plusSeconds(it) },
            grantedScopes = request.currentScopes,
            externalAccountId = null,
            externalAccountName = null,
        )
    }

    override fun revoke(request: OAuthRevokeRequest) {
        if (request.revocationUri.isBlank()) return
        val body = LinkedMultiValueMap<String, String>()
        body.add("token", request.accessToken)
        body.add("token_type_hint", "access_token")
        try {
            restClient
                .post()
                .uri(request.revocationUri)
                .headers { it.set(HttpHeaders.AUTHORIZATION, basicAuthHeader(request.clientId, request.clientSecret)) }
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(body)
                .retrieve()
                .toBodilessEntity()
        } catch (ex: RestClientException) {
            log.warn("X token revocation failed: {}", ex.message)
            throw ServiceUnavailableException("X_REVOCATION_FAILED", "X did not confirm credential revocation.")
        }
    }

    override fun checkHealth(
        provider: IntegrationProvider,
        accessToken: String,
    ): ProviderHealthResult =
        try {
            restClient
                .get()
                .uri("$X_API_BASE_URL/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .retrieve()
                .toBodilessEntity()
            ProviderHealthResult(healthy = true)
        } catch (ex: RestClientException) {
            ProviderHealthResult(healthy = false, errorCode = "X_TOKEN_REJECTED", errorMessage = ex.message)
        }

    private fun postTokenRequest(
        tokenUri: String,
        clientId: String,
        clientSecret: String,
        body: MultiValueMap<String, String>,
        errorCode: String,
    ): XTokenResponse =
        try {
            restClient
                .post()
                .uri(tokenUri)
                .headers { it.set(HttpHeaders.AUTHORIZATION, basicAuthHeader(clientId, clientSecret)) }
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(body)
                .retrieve()
                .body(XTokenResponse::class.java) ?: throw ServiceUnavailableException(errorCode, "X did not return a token response.")
        } catch (ex: RestClientException) {
            log.warn("X token request failed: {}", ex.message)
            throw ServiceUnavailableException(errorCode, "X did not accept the token request.")
        }

    private fun basicAuthHeader(
        clientId: String,
        clientSecret: String,
    ): String = "Basic " + Base64.getEncoder().encodeToString("$clientId:$clientSecret".toByteArray(Charsets.UTF_8))
}
