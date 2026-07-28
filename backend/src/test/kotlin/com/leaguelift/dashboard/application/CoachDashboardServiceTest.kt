package com.leaguelift.dashboard.application

import com.leaguelift.common.web.CurrentUser
import com.leaguelift.fundraising.persistence.CampaignRepository
import com.leaguelift.membership.application.MembershipService
import com.leaguelift.membership.domain.MembershipRole
import com.leaguelift.membership.domain.MembershipStatus
import com.leaguelift.membership.domain.OrganizationMembership
import com.leaguelift.participant.persistence.ParticipantRepository
import com.leaguelift.publicpage.persistence.PublicPageRepository
import com.leaguelift.team.domain.Team
import com.leaguelift.team.domain.TeamStatus
import com.leaguelift.team.persistence.TeamRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CoachDashboardServiceTest {

	private val membershipService = mockk<MembershipService>()
	private val teamRepository = mockk<TeamRepository>()
	private val participantRepository = mockk<ParticipantRepository>()
	private val publicPageRepository = mockk<PublicPageRepository>()
	private val campaignRepository = mockk<CampaignRepository>()

	private val service = CoachDashboardService(membershipService, teamRepository, participantRepository, publicPageRepository, campaignRepository)

	private val orgId = UUID.randomUUID()
	private val currentUser = CurrentUser(UUID.randomUUID(), "coach@example.com", "Coach")

	@Test
	fun `getRosterSummary requires active membership and sums real participant counts across teams`() {
		val teamA = team("U12 Blue")
		val teamB = team("U14 Elite")
		every { membershipService.requireActiveMembership(orgId, currentUser) } returns membership()
		every { teamRepository.findAll(orgId, 0, 25) } returns listOf(teamA, teamB)
		every { participantRepository.countActiveForTeam(teamA.id, orgId) } returns 10
		every { participantRepository.countActiveForTeam(teamB.id, orgId) } returns 8

		val result = service.getRosterSummary(orgId, currentUser)

		assertEquals(18, result.athletes)
		assertEquals(true, result.isAttendanceDemoData)
		verify(exactly = 1) { membershipService.requireActiveMembership(orgId, currentUser) }
	}

	@Test
	fun `getTeamPageStatus returns null when the organization has no teams`() {
		every { membershipService.requireActiveMembership(orgId, currentUser) } returns membership()
		every { teamRepository.findAll(orgId, 0, 1) } returns emptyList()

		val result = service.getTeamPageStatus(orgId, currentUser)

		assertNull(result)
	}

	@Test
	fun `getFundraisingProgress returns null when there is no active campaign`() {
		every { membershipService.requireActiveMembership(orgId, currentUser) } returns membership()
		every { campaignRepository.findAll(orgId, 0, 25) } returns emptyList()

		val result = service.getFundraisingProgress(orgId, currentUser)

		assertNull(result)
	}

	@Test
	fun `getTeams returns real team identity and participant counts`() {
		val team = team("U12 Blue")
		every { membershipService.requireActiveMembership(orgId, currentUser) } returns membership()
		every { teamRepository.findAll(orgId, 0, 25) } returns listOf(team)
		every { participantRepository.countActiveForTeam(team.id, orgId) } returns 14

		val result = service.getTeams(orgId, currentUser)

		assertEquals(1, result.size)
		assertEquals(14, result.first().participants)
	}

	private fun team(name: String) = Team(UUID.randomUUID(), orgId, name, "Soccer", "2025", TeamStatus.ACTIVE, null, Instant.now(), Instant.now())

	private fun membership() = OrganizationMembership(
		id = UUID.randomUUID(),
		organizationId = orgId,
		userId = currentUser.userId,
		role = MembershipRole.TEAM_ADMINISTRATOR,
		status = MembershipStatus.ACTIVE,
		createdAt = Instant.now(),
		updatedAt = Instant.now(),
	)
}
