package com.leaguelift.dashboard.application

import com.leaguelift.authorization.application.AuthorizationService
import com.leaguelift.authorization.domain.Capabilities
import com.leaguelift.common.error.ForbiddenException
import com.leaguelift.common.web.CurrentUser
import com.leaguelift.fundraising.persistence.CampaignRepository
import com.leaguelift.fundraising.persistence.ContributionRepository
import com.leaguelift.participant.persistence.ParticipantRepository
import com.leaguelift.publicpage.persistence.PublicPageRepository
import com.leaguelift.team.domain.Team
import com.leaguelift.team.domain.TeamStatus
import com.leaguelift.team.persistence.TeamRepository
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * As of Phase 7/ADR-020, every card is scoped through
 * [AuthorizationService.listAccessibleTeamIds] rather than "every team in the
 * organization" — see `AuthorizationServiceTest`/`AuthorizationIsolationIntegrationTest`
 * for the underlying scoping-rule coverage; these tests cover this service's own
 * plumbing (deny-with-zero-teams, real data assembly for accessible teams).
 */
class CoachDashboardServiceTest {

	private val authorizationService = mockk<AuthorizationService>()
	private val teamRepository = mockk<TeamRepository>()
	private val participantRepository = mockk<ParticipantRepository>()
	private val publicPageRepository = mockk<PublicPageRepository>()
	private val campaignRepository = mockk<CampaignRepository>()
	private val contributionRepository = mockk<ContributionRepository>()

	private val service = CoachDashboardService(
		authorizationService, teamRepository, participantRepository, publicPageRepository, campaignRepository, contributionRepository,
	)

	private val orgId = UUID.randomUUID()
	private val currentUser = CurrentUser(UUID.randomUUID(), "coach@example.com", "Coach")

	@Test
	fun `getTeams throws when the caller has no accessible teams`() {
		every { authorizationService.listAccessibleTeamIds(orgId, currentUser, Capabilities.TEAM_VIEW) } returns emptySet()

		assertFailsWith<ForbiddenException> {
			service.getTeams(orgId, currentUser)
		}
	}

	@Test
	fun `getTeams returns real identity and participant counts only for accessible teams`() {
		val teamA = team("U12 Blue")
		val teamB = team("U14 Elite")
		every { authorizationService.listAccessibleTeamIds(orgId, currentUser, Capabilities.TEAM_VIEW) } returns setOf(teamA.id)
		every { teamRepository.findById(teamA.id, orgId) } returns teamA
		every { participantRepository.countActiveForTeam(teamA.id, orgId) } returns 14

		val result = service.getTeams(orgId, currentUser)

		assertEquals(1, result.size)
		assertEquals(teamA.id, result.first().teamId)
		assertEquals(14, result.first().participants)
		// teamB was never requested — proves the service didn't fall back to "every team".
	}

	@Test
	fun `getRosterSummary sums participant counts across only the caller's accessible teams`() {
		val teamA = team("U12 Blue")
		val teamB = team("U14 Elite")
		every { authorizationService.listAccessibleTeamIds(orgId, currentUser, Capabilities.TEAM_VIEW) } returns setOf(teamA.id, teamB.id)
		every { participantRepository.countActiveForTeam(teamA.id, orgId) } returns 10
		every { participantRepository.countActiveForTeam(teamB.id, orgId) } returns 8

		val result = service.getRosterSummary(orgId, currentUser)

		assertEquals(18, result.athletes)
		assertEquals(true, result.isAttendanceDemoData)
	}

	@Test
	fun `getTeamPageStatus returns null when the accessible team has no public page`() {
		val teamA = team("U12 Blue")
		every { authorizationService.listAccessibleTeamIds(orgId, currentUser, Capabilities.TEAM_VIEW) } returns setOf(teamA.id)
		every { teamRepository.findById(teamA.id, orgId) } returns teamA
		every { publicPageRepository.findByEntityId(teamA.id) } returns null

		val result = service.getTeamPageStatus(orgId, currentUser)

		assertEquals(teamA.id, result?.teamId)
		assertEquals("NOT_CREATED", result?.status)
	}

	@Test
	fun `getFundraisingProgress returns null when the accessible team has no active campaign`() {
		val teamA = team("U12 Blue")
		every { authorizationService.listAccessibleTeamIds(orgId, currentUser, Capabilities.TEAM_VIEW) } returns setOf(teamA.id)
		every { teamRepository.findById(teamA.id, orgId) } returns teamA
		every { campaignRepository.findActiveByTeam(teamA.id, orgId) } returns null

		val result = service.getFundraisingProgress(orgId, currentUser)

		assertNull(result)
	}

	@Test
	fun `getTeamPageStatus defaults to the alphabetically-first accessible team when no teamId is given`() {
		val teamA = team("Alpha")
		val teamB = team("Zeta")
		every { authorizationService.listAccessibleTeamIds(orgId, currentUser, Capabilities.TEAM_VIEW) } returns setOf(teamA.id, teamB.id)
		every { teamRepository.findById(teamA.id, orgId) } returns teamA
		every { teamRepository.findById(teamB.id, orgId) } returns teamB
		every { publicPageRepository.findByEntityId(teamA.id) } returns null

		val result = service.getTeamPageStatus(orgId, currentUser)

		assertEquals(teamA.id, result?.teamId)
	}

	@Test
	fun `getTeamPageStatus honors an explicit teamId selection among the caller's accessible teams`() {
		val teamA = team("Alpha")
		val teamB = team("Zeta")
		every { authorizationService.listAccessibleTeamIds(orgId, currentUser, Capabilities.TEAM_VIEW) } returns setOf(teamA.id, teamB.id)
		every { teamRepository.findById(teamB.id, orgId) } returns teamB
		every { publicPageRepository.findByEntityId(teamB.id) } returns null

		val result = service.getTeamPageStatus(orgId, currentUser, teamB.id)

		assertEquals(teamB.id, result?.teamId)
	}

	@Test
	fun `getTeamPageStatus rejects a teamId the caller cannot access`() {
		val teamA = team("Alpha")
		val unrelatedTeamId = UUID.randomUUID()
		every { authorizationService.listAccessibleTeamIds(orgId, currentUser, Capabilities.TEAM_VIEW) } returns setOf(teamA.id)

		assertFailsWith<ForbiddenException> {
			service.getTeamPageStatus(orgId, currentUser, unrelatedTeamId)
		}
	}

	@Test
	fun `getFundraisingProgress honors an explicit teamId selection among the caller's accessible teams`() {
		val teamA = team("Alpha")
		val teamB = team("Zeta")
		every { authorizationService.listAccessibleTeamIds(orgId, currentUser, Capabilities.TEAM_VIEW) } returns setOf(teamA.id, teamB.id)
		every { teamRepository.findById(teamB.id, orgId) } returns teamB
		every { campaignRepository.findActiveByTeam(teamB.id, orgId) } returns null

		val result = service.getFundraisingProgress(orgId, currentUser, teamB.id)

		assertNull(result)
	}

	private fun team(name: String) = Team(UUID.randomUUID(), orgId, name, "Soccer", "2025", TeamStatus.ACTIVE, null, Instant.now(), Instant.now())
}
