package com.rally26.integration.social.infra

import com.rally26.common.error.ServiceUnavailableException
import com.rally26.integration.core.application.OAuthAuthorizationRequest
import com.rally26.integration.core.application.OAuthCodeExchangeRequest
import com.rally26.integration.core.application.OAuthRefreshRequest
import com.rally26.integration.core.domain.IntegrationProvider
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FacebookAuthorizationAdapterTest {
    private val adapter = FacebookAuthorizationAdapter()

    @Test
    fun `only supports Facebook`() {
        assertTrue(adapter.supports(IntegrationProvider.FACEBOOK))
        assertFalse(adapter.supports(IntegrationProvider.INSTAGRAM))
    }

    @Test
    fun `authorization url is a standard OAuth2 authorization-code request`() {
        val url =
            adapter.buildAuthorizationUrl(
                OAuthAuthorizationRequest(
                    provider = IntegrationProvider.FACEBOOK,
                    clientId = "client-456",
                    authorizationUri = "https://www.facebook.com/v21.0/dialog/oauth",
                    redirectUri = "https://api.rally26.com/api/v1/integrations/oauth/facebook/callback",
                    state = "state-xyz",
                    codeChallenge = "unused",
                    scopes = listOf("pages_show_list"),
                ),
            )

        assertTrue(url.startsWith("https://www.facebook.com/v21.0/dialog/oauth?"))
        assertTrue(url.contains("client_id=client-456"))
        assertTrue(url.contains("response_type=code"))
        assertTrue(url.contains("state=state-xyz"))
        assertTrue(url.contains("scope=pages_show_list"))
    }

    @Test
    fun `authorization url omits the scope param entirely when no scopes are configured`() {
        val url =
            adapter.buildAuthorizationUrl(
                OAuthAuthorizationRequest(
                    provider = IntegrationProvider.FACEBOOK,
                    clientId = "client-456",
                    authorizationUri = "https://www.facebook.com/v21.0/dialog/oauth",
                    redirectUri = "https://api.rally26.com/callback",
                    state = "state-xyz",
                    codeChallenge = "unused",
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
                    provider = IntegrationProvider.FACEBOOK,
                    clientId = "client-456",
                    clientSecret = "secret-456",
                    tokenUri = "",
                    redirectUri = "https://api.rally26.com/callback",
                    code = "auth-code",
                    codeVerifier = "unused",
                    requestedScopes = emptyList(),
                ),
            )
        }
    }

    @Test
    fun `refresh is not supported since Meta issues no refresh token for this flow`() {
        assertFailsWith<ServiceUnavailableException> {
            adapter.refresh(
                OAuthRefreshRequest(
                    provider = IntegrationProvider.FACEBOOK,
                    clientId = "client-456",
                    clientSecret = "secret-456",
                    tokenUri = "https://graph.facebook.com/v21.0/oauth/access_token",
                    refreshToken = "unused",
                    currentScopes = emptyList(),
                ),
            )
        }
    }
}
