package com.rally26.household.web

import com.rally26.common.web.CurrentUser
import com.rally26.household.application.HouseholdService
import com.rally26.identity.application.PasswordAuthenticationService
import com.rally26.identity.application.TokenService
import com.rally26.organization.application.OrganizationService
import com.rally26.organization.domain.OrganizationType
import com.rally26.testsupport.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Repro/fix test for LAUNCH-READINESS.md LR-016 — see
 * `TeamSearchControllerIntegrationTest`'s own comment for the full explanation. Same
 * bug, same fix shape, for `GET .../households/search`.
 */
class HouseholdSearchControllerIntegrationTest : AbstractIntegrationTest() {
    @LocalServerPort
    var port: Int = 0

    @Autowired
    lateinit var passwordAuthenticationService: PasswordAuthenticationService

    @Autowired
    lateinit var tokenService: TokenService

    @Autowired
    lateinit var organizationService: OrganizationService

    @Autowired
    lateinit var householdService: HouseholdService

    private val httpClient: HttpClient = HttpClient.newHttpClient()

    private data class AuthedOrg(
        val token: String,
        val organizationId: UUID,
        val currentUser: CurrentUser,
    )

    private fun authedOrg(): AuthedOrg {
        val appUser =
            passwordAuthenticationService.register(
                "household-search-${System.nanoTime()}@example.com",
                "password1234",
                "Test Owner",
            )
        val currentUser = passwordAuthenticationService.toCurrentUser(appUser)
        val token = tokenService.issueAccessToken(currentUser.userId, appUser.email, appUser.displayName)
        val organization =
            organizationService.create(
                "Household Search Test Org",
                "household-search-org-${System.nanoTime()}",
                OrganizationType.RECREATIONAL_LEAGUE,
                currentUser,
            )
        return AuthedOrg(token.accessToken, organization.id, currentUser)
    }

    private fun searchHouseholds(
        organizationId: UUID,
        token: String,
        query: String,
    ): HttpResponse<String> {
        val request =
            HttpRequest
                .newBuilder()
                .uri(URI.create("http://localhost:$port/api/v1/organizations/$organizationId/households/search?$query"))
                .header("Authorization", "Bearer $token")
                .GET()
                .build()
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString())
    }

    @Test
    fun `search returns 200 with real results instead of 500ing on the literal 'search' path segment`() {
        val org = authedOrg()
        householdService.create(org.organizationId, "Johnson Family", "sarah@example.com", null, null, org.currentUser)
        householdService.create(org.organizationId, "Martinez Family", "carlos@example.com", null, null, org.currentUser)

        val response = searchHouseholds(org.organizationId, org.token, "page=0&size=25&sort=NAME_ASC")

        assertEquals(200, response.statusCode(), "expected a real search response, not the routing-conflict 500: ${response.body()}")
        assertTrue(response.body().contains("Johnson Family"))
        assertTrue(response.body().contains("Martinez Family"))
    }

    @Test
    fun `search keyword matches parent contact email`() {
        val org = authedOrg()
        householdService.create(org.organizationId, "Johnson Family", "sarah@example.com", null, null, org.currentUser)
        householdService.create(org.organizationId, "Martinez Family", "carlos@example.com", null, null, org.currentUser)

        val response = searchHouseholds(org.organizationId, org.token, "page=0&size=25&sort=NAME_ASC&q=sarah")

        assertEquals(200, response.statusCode())
        assertTrue(response.body().contains("Johnson Family"))
        assertTrue(!response.body().contains("Martinez Family"))
    }
}
