package com.rally26.authorization.web

import com.rally26.authorization.application.AuthorizationService
import com.rally26.authorization.domain.ResourceRole
import com.rally26.common.web.CurrentUser
import com.rally26.identity.application.PasswordAuthenticationService
import com.rally26.identity.application.TokenService
import com.rally26.identity.domain.AppUser
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
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Repro/fix test for LAUNCH-READINESS.md LR-020: `GET .../teams/{teamId}/staff` (what
 * `TeamStaffList.tsx` has always called) had no backend mapping at all, 500ing for
 * every caller. The right fix wasn't just adding the route — the equivalent existing
 * endpoint (`/role-assignments`) is manager-only, but a coach should be able to see who
 * else coaches their own team, so this is TEAM_VIEW-gated instead.
 */
class TeamStaffIntegrationTest : AbstractIntegrationTest() {
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

    @Autowired
    lateinit var authorizationService: AuthorizationService

    private val httpClient: HttpClient = HttpClient.newHttpClient()

    private fun registerUser(label: String): Pair<AppUser, CurrentUser> {
        val appUser = passwordAuthenticationService.register("team-staff-$label-${System.nanoTime()}@example.com", "password1234", label)
        return appUser to passwordAuthenticationService.toCurrentUser(appUser)
    }

    private fun bearerFor(currentUser: CurrentUser): String =
        tokenService.issueAccessToken(currentUser.userId, currentUser.email, currentUser.displayName).accessToken

    private fun getStaff(
        organizationId: UUID,
        teamId: UUID,
        token: String,
    ): HttpResponse<String> {
        val request =
            HttpRequest
                .newBuilder()
                .uri(URI.create("http://localhost:$port/api/v1/organizations/$organizationId/teams/$teamId/staff"))
                .header("Authorization", "Bearer $token")
                .GET()
                .build()
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString())
    }

    @Test
    fun `a coach with a TEAM_VIEW-granting role can see the team's staff, an unrelated user cannot`() {
        val (_, owner) = registerUser("owner")
        val organization =
            organizationService.create(
                "Team Staff Test Org",
                "team-staff-org-${System.nanoTime()}",
                OrganizationType.RECREATIONAL_LEAGUE,
                owner,
            )
        val team = teamService.create(organization.id, "Varsity Soccer", Sport.SOCCER, null, null, null, null, null, owner)

        val (coachAppUser, coach) = registerUser("coach")
        authorizationService.grantTeamRole(organization.id, team.id, coach.userId, ResourceRole.COACH_READ, owner)

        val (_, outsider) = registerUser("outsider")

        val coachResponse = getStaff(organization.id, team.id, bearerFor(coach))
        assertEquals(200, coachResponse.statusCode())
        assertTrue(coachResponse.body().contains("\"roleLabel\":\"Coach (read-only)\""), coachResponse.body())
        assertTrue(coachResponse.body().contains(coachAppUser.displayName), coachResponse.body())

        val outsiderResponse = getStaff(organization.id, team.id, bearerFor(outsider))
        assertEquals(403, outsiderResponse.statusCode())
    }
}
