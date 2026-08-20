package com.rally26.team.web

import com.rally26.common.web.CurrentUser
import com.rally26.identity.application.PasswordAuthenticationService
import com.rally26.identity.application.TokenService
import com.rally26.organization.application.OrganizationService
import com.rally26.organization.domain.OrganizationType
import com.rally26.team.application.TeamService
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
 * Repro/fix test for LAUNCH-READINESS.md LR-016: `GET .../teams/search` (what
 * `frontend/src/features/teams/searchApi.ts` has always called) had no backend mapping
 * at all — Spring matched the literal path segment "search" to `TeamController.get`'s
 * `{teamId}` path variable and failed trying to parse it as a UUID, a 500 on every
 * single request. Exercises the real HTTP/routing pipeline end-to-end (unlike a
 * service-level unit test, which would not have caught a routing bug), the same
 * reasoning as `GlobalExceptionHandlerIntegrationTest`.
 */
class TeamSearchControllerIntegrationTest : AbstractIntegrationTest() {
    @LocalServerPort
    var port: Int = 0

    @Autowired
    lateinit var passwordAuthenticationService: PasswordAuthenticationService

    @Autowired
    lateinit var tokenService: TokenService

    @Autowired
    lateinit var organizationService: OrganizationService

    @Autowired
    lateinit var teamService: TeamService

    private val httpClient: HttpClient = HttpClient.newHttpClient()

    private data class AuthedOrg(
        val token: String,
        val organizationId: UUID,
        val currentUser: CurrentUser,
    )

    private fun authedOrg(): AuthedOrg {
        val appUser =
            passwordAuthenticationService.register(
                "team-search-${System.nanoTime()}@example.com",
                "password1234",
                "Test Owner",
            )
        val currentUser = passwordAuthenticationService.toCurrentUser(appUser)
        val token = tokenService.issueAccessToken(currentUser.userId, appUser.email, appUser.displayName)
        val organization =
            organizationService.create(
                "Team Search Test Org",
                "team-search-org-${System.nanoTime()}",
                OrganizationType.RECREATIONAL_LEAGUE,
                currentUser,
            )
        return AuthedOrg(token.accessToken, organization.id, currentUser)
    }

    private fun searchTeams(
        organizationId: UUID,
        token: String,
        query: String,
    ): HttpResponse<String> {
        val request =
            HttpRequest
                .newBuilder()
                .uri(URI.create("http://localhost:$port/api/v1/organizations/$organizationId/teams/search?$query"))
                .header("Authorization", "Bearer $token")
                .GET()
                .build()
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString())
    }

    @Test
    fun `search returns 200 with real results instead of 500ing on the literal 'search' path segment`() {
        val org = authedOrg()
        teamService.create(org.organizationId, "Varsity Soccer", "Soccer", "Fall 2026", null, null, null, null, org.currentUser)
        teamService.create(org.organizationId, "JV Basketball", "Basketball", "Fall 2026", null, null, null, null, org.currentUser)

        val response = searchTeams(org.organizationId, org.token, "page=0&size=25&sort=NAME_ASC")

        assertEquals(200, response.statusCode(), "expected a real search response, not the routing-conflict 500: ${response.body()}")
        assertTrue(response.body().contains("Varsity Soccer"))
        assertTrue(response.body().contains("JV Basketball"))
    }

    @Test
    fun `search keyword filters by sport`() {
        val org = authedOrg()
        teamService.create(org.organizationId, "Varsity Soccer", "Soccer", null, null, null, null, null, org.currentUser)
        teamService.create(org.organizationId, "JV Basketball", "Basketball", null, null, null, null, null, org.currentUser)

        val response = searchTeams(org.organizationId, org.token, "page=0&size=25&sort=NAME_ASC&q=basketball")

        assertEquals(200, response.statusCode())
        assertTrue(response.body().contains("JV Basketball"))
        assertTrue(!response.body().contains("Varsity Soccer"))
    }
}
