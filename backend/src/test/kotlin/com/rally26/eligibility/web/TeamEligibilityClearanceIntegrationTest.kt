package com.rally26.eligibility.web

import com.rally26.common.web.CurrentUser
import com.rally26.identity.application.PasswordAuthenticationService
import com.rally26.identity.application.TokenService
import com.rally26.organization.application.OrganizationService
import com.rally26.organization.domain.OrganizationType
import com.rally26.team.application.TeamService
import com.rally26.team.domain.Sport
import com.rally26.testsupport.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.assertEquals

/**
 * Repro/fix test for LAUNCH-READINESS.md LR-006/LR-022: `GET .../teams/{teamId}/eligibility/clearance`
 * with no `status` query param (the only way the frontend ever calls it — there is no
 * status filter UI wired anywhere) 500'd on every single request. Root cause: the
 * repository bound an untyped `null` for the optional `:statusFilter` parameter
 * (`EligibilityClearanceRepository.listForTeam`), and Postgres's extended query
 * protocol can't infer an untyped null's SQL type — `ERROR: could not determine data
 * type of parameter $N`. Every sibling repository doing this same "optional filter"
 * pattern already casts explicitly (`AnnouncementRepository`, `FeeRepository`,
 * `ReportingRepository`, `ProfileCorrectionRepository`); this was the one outlier.
 * This is very likely the real root cause behind a chunk of this session's
 * "LR-006" intermittent-503/connection-poisoning symptoms on unrelated endpoints too —
 * a wire-protocol-level error on a pooled connection can leave it in a bad state for
 * whichever unrelated request Hikari hands it to next. Only a real-HTTP-against-real-
 * Postgres test catches this; a mocked repository test cannot.
 */
class TeamEligibilityClearanceIntegrationTest : AbstractIntegrationTest() {
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

    @Test
    fun `listing a team's eligibility clearance with no status filter returns 200, not 500`() {
        val appUser =
            passwordAuthenticationService.register(
                "eligibility-clearance-${System.nanoTime()}@example.com",
                "password1234",
                "Test Owner",
            )
        val currentUser: CurrentUser = passwordAuthenticationService.toCurrentUser(appUser)
        val token = tokenService.issueAccessToken(currentUser.userId, appUser.email, appUser.displayName)
        val organization =
            organizationService.create(
                "Eligibility Clearance Test Org",
                "eligibility-clearance-org-${System.nanoTime()}",
                OrganizationType.RECREATIONAL_LEAGUE,
                currentUser,
            )
        val team =
            teamService.create(
                organization.id,
                "Varsity Soccer",
                Sport.SOCCER,
                null,
                null,
                null,
                null,
                null,
                currentUser,
            )

        val request =
            HttpRequest
                .newBuilder()
                .uri(URI.create("http://localhost:$port/api/v1/organizations/${organization.id}/teams/${team.id}/eligibility/clearance"))
                .header("Authorization", "Bearer ${token.accessToken}")
                .GET()
                .build()
        val response: HttpResponse<String> = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

        assertEquals(200, response.statusCode(), "expected an empty clearance list, not a 500: ${response.body()}")
        assertEquals("[]", response.body())
    }
}
