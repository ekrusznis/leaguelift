package com.rally26.integration.social.infra

import com.rally26.common.error.ServiceUnavailableException
import com.rally26.integration.core.application.OAuthAuthorizationRequest
import com.rally26.integration.core.application.OAuthCodeExchangeRequest
import com.rally26.integration.core.application.OAuthRefreshRequest
import com.rally26.integration.core.application.OAuthRevokeRequest
import com.rally26.integration.core.domain.IntegrationProvider
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class XAuthorizationAdapterTest {
    private val adapter = XAuthorizationAdapter()

    @Test
    fun `only supports X`() {
        assertTrue(adapter.supports(IntegrationProvider.X))
        assertFalse(adapter.supports(IntegrationProvider.FACEBOOK))
    }

    @Test
    fun `authorization url is a PKCE OAuth2 authorization-code request`() {
        val url =
            adapter.buildAuthorizationUrl(
                OAuthAuthorizationRequest(
                    provider = IntegrationProvider.X,
                    clientId = "client-456",
                    authorizationUri = "https://x.com/i/oauth2/authorize",
                    redirectUri = "https://api.rally26.com/api/v1/integrations/oauth/x/callback",
                    state = "state-xyz",
                    codeChallenge = "challenge-abc",
                    scopes = listOf("tweet.write", "offline.access"),
                ),
            )

        assertTrue(url.startsWith("https://x.com/i/oauth2/authorize?"))
        assertTrue(url.contains("client_id=client-456"))
        assertTrue(url.contains("response_type=code"))
        assertTrue(url.contains("state=state-xyz"))
        assertTrue(url.contains("code_challenge=challenge-abc"))
        assertTrue(url.contains("code_challenge_method=S256"))
        assertTrue(url.contains("scope=tweet.write%20offline.access") || url.contains("scope=tweet.write+offline.access"))
    }

    @Test
    fun `authorization url omits the scope param entirely when no scopes are configured`() {
        val url =
            adapter.buildAuthorizationUrl(
                OAuthAuthorizationRequest(
                    provider = IntegrationProvider.X,
                    clientId = "client-456",
                    authorizationUri = "https://x.com/i/oauth2/authorize",
                    redirectUri = "https://api.rally26.com/callback",
                    state = "state-xyz",
                    codeChallenge = "challenge-abc",
                    scopes = emptyList(),
                ),
            )

        assertFalse(url.contains("scope="))
    }

    @Test
    fun `exchangeCode fails closed with a clear error when no token endpoint is configured`() {
        assertFailsWith<ServiceUnavailableException> {
            adapter.exchangeCode(
                OAuthCodeExchangeRequest(
                    provider = IntegrationProvider.X,
                    clientId = "client-456",
                    clientSecret = "secret-456",
                    tokenUri = "",
                    redirectUri = "https://api.rally26.com/callback",
                    code = "auth-code",
                    codeVerifier = "verifier-abc",
                    requestedScopes = emptyList(),
                ),
            )
        }
    }

    @Test
    fun `refresh fails closed with a clear error when no token endpoint is configured`() {
        assertFailsWith<ServiceUnavailableException> {
            adapter.refresh(
                OAuthRefreshRequest(
                    provider = IntegrationProvider.X,
                    clientId = "client-456",
                    clientSecret = "secret-456",
                    tokenUri = "",
                    refreshToken = "refresh-token",
                    currentScopes = emptyList(),
                ),
            )
        }
    }

    @Test
    fun `revoke is a no-op when no revocation endpoint is configured`() {
        adapter.revoke(
            OAuthRevokeRequest(
                provider = IntegrationProvider.X,
                clientId = "client-456",
                clientSecret = "secret-456",
                revocationUri = "",
                accessToken = "access-token",
                refreshToken = null,
            ),
        )
    }
}
