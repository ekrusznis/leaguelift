package com.rally26.integration.sportsdata.infra

import com.rally26.common.error.ServiceUnavailableException
import com.rally26.integration.core.application.OAuthAuthorizationRequest
import com.rally26.integration.core.application.OAuthCodeExchangeRequest
import com.rally26.integration.core.application.OAuthRefreshRequest
import com.rally26.integration.core.domain.IntegrationProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SportsEngineAuthorizationAdapterTest {
    private val adapter = SportsEngineAuthorizationAdapter()

    @Test
    fun `only supports SportsEngine`() {
        assertTrue(adapter.supports(IntegrationProvider.SPORTSENGINE))
        assertFalse(adapter.supports(IntegrationProvider.TEAMSNAP))
    }

    @Test
    fun `authorization url uses the org-grant flow, not standard PKCE code`() {
        val url =
            adapter.buildAuthorizationUrl(
                OAuthAuthorizationRequest(
                    provider = IntegrationProvider.SPORTSENGINE,
                    clientId = "client-123",
                    authorizationUri = "https://user.sportsengine.com/oauth/authorize",
                    redirectUri = "https://api.rally26.com/api/v1/integrations/oauth/sportsengine/callback",
                    state = "state-abc",
                    codeChallenge = "unused-for-this-provider",
                    scopes = emptyList(),
                ),
            )

        assertTrue(url.startsWith("https://user.sportsengine.com/oauth/authorize?"))
        assertTrue(url.contains("client_id=client-123"))
        assertTrue(url.contains("scope=organization_grant"))
        assertTrue(url.contains("response_type=organization_grant"))
        assertTrue(url.contains("state=state-abc"))
        assertFalse(url.contains("code_challenge"), "SportsEngine's org-grant flow doesn't use PKCE")
    }

    @Test
    fun `exchangeCode fails closed with a clear error when no token endpoint is configured`() {
        assertFailsWith<ServiceUnavailableException> {
            adapter.exchangeCode(
                OAuthCodeExchangeRequest(
                    provider = IntegrationProvider.SPORTSENGINE,
                    clientId = "client-123",
                    clientSecret = "secret-123",
                    tokenUri = "",
                    redirectUri = "https://api.rally26.com/callback",
                    code = "org-grant-id",
                    codeVerifier = "unused",
                    requestedScopes = emptyList(),
                ),
            )
        }
    }

    @Test
    fun `refresh always throws — SportsEngine org-grant tokens aren't refreshable`() {
        val error =
            assertFailsWith<ServiceUnavailableException> {
                adapter.refresh(
                    OAuthRefreshRequest(
                        provider = IntegrationProvider.SPORTSENGINE,
                        clientId = "client-123",
                        clientSecret = "secret-123",
                        tokenUri = "https://user.sportsengine.com/oauth/token",
                        refreshToken = "irrelevant",
                        currentScopes = emptyList(),
                    ),
                )
            }
        assertEquals("SPORTSENGINE_TOKEN_NOT_REFRESHABLE", error.code)
    }
}
