package com.rally26.participant.web

import com.rally26.common.web.CurrentUser
import com.rally26.household.application.HouseholdService
import com.rally26.identity.application.PasswordAuthenticationService
import com.rally26.identity.application.TokenService
import com.rally26.organization.application.OrganizationService
import com.rally26.organization.domain.OrganizationType
import com.rally26.participant.application.ParticipantService
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
 * Repro/fix test for LAUNCH-READINESS.md LR-019: `GET .../participants/{id}/teams`
 * used to return only a bare `teamId`, forcing callers to resolve the team name via
 * `GET .../teams` (org-staff-only) — which silently showed guardians a raw UUID
 * instead of a team name (`HouseholdDetailPage.tsx`'s `ParticipantTeamRow`). Verifies
 * the real SQL join against a real Postgres test container returns `teamName` directly.
 */
class ParticipantControllerIntegrationTest : AbstractIntegrationTest() {
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

    @Autowired
    lateinit var teamService: TeamService

    @Autowired
    lateinit var participantService: ParticipantService

    private val httpClient: HttpClient = HttpClient.newHttpClient()

    private data class AuthedOrg(
        val token: String,
        val organizationId: UUID,
        val currentUser: CurrentUser,
    )

    private fun authedOrg(): AuthedOrg {
        val appUser =
            passwordAuthenticationService.register(
                "participant-teams-${System.nanoTime()}@example.com",
                "password1234",
                "Test Owner",
            )
        val currentUser = passwordAuthenticationService.toCurrentUser(appUser)
        val token = tokenService.issueAccessToken(currentUser.userId, appUser.email, appUser.displayName)
        val organization =
            organizationService.create(
                "Participant Teams Test Org",
                "participant-teams-org-${System.nanoTime()}",
                OrganizationType.RECREATIONAL_LEAGUE,
                currentUser,
            )
        return AuthedOrg(token.accessToken, organization.id, currentUser)
    }

    @Test
    fun `listing a participant's teams returns the real team name, not just the id`() {
        val org = authedOrg()
        val household = householdService.create(org.organizationId, "Johnson Family", "sarah@example.com", null, null, org.currentUser)
        val participant = participantService.create(org.organizationId, household.id, "Maya", "Johnson", null, null, org.currentUser)
        val team =
            teamService.create(
                org.organizationId,
                "Varsity Soccer",
                Sport.SOCCER,
                "Fall 2026",
                null,
                null,
                null,
                null,
                org.currentUser,
            )
        participantService.assignToTeam(org.organizationId, participant.id, team.id, null, org.currentUser)

        val request =
            HttpRequest
                .newBuilder()
                .uri(URI.create("http://localhost:$port/api/v1/organizations/${org.organizationId}/participants/${participant.id}/teams"))
                .header("Authorization", "Bearer ${org.token}")
                .GET()
                .build()
        val response: HttpResponse<String> = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

        assertEquals(200, response.statusCode())
        assertTrue(
            response.body().contains("\"teamName\":\"Varsity Soccer\""),
            "expected the joined team name in the response: ${response.body()}",
        )
    }
}
