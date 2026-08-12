package com.rally26.integration.sportsdata.infra

import com.rally26.common.error.ServiceUnavailableException
import com.rally26.integration.core.application.OAuthAuthorizationRequest
import com.rally26.integration.core.application.OAuthCodeExchangeRequest
import com.rally26.integration.core.application.OAuthRefreshRequest
import com.rally26.integration.core.domain.IntegrationProvider
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TeamSnapAuthorizationAdapterTest {
    private val adapter = TeamSnapAuthorizationAdapter()

    @Test
    fun `only supports TeamSnap`() {
        assertTrue(adapter.supports(IntegrationProvider.TEAMSNAP))
        assertFalse(adapter.supports(IntegrationProvider.SPORTSENGINE))
    }

    @Test
    fun `authorization url is a standard OAuth2 authorization-code request`() {
        val url =
            adapter.buildAuthorizationUrl(
                OAuthAuthorizationRequest(
                    provider = IntegrationProvider.TEAMSNAP,
                    clientId = "client-456",
                    authorizationUri = "https://auth.teamsnap.com/oauth/authorize",
                    redirectUri = "https://api.rally26.com/api/v1/integrations/oauth/teamsnap/callback",
                    state = "state-xyz",
                    codeChallenge = "unused",
                    scopes = listOf("read"),
                ),
            )

        assertTrue(url.startsWith("https://auth.teamsnap.com/oauth/authorize?"))
        assertTrue(url.contains("client_id=client-456"))
        assertTrue(url.contains("response_type=code"))
        assertTrue(url.contains("state=state-xyz"))
        assertTrue(url.contains("scope=read"))
    }

    @Test
    fun `authorization url omits the scope param entirely when no scopes are configured`() {
        val url =
            adapter.buildAuthorizationUrl(
                OAuthAuthorizationRequest(
                    provider = IntegrationProvider.TEAMSNAP,
                    clientId = "client-456",
                    authorizationUri = "https://auth.teamsnap.com/oauth/authorize",
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
                    provider = IntegrationProvider.TEAMSNAP,
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
    fun `refresh fails closed with a clear error when no token endpoint is configured`() {
        assertFailsWith<ServiceUnavailableException> {
            adapter.refresh(
                OAuthRefreshRequest(
                    provider = IntegrationProvider.TEAMSNAP,
                    clientId = "client-456",
                    clientSecret = "secret-456",
                    tokenUri = "",
                    refreshToken = "refresh-token",
                    currentScopes = emptyList(),
                ),
            )
        }
    }
}
