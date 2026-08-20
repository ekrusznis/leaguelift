package com.rally26.sponsorship.web

import com.rally26.common.web.CurrentUser
import com.rally26.identity.application.PasswordAuthenticationService
import com.rally26.identity.application.TokenService
import com.rally26.organization.application.OrganizationService
import com.rally26.organization.domain.OrganizationType
import com.rally26.sponsorship.application.SponsorshipPackageService
import com.rally26.sponsorship.persistence.SponsorRepository
import com.rally26.sponsorship.persistence.SponsorshipRepository
import com.rally26.testsupport.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Repro/fix test for LAUNCH-READINESS.md LR-028: `GET .../sponsorships/search` (what
 * `frontend/src/features/sponsorship/searchApi.ts`'s `useSponsorshipSearch` has always
 * called to power the "Review pending sponsorships" tab) had no backend mapping at all.
 * Found via a systematic audit of every frontend `/search` call against backend routes,
 * same class of bug as LR-016/018/020/025/026/027.
 */
class SponsorshipSearchIntegrationTest : AbstractIntegrationTest() {
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

    @Autowired
    lateinit var sponsorRepository: SponsorRepository

    @Autowired
    lateinit var sponsorshipRepository: SponsorshipRepository

    private val httpClient: HttpClient = HttpClient.newHttpClient()

    private data class AuthedOrg(
        val token: String,
        val organizationId: UUID,
        val currentUser: CurrentUser,
    )

    private fun authedOrg(): AuthedOrg {
        val appUser =
            passwordAuthenticationService.register(
                "sponsorship-full-search-${System.nanoTime()}@example.com",
                "password1234",
                "Test Owner",
            )
        val currentUser = passwordAuthenticationService.toCurrentUser(appUser)
        val token = tokenService.issueAccessToken(currentUser.userId, appUser.email, appUser.displayName)
        val organization =
            organizationService.create(
                "Sponsorship Full Search Test Org",
                "sponsorship-full-search-org-${System.nanoTime()}",
                OrganizationType.RECREATIONAL_LEAGUE,
                currentUser,
            )
        activateClubPlan(organization.id)
        return AuthedOrg(token.accessToken, organization.id, currentUser)
    }

    private fun searchSponsorships(
        organizationId: UUID,
        token: String,
        query: String,
    ): HttpResponse<String> {
        val request =
            HttpRequest
                .newBuilder()
                .uri(URI.create("http://localhost:$port/api/v1/organizations/$organizationId/sponsorships/search?$query"))
                .header("Authorization", "Bearer $token")
                .GET()
                .build()
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString())
    }

    @Test
    fun `search returns 200 with a confirmed sponsorship, not a 404`() {
        val org = authedOrg()
        val pkg =
            sponsorshipPackageService.create(
                org.organizationId,
                "Gold Sponsor",
                null,
                50000,
                "USD",
                null,
                false,
                null,
                null,
                org.currentUser,
            )
        val sponsor = sponsorRepository.insert(org.organizationId, "Acme Corp", "acme@example.com")
        val sponsorship = sponsorshipRepository.insertOfflinePending(org.organizationId, pkg.id, sponsor.id, 50000, "USD")
        sponsorshipRepository.markOfflineConfirmed(sponsorship.id, Instant.now())

        val response = searchSponsorships(org.organizationId, org.token, "page=0&size=25&sort=NEWEST")

        assertEquals(200, response.statusCode(), "expected a real search response, not a 404: ${response.body()}")
        assertTrue(response.body().contains("Acme Corp"))
        assertTrue(response.body().contains("Gold Sponsor"))
    }

    @Test
    fun `search keyword matches sponsor name`() {
        val org = authedOrg()
        val pkg =
            sponsorshipPackageService.create(
                org.organizationId,
                "Gold Sponsor",
                null,
                50000,
                "USD",
                null,
                false,
                null,
                null,
                org.currentUser,
            )
        val acme = sponsorRepository.insert(org.organizationId, "Acme Corp", "acme@example.com")
        val acmeSponsorship = sponsorshipRepository.insertOfflinePending(org.organizationId, pkg.id, acme.id, 50000, "USD")
        sponsorshipRepository.markOfflineConfirmed(acmeSponsorship.id, Instant.now())
        val globex = sponsorRepository.insert(org.organizationId, "Globex Inc", "globex@example.com")
        val globexSponsorship = sponsorshipRepository.insertOfflinePending(org.organizationId, pkg.id, globex.id, 50000, "USD")
        sponsorshipRepository.markOfflineConfirmed(globexSponsorship.id, Instant.now())

        val response = searchSponsorships(org.organizationId, org.token, "page=0&size=25&sort=NEWEST&q=acme")

        assertEquals(200, response.statusCode())
        assertTrue(response.body().contains("Acme Corp"))
        assertTrue(!response.body().contains("Globex Inc"))
    }
}
