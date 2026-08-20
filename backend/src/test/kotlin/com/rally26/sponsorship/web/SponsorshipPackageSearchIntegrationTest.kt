package com.rally26.sponsorship.web

import com.rally26.common.web.CurrentUser
import com.rally26.identity.application.PasswordAuthenticationService
import com.rally26.identity.application.TokenService
import com.rally26.organization.application.OrganizationService
import com.rally26.organization.domain.OrganizationType
import com.rally26.sponsorship.application.SponsorshipPackageService
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
 * Repro/fix test for LAUNCH-READINESS.md LR-026: `GET .../sponsorship-packages/search`
 * (what `frontend/src/features/sponsorship/searchApi.ts`'s `useSponsorshipPackageSearch`
 * has always called to power the Sponsorships tab) had no backend mapping at all —
 * Spring matched the literal path segment "search" to the existing `{packageId}`
 * wildcard handler and threw trying to parse "search" as a UUID, a 500 on every
 * request. Same class of bug as LR-016/018/020/025.
 */
class SponsorshipPackageSearchIntegrationTest : AbstractIntegrationTest() {
    @LocalServerPort
    var port: Int = 0

    @Autowired
    lateinit var passwordAuthenticationService: PasswordAuthenticationService

    @Autowired
    lateinit var tokenService: TokenService

    @Autowired
    lateinit var organizationService: OrganizationService

    @Autowired
    lateinit var sponsorshipPackageService: SponsorshipPackageService

    private val httpClient: HttpClient = HttpClient.newHttpClient()

    private data class AuthedOrg(
        val token: String,
        val organizationId: UUID,
        val currentUser: CurrentUser,
    )

    private fun authedOrg(): AuthedOrg {
        val appUser =
            passwordAuthenticationService.register(
                "sponsorship-search-${System.nanoTime()}@example.com",
                "password1234",
                "Test Owner",
            )
        val currentUser = passwordAuthenticationService.toCurrentUser(appUser)
        val token = tokenService.issueAccessToken(currentUser.userId, appUser.email, appUser.displayName)
        val organization =
            organizationService.create(
                "Sponsorship Search Test Org",
                "sponsorship-search-org-${System.nanoTime()}",
                OrganizationType.RECREATIONAL_LEAGUE,
                currentUser,
            )
        activateClubPlan(organization.id)
        return AuthedOrg(token.accessToken, organization.id, currentUser)
    }

    private fun searchPackages(
        organizationId: UUID,
        token: String,
        query: String,
    ): HttpResponse<String> {
        val request =
            HttpRequest
                .newBuilder()
                .uri(URI.create("http://localhost:$port/api/v1/organizations/$organizationId/sponsorship-packages/search?$query"))
                .header("Authorization", "Bearer $token")
                .GET()
                .build()
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString())
    }

    @Test
    fun `search returns 200 with real packages instead of 500ing on the literal 'search' path segment`() {
        val org = authedOrg()
        sponsorshipPackageService.create(org.organizationId, "Gold Sponsor", null, 50000, "USD", null, false, null, null, org.currentUser)
        sponsorshipPackageService.create(org.organizationId, "Silver Sponsor", null, 25000, "USD", null, false, null, null, org.currentUser)

        val response = searchPackages(org.organizationId, org.token, "page=0&size=25&sort=NEWEST")

        assertEquals(200, response.statusCode(), "expected a real search response, not the routing-conflict 500: ${response.body()}")
        assertTrue(response.body().contains("Gold Sponsor"))
        assertTrue(response.body().contains("Silver Sponsor"))
        assertTrue(response.body().contains("\"confirmedCount\":0"))
    }

    @Test
    fun `search keyword matches package name`() {
        val org = authedOrg()
        sponsorshipPackageService.create(org.organizationId, "Gold Sponsor", null, 50000, "USD", null, false, null, null, org.currentUser)
        sponsorshipPackageService.create(org.organizationId, "Silver Sponsor", null, 25000, "USD", null, false, null, null, org.currentUser)

        val response = searchPackages(org.organizationId, org.token, "page=0&size=25&sort=NEWEST&q=gold")

        assertEquals(200, response.statusCode())
        assertTrue(response.body().contains("Gold Sponsor"))
        assertTrue(!response.body().contains("Silver Sponsor"))
    }
}
