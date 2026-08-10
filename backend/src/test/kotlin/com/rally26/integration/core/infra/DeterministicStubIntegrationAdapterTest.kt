package com.rally26.integration.core.infra

import com.rally26.common.error.ServiceUnavailableException
import com.rally26.integration.core.application.OAuthAuthorizationRequest
import com.rally26.integration.core.application.OAuthCodeExchangeRequest
import com.rally26.integration.core.domain.IntegrationProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DeterministicStubIntegrationAdapterTest {
    private val adapter = DeterministicStubIntegrationAdapter()

    @Test
    fun `authorization url includes state and pkce challenge`() {
        val url =
            adapter.buildAuthorizationUrl(
                OAuthAuthorizationRequest(
                    IntegrationProvider.GOOGLE_CALENDAR,
                    "client",
                    "unused-in-stub",
                    "http://localhost/callback",
                    "state-value",
                    "challenge-value",
                    listOf("calendar.events"),
                ),
            )
        assertTrue(url.contains("state=state-value"))
        assertTrue(url.contains("code_challenge=challenge-value"))
        assertTrue(url.contains("code_challenge_method=S256"))
    }

    @Test
    fun `token exchange is deterministic and never marks an unsupported provider`() {
        val request =
            OAuthCodeExchangeRequest(
                IntegrationProvider.QUICKBOOKS_ONLINE,
                "client",
                "secret",
                "unused",
                "http://localhost/callback",
                "code",
                "verifier",
                listOf("accounting"),
            )
        val first = adapter.exchangeCode(request)
        val second = adapter.exchangeCode(request)
        assertEquals(first.accessToken, second.accessToken)
        assertEquals(listOf("accounting"), first.grantedScopes)
        assertTrue(!adapter.supports(IntegrationProvider.ICS_FEED))
    }

    @Test
    fun `stub can deterministically simulate a rate limit`() {
        val request =
            OAuthCodeExchangeRequest(
                IntegrationProvider.GOOGLE_CALENDAR,
                "client",
                "secret",
                "unused",
                "http://localhost/callback",
                "stub-rate-limit",
                "verifier",
                emptyList(),
            )
        assertFailsWith<ServiceUnavailableException> { adapter.exchangeCode(request) }
    }
}
