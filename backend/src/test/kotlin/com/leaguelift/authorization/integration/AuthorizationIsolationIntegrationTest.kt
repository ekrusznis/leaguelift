package com.leaguelift.authorization.integration

import com.leaguelift.authorization.application.AuthorizationService
import com.leaguelift.authorization.domain.Capabilities
import com.leaguelift.authorization.domain.ResourceRole
import com.leaguelift.common.error.ForbiddenException
import com.leaguelift.common.web.CurrentUser
import com.leaguelift.dashboard.application.CoachDashboardService
import com.leaguelift.identity.application.PasswordAuthenticationService
import com.leaguelift.organization.application.OrganizationService
import com.leaguelift.organization.domain.OrganizationType
import com.leaguelift.team.application.TeamService
import com.leaguelift.testsupport.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Exercises DESIGN-DOC.md section 18.1's "team admin cannot access an unrelated team"
 * and "a coach cannot edit another team's event"-adjacent critical scenarios end-to-end
 * against a real Postgres instance, for the new capability model (Phase 7/ADR-020).
 *
 * Requires Docker (Testcontainers).
 */
class AuthorizationIsolationIntegrationTest : AbstractIntegrationTest() {

	@Autowired lateinit var passwordAuthenticationService: PasswordAuthenticationService
	@Autowired lateinit var organizationService: OrganizationService
	@Autowired lateinit var teamService: TeamService
	@Autowired lateinit var authorizationService: AuthorizationService
	@Autowired lateinit var coachDashboardService: CoachDashboardService

	private fun registerUser(prefix: String) =
		passwordAuthenticationService.toCurrentUser(
			passwordAuthenticationService.register("$prefix-${System.nanoTime()}@example.com", "password1234", prefix),
		)

	@Test
	fun `a coach assigned to one team cannot see or manage another team in the same organization`() {
		val owner = registerUser("owner")
		val coachA = registerUser("coachA")
		val coachB = registerUser("coachB")

		val organization = organizationService.create("Riverside Youth Sports", "riverside-${System.nanoTime()}", OrganizationType.TRAVEL_CLUB, owner)
		val teamA = teamService.create(organization.id, "Varsity Soccer", "Soccer", "2024", null, owner)
		val teamB = teamService.create(organization.id, "JV Basketball", "Basketball", "2024", null, owner)

		authorizationService.grantTeamRole(organization.id, teamA.id, coachA.userId, ResourceRole.TEAM_MANAGER, owner)

		// Coach A can view and manage their own team...
		assertTrue(authorizationService.hasTeamCapability(organization.id, teamA.id, coachA, Capabilities.TEAM_VIEW))
		assertTrue(authorizationService.hasTeamCapability(organization.id, teamA.id, coachA, Capabilities.TEAM_ROSTER_MANAGE))
		// ...but not the other team.
		assertTrue(!authorizationService.hasTeamCapability(organization.id, teamB.id, coachA, Capabilities.TEAM_VIEW))

		val coachATeams = coachDashboardService.getTeams(organization.id, coachA)
		assertEquals(listOf(teamA.id), coachATeams.map { it.teamId })

		// Coach B has no grant at all in this organization.
		assertFailsWith<ForbiddenException> {
			coachDashboardService.getTeams(organization.id, coachB)
		}
	}

	@Test
	fun `an organization owner sees every team without an explicit grant, an outsider sees none`() {
		val owner = registerUser("owner2")
		val outsider = registerUser("outsider2")

		val organization = organizationService.create("Lakeside FC", "lakeside-${System.nanoTime()}", OrganizationType.RECREATIONAL_LEAGUE, owner)
		val team = teamService.create(organization.id, "U10 Blue", "Soccer", "2024", null, owner)

		val ownerTeams = coachDashboardService.getTeams(organization.id, owner)
		assertEquals(listOf(team.id), ownerTeams.map { it.teamId })

		assertFailsWith<ForbiddenException> {
			coachDashboardService.getTeams(organization.id, outsider)
		}
	}

	@Test
	fun `a platform administrator can access any organization's team data without a membership or grant`() {
		val owner = registerUser("owner3")
		val organization = organizationService.create("Metro Baseball", "metro-${System.nanoTime()}", OrganizationType.RECREATIONAL_LEAGUE, owner)
		val team = teamService.create(organization.id, "12U AAA", "Baseball", "2024", null, owner)

		val platformAdminIdentity = registerUser("platform")
		val platformAdmin = CurrentUser(platformAdminIdentity.userId, platformAdminIdentity.email, platformAdminIdentity.displayName, platformAdministrator = true)

		val teams = coachDashboardService.getTeams(organization.id, platformAdmin)
		assertEquals(listOf(team.id), teams.map { it.teamId })
	}
}
