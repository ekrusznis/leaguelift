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

private val log = LoggerFactory.getLogger(InstagramAuthorizationAdapter::class.java)

private data class InstagramLinkedPage(
    val id: String,
    @JsonProperty("instagram_business_account") val instagramBusinessAccount: InstagramAccountRef?,
)

private data class InstagramAccountRef(
    val id: String,
)

private data class InstagramLinkedPagesResponse(
    val data: List<InstagramLinkedPage>?,
)

private data class InstagramProfile(
    val username: String?,
)

/**
 * Instagram Business/Creator accounts authenticate through the same Meta "Login for
 * Business" OAuth2 flow as [FacebookAuthorizationAdapter] — there is no separate
 * Instagram-specific authorize/token endpoint (confirmed via Meta's official
 * developers.facebook.com/docs/facebook-login docs, researched 2026-08-22). The
 * distinct step is resolving *which* Instagram account the connection is for: an
 * Instagram Business Account only exists linked to a Facebook Page, so this walks
 * `/me/accounts` (the pages the user manages) looking at each page's
 * `instagram_business_account` field for the first linked IG account, then reads its
 * username for display. A real account may manage multiple pages/IG accounts — same
 * "first one, revisit with real multi-account UI" placeholder as
 * [FacebookAuthorizationAdapter], see brief §16.
 */
@Component
class InstagramAuthorizationAdapter : IntegrationAuthorizationAdapter {
    private val restClient = RestClient.create()

    override fun supports(provider: IntegrationProvider): Boolean = provider == IntegrationProvider.INSTAGRAM

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
            throw ServiceUnavailableException("INSTAGRAM_NOT_CONFIGURED", "Instagram token endpoint is not configured yet.")
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
                log.warn("Instagram token exchange failed: {}", ex.message)
                throw ServiceUnavailableException("INSTAGRAM_TOKEN_EXCHANGE_FAILED", "Instagram did not accept the authorization.")
            }
        val shortLivedToken =
            shortLived?.accessToken
                ?: throw ServiceUnavailableException("INSTAGRAM_TOKEN_EXCHANGE_FAILED", "Instagram did not return an access token.")
        val longLived = exchangeForLongLivedToken(request.tokenUri, request.clientId, request.clientSecret, shortLivedToken)
        val longLivedAccessToken =
            longLived.accessToken
                ?: throw ServiceUnavailableException(
                    "INSTAGRAM_TOKEN_EXCHANGE_FAILED",
                    "Instagram did not return a long-lived access token.",
                )
        val igAccountId = firstLinkedInstagramAccountId(longLivedAccessToken)
        val username = igAccountId?.let { fetchUsername(it, longLivedAccessToken) }
        return ProviderTokenSet(
            accessToken = longLivedAccessToken,
            refreshToken = null,
            expiresAt = longLived.expiresIn?.let { Instant.now().plusSeconds(it) },
            grantedScopes = request.requestedScopes,
            externalAccountId = igAccountId,
            externalAccountName = username?.let { "@$it" },
        )
    }

    /** Not supported — same reasoning as [FacebookAuthorizationAdapter.refresh]. */
    override fun refresh(request: OAuthRefreshRequest): ProviderTokenSet =
        throw ServiceUnavailableException("INSTAGRAM_REFRESH_NOT_SUPPORTED", "Reconnect Instagram to renew this connection.")

    /** Same constraint as [FacebookAuthorizationAdapter.revoke] — Meta's revoke call needs an account id not present in the generic request shape. Local disconnect always remains available. */
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
            ProviderHealthResult(healthy = false, errorCode = "INSTAGRAM_TOKEN_REJECTED", errorMessage = ex.message)
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
                ?: throw ServiceUnavailableException("INSTAGRAM_TOKEN_EXCHANGE_FAILED", "Instagram did not return a long-lived token.")
        } catch (ex: RestClientException) {
            log.warn("Instagram long-lived token exchange failed: {}", ex.message)
            throw ServiceUnavailableException("INSTAGRAM_TOKEN_EXCHANGE_FAILED", "Instagram did not accept the long-lived token exchange.")
        }

    private fun firstLinkedInstagramAccountId(accessToken: String): String? =
        try {
            restClient
                .get()
                .uri("$META_GRAPH_API_BASE_URL/me/accounts?fields=id,instagram_business_account")
                .header("Authorization", "Bearer $accessToken")
                .retrieve()
                .body(InstagramLinkedPagesResponse::class.java)
                ?.data
                ?.firstNotNullOfOrNull { it.instagramBusinessAccount?.id }
        } catch (ex: RestClientException) {
            log.warn("Could not list linked Instagram Business accounts: {}", ex.message)
            null
        }

    private fun fetchUsername(
        igAccountId: String,
        accessToken: String,
    ): String? =
        try {
            restClient
                .get()
                .uri("$META_GRAPH_API_BASE_URL/$igAccountId?fields=username")
                .header("Authorization", "Bearer $accessToken")
                .retrieve()
                .body(InstagramProfile::class.java)
                ?.username
        } catch (ex: RestClientException) {
            log.warn("Could not fetch the connected Instagram account's username: {}", ex.message)
            null
        }
}
