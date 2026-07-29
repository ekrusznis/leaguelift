package com.leaguelift.dashboard.application

import com.leaguelift.audit.persistence.AuditEventRepository
import com.leaguelift.common.error.NotFoundException
import com.leaguelift.common.web.CurrentUser
import com.leaguelift.household.persistence.HouseholdRepository
import com.leaguelift.membership.application.MembershipService
import com.leaguelift.membership.domain.MembershipRole
import com.leaguelift.membership.domain.MembershipStatus
import com.leaguelift.membership.domain.OrganizationMembership
import com.leaguelift.organization.domain.Organization
import com.leaguelift.organization.domain.OrganizationStatus
import com.leaguelift.organization.domain.OrganizationType
import com.leaguelift.fee.domain.OrganizationFeeFinancialSummary
import com.leaguelift.fee.persistence.FeeRepository
import com.leaguelift.organization.persistence.OrganizationRepository
import com.leaguelift.participant.persistence.ParticipantRepository
import com.leaguelift.team.domain.Team
import com.leaguelift.team.domain.TeamStatus
import com.leaguelift.team.persistence.TeamRepository
import com.leaguelift.tournament.persistence.TournamentRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OwnerDashboardServiceTest {

	private val membershipService = mockk<MembershipService>()
	private val organizationRepository = mockk<OrganizationRepository>()
	private val teamRepository = mockk<TeamRepository>()
	private val householdRepository = mockk<HouseholdRepository>()
	private val participantRepository = mockk<ParticipantRepository>()
	private val tournamentRepository = mockk<TournamentRepository>()
	private val auditEventRepository = mockk<AuditEventRepository>()
	private val feeRepository = mockk<FeeRepository>()

	private val service = OwnerDashboardService(
		membershipService, organizationRepository, teamRepository,
		householdRepository, participantRepository, tournamentRepository, auditEventRepository, feeRepository,
	)

	private val orgId = UUID.randomUUID()
	private val currentUser = CurrentUser(UUID.randomUUID(), "owner@example.com", "Owner")

	@Test
	fun `getSummary requires active membership and returns real counts`() {
		every { membershipService.requireActiveMembership(orgId, currentUser) } returns membership()
		every { organizationRepository.findById(orgId) } returns organization()
		every { teamRepository.countAll(orgId) } returns 3
		every { participantRepository.countActiveForOrganization(orgId) } returns 12
		every { householdRepository.countAll(orgId) } returns 8
		every { tournamentRepository.countUpcoming(orgId) } returns 1

		val result = service.getSummary(orgId, currentUser)

		assertEquals("Riverside Youth Sports Club", result.organizationName)
		assertEquals(3, result.activeTeams)
		assertEquals(12, result.participants)
		verify(exactly = 1) { membershipService.requireActiveMembership(orgId, currentUser) }
	}

	@Test
	fun `getSummary throws NotFoundException when organization does not exist`() {
		every { membershipService.requireActiveMembership(orgId, currentUser) } returns membership()
		every { organizationRepository.findById(orgId) } returns null

		assertFailsWith<NotFoundException> {
			service.getSummary(orgId, currentUser)
		}
	}

	@Test
	fun `getFinancialOverview returns real fee figures but keeps fundraising demo-tagged`() {
		every { membershipService.requireActiveMembership(orgId, currentUser) } returns membership()
		every { feeRepository.getFinancialSummary(orgId) } returns OrganizationFeeFinancialSummary(
			feesAssignedMinor = 15000L, feesCollectedMinor = 5000L, outstandingMinor = 10000L,
		)

		val result = service.getFinancialOverview(orgId, currentUser)

		assertEquals(false, result.isFeesDemoData)
		assertEquals(true, result.isFundraisingDemoData)
		assertEquals(15000L, result.feesAssignedMinor)
		assertEquals(5000L, result.feesCollectedMinor)
		assertEquals(10000L, result.outstandingMinor)
		verify(exactly = 1) { membershipService.requireActiveMembership(orgId, currentUser) }
	}

	@Test
	fun `getTeamPerformance includes real participant counts and is tagged demo for fundraising`() {
		val team = Team(UUID.randomUUID(), orgId, "U12 Blue", "Soccer", "2025", TeamStatus.ACTIVE, null, Instant.now(), Instant.now())
		every { membershipService.requireActiveMembership(orgId, currentUser) } returns membership()
		every { teamRepository.findAll(orgId, 0, 10) } returns listOf(team)
		every { participantRepository.countActiveForTeam(team.id, orgId) } returns 5

		val result = service.getTeamPerformance(orgId, currentUser)

		assertEquals(1, result.size)
		assertEquals(5, result.first().participants)
		assertEquals(true, result.first().isFundraisingDemoData)
	}

	@Test
	fun `getRecentActivity maps real audit events`() {
		every { membershipService.requireActiveMembership(orgId, currentUser) } returns membership()
		every { auditEventRepository.listRecentForOrganization(orgId, 10) } returns emptyList()

		val result = service.getRecentActivity(orgId, currentUser)

		assertEquals(0, result.size)
	}

	private fun organization() = Organization(
		id = orgId,
		name = "Riverside Youth Sports Club",
		slug = "riverside-youth-sports-club",
		organizationType = OrganizationType.TRAVEL_CLUB,
		status = OrganizationStatus.ACTIVE,
		sports = emptyList(),
		contactEmail = null,
		contactPhone = null,
		createdAt = Instant.now(),
		updatedAt = Instant.now(),
	)

	private fun membership() = OrganizationMembership(
		id = UUID.randomUUID(),
		organizationId = orgId,
		userId = currentUser.userId,
		role = MembershipRole.OWNER,
		status = MembershipStatus.ACTIVE,
		createdAt = Instant.now(),
		updatedAt = Instant.now(),
	)
}
