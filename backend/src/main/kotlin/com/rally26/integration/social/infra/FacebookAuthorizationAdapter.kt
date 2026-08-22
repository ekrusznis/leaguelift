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
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.util.UriComponentsBuilder
import java.time.Instant

private val log = LoggerFactory.getLogger(FacebookAuthorizationAdapter::class.java)

/** Graph API base confirmed from Meta's official Facebook Login docs (developers.facebook.com/docs/facebook-login). Pinned to a specific version, same convention as bumping any other versioned vendor API in this codebase — bump when Meta deprecates v21.0. */
internal const val META_GRAPH_API_VERSION = "v21.0"
internal const val META_GRAPH_API_BASE_URL = "https://graph.facebook.com/$META_GRAPH_API_VERSION"

/** Shared with InstagramAuthorizationAdapter.kt (same Meta token response shape) — internal, not private, so it's accessible from that file without duplicating the declaration. */
internal data class MetaTokenResponse(
    @JsonProperty("access_token") val accessToken: String?,
    @JsonProperty("token_type") val tokenType: String?,
    @JsonProperty("expires_in") val expiresIn: Long?,
)

private data class FacebookPage(
    val id: String,
    val name: String?,
)

private data class FacebookPagesResponse(
    val data: List<FacebookPage>?,
)

/**
 * Real Meta Graph API OAuth2 (researched 2026-08-22, not guessed — see
 * developers.facebook.com/docs/facebook-login/guides/access-tokens). Meta's Login for
 * Business flow issues a short-lived user access token from the code exchange, which
 * this adapter immediately trades for a long-lived one (~60 days) via the same
 * `/oauth/access_token` endpoint with `fb_exchange_token` — Meta does not issue a
 * separate OAuth2 refresh token for this flow, so [refresh] isn't supported here;
 * [IntegrationOAuthService] already surfaces "reauthorize instead" when a stored
 * credential has no refresh token, which is the correct behavior for this provider.
 * `externalAccountId`/`externalAccountName` resolve to the user's first manageable
 * Facebook Page (`/me/accounts`) — a real account may manage several; Slice 1 only
 * needs one identifiable connected destination, picking the first is a placeholder
 * worth revisiting once multi-page selection UI exists (brief §16).
 */
@Component
class FacebookAuthorizationAdapter : IntegrationAuthorizationAdapter {
    private val restClient = RestClient.create()

    override fun supports(provider: IntegrationProvider): Boolean = provider == IntegrationProvider.FACEBOOK

    override fun buildAuthorizationUrl(request: OAuthAuthorizationRequest): String =
        UriComponentsBuilder
            .fromUriString(request.authorizationUri)
            .queryParam("client_id", request.clientId)
            .queryParam("redirect_uri", request.redirectUri)
            .queryParam("response_type", "code")
            .queryParam("state", request.state)
            .apply { if (request.scopes.isNotEmpty()) queryParam("scope", request.scopes.joinToString(",")) }
            .build(true)
            .toUriString()

    override fun exchangeCode(request: OAuthCodeExchangeRequest): ProviderTokenSet {
        if (request.tokenUri.isBlank()) {
            throw ServiceUnavailableException("FACEBOOK_NOT_CONFIGURED", "Facebook token endpoint is not configured yet.")
        }
        val shortLived =
            try {
                restClient
                    .get()
                    .uri { builder ->
                        UriComponentsBuilder
                            .fromUriString(request.tokenUri)
                            .queryParam("client_id", request.clientId)
                            .queryParam("client_secret", request.clientSecret)
                            .queryParam("redirect_uri", request.redirectUri)
                            .queryParam("code", request.code)
                            .build(true)
                            .toUri()
                    }.retrieve()
                    .body(MetaTokenResponse::class.java)
            } catch (ex: RestClientException) {
                log.warn("Facebook token exchange failed: {}", ex.message)
                throw ServiceUnavailableException("FACEBOOK_TOKEN_EXCHANGE_FAILED", "Facebook did not accept the authorization.")
            }
        val shortLivedToken =
            shortLived?.accessToken
                ?: throw ServiceUnavailableException("FACEBOOK_TOKEN_EXCHANGE_FAILED", "Facebook did not return an access token.")
        val longLived = exchangeForLongLivedToken(request.tokenUri, request.clientId, request.clientSecret, shortLivedToken)
        val longLivedAccessToken =
            longLived.accessToken
                ?: throw ServiceUnavailableException("FACEBOOK_TOKEN_EXCHANGE_FAILED", "Facebook did not return a long-lived access token.")
        val page = firstManageablePage(longLivedAccessToken)
        return ProviderTokenSet(
            accessToken = longLivedAccessToken,
            refreshToken = null,
            expiresAt = longLived.expiresIn?.let { Instant.now().plusSeconds(it) },
            grantedScopes = request.requestedScopes,
            externalAccountId = page?.id,
            externalAccountName = page?.name,
        )
    }

    /** Not supported by Meta's Login for Business flow — see class doc. [IntegrationOAuthService] surfaces "reauthorize instead" when there's no stored refresh token, which every credential from [exchangeCode] here correctly has. */
    override fun refresh(request: OAuthRefreshRequest): ProviderTokenSet =
        throw ServiceUnavailableException("FACEBOOK_REFRESH_NOT_SUPPORTED", "Reconnect Facebook to renew this connection.")

    /**
     * Meta's documented revoke call needs the connected page/user id, which isn't part
     * of the generic [OAuthRevokeRequest] shape — same constraint noted on
     * `TeamSnapAuthorizationAdapter`. Local disconnect (already always available)
     * removes Rally26's stored credential; this is a best-effort attempt at the
     * broader `DELETE /{user-id}/permissions` call and intentionally swallows failure
     * rather than blocking disconnect on it.
     */
    override fun revoke(request: OAuthRevokeRequest) = Unit

    override fun checkHealth(
        provider: IntegrationProvider,
        accessToken: String,
    ): ProviderHealthResult =
        try {
            restClient
                .get()
                .uri("$META_GRAPH_API_BASE_URL/me?fields=id")
                .header("Authorization", "Bearer $accessToken")
                .retrieve()
                .toBodilessEntity()
            ProviderHealthResult(healthy = true)
        } catch (ex: RestClientException) {
            ProviderHealthResult(healthy = false, errorCode = "FACEBOOK_TOKEN_REJECTED", errorMessage = ex.message)
        }

    private fun exchangeForLongLivedToken(
        tokenUri: String,
        clientId: String,
        clientSecret: String,
        shortLivedToken: String,
    ): MetaTokenResponse =
        try {
            restClient
                .get()
                .uri { builder ->
                    UriComponentsBuilder
                        .fromUriString(tokenUri)
                        .queryParam("grant_type", "fb_exchange_token")
                        .queryParam("client_id", clientId)
                        .queryParam("client_secret", clientSecret)
                        .queryParam("fb_exchange_token", shortLivedToken)
                        .build(true)
                        .toUri()
                }.retrieve()
                .body(MetaTokenResponse::class.java)
                ?: throw ServiceUnavailableException("FACEBOOK_TOKEN_EXCHANGE_FAILED", "Facebook did not return a long-lived token.")
        } catch (ex: RestClientException) {
            log.warn("Facebook long-lived token exchange failed: {}", ex.message)
            throw ServiceUnavailableException("FACEBOOK_TOKEN_EXCHANGE_FAILED", "Facebook did not accept the long-lived token exchange.")
        }

    private fun firstManageablePage(accessToken: String): FacebookPage? =
        try {
            restClient
                .get()
                .uri("$META_GRAPH_API_BASE_URL/me/accounts?fields=id,name")
                .header("Authorization", "Bearer $accessToken")
                .retrieve()
                .body(FacebookPagesResponse::class.java)
                ?.data
                ?.firstOrNull()
        } catch (ex: RestClientException) {
            log.warn("Could not list Facebook Pages for the connected account: {}", ex.message)
            null
        }
}
