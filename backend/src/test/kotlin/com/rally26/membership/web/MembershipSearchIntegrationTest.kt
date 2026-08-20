package com.rally26.membership.web

import com.rally26.common.web.CurrentUser
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
 * Repro/fix test for LAUNCH-READINESS.md LR-027: `GET .../members/search` (what
 * `frontend/src/features/members/searchApi.ts`'s `useMemberSearch` has always called
 * to power the Members tab) had no backend mapping at all — only the plain, unfiltered
 * `GET .../members` existed. Every request 404'd, same class of bug as
 * LR-016/018/020/025/026. Found via a systematic audit of every frontend `/search` call
 * against backend routes, prompted by how often this exact bug class recurred this
 * session.
 */
class MembershipSearchIntegrationTest : AbstractIntegrationTest() {
    @LocalServerPort
    var port: Int = 0

    @Autowired
    lateinit var passwordAuthenticationService: PasswordAuthenticationService

    @Autowired
    lateinit var tokenService: TokenService

    @Autowired
    lateinit var organizationService: OrganizationService

    private val httpClient: HttpClient = HttpClient.newHttpClient()

    private fun searchMembers(
        organizationId: UUID,
        token: String,
        query: String,
    ): HttpResponse<String> {
        val request =
            HttpRequest
                .newBuilder()
                .uri(URI.create("http://localhost:$port/api/v1/organizations/$organizationId/members/search?$query"))
                .header("Authorization", "Bearer $token")
                .GET()
                .build()
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString())
    }

    @Test
    fun `search returns 200 with the real owner membership, not a 404`() {
        val appUser =
            passwordAuthenticationService.register("member-search-${System.nanoTime()}@example.com", "password1234", "Jamie Rivera")
        val currentUser: CurrentUser = passwordAuthenticationService.toCurrentUser(appUser)
        val token = tokenService.issueAccessToken(currentUser.userId, appUser.email, appUser.displayName)
        val organization =
            organizationService.create(
                "Member Search Test Org",
                "member-search-org-${System.nanoTime()}",
                OrganizationType.RECREATIONAL_LEAGUE,
                currentUser,
            )

        val response = searchMembers(organization.id, token.accessToken, "page=0&size=25&sort=NAME_ASC")

        assertEquals(200, response.statusCode(), "expected a real search response, not a 404: ${response.body()}")
        assertTrue(response.body().contains("Jamie Rivera"))
        assertTrue(response.body().contains("\"role\":\"OWNER\""))
    }

    @Test
    fun `search keyword with no match returns an empty page, not an error`() {
        val appUser =
            passwordAuthenticationService.register("member-search-kw-${System.nanoTime()}@example.com", "password1234", "Jamie Rivera")
        val currentUser: CurrentUser = passwordAuthenticationService.toCurrentUser(appUser)
        val token = tokenService.issueAccessToken(currentUser.userId, appUser.email, appUser.displayName)
        val organization =
            organizationService.create(
                "Member Search KW Test Org",
                "member-search-kw-org-${System.nanoTime()}",
                OrganizationType.RECREATIONAL_LEAGUE,
                currentUser,
            )

        val response = searchMembers(organization.id, token.accessToken, "page=0&size=25&sort=NAME_ASC&q=nonexistentname")

        assertEquals(200, response.statusCode())
        assertTrue(response.body().contains("\"totalElements\":0"))
    }
}
