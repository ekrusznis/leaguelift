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
import com.leaguelift.fundraising.domain.Campaign
import com.leaguelift.fundraising.domain.CampaignStatus
import com.leaguelift.fundraising.domain.CampaignType
import com.leaguelift.fundraising.persistence.CampaignRepository
import com.leaguelift.fundraising.persistence.ContributionRepository
import com.leaguelift.ledger.application.PayoutSummary
import com.leaguelift.ledger.domain.LedgerDirection
import com.leaguelift.ledger.domain.LedgerEntryType
import com.leaguelift.ledger.persistence.LedgerEntryRepository
import com.leaguelift.organization.persistence.OrganizationRepository
import com.leaguelift.participant.persistence.ParticipantRepository
import com.leaguelift.payout.application.PayoutAccountService
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
	private val campaignRepository = mockk<CampaignRepository>()
	private val contributionRepository = mockk<ContributionRepository>()
	private val ledgerEntryRepository = mockk<LedgerEntryRepository>()
	private val payoutAccountService = mockk<PayoutAccountService>()

	private val service = OwnerDashboardService(
		membershipService, organizationRepository, teamRepository,
		householdRepository, participantRepository, tournamentRepository, auditEventRepository, feeRepository,
		campaignRepository, contributionRepository, ledgerEntryRepository, payoutAccountService,
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
	fun `getFinancialOverview returns real fee, fundraising, apparel, and payout figures`() {
		every { membershipService.requireActiveMembership(orgId, currentUser) } returns membership()
		every { feeRepository.getFinancialSummary(orgId) } returns OrganizationFeeFinancialSummary(
			feesAssignedMinor = 15000L, feesCollectedMinor = 5000L, outstandingMinor = 10000L,
		)
		every { contributionRepository.sumConfirmedByOrganization(orgId) } returns 42_00L
		every { ledgerEntryRepository.sumByOrganizationTypeAndDirection(orgId, LedgerEntryType.GROSS_SALE, LedgerDirection.CREDIT) } returns 18_900L
		every { payoutAccountService.getPayoutSummary(orgId, currentUser) } returns PayoutSummary(
			eligibleMinor = 20_000L, heldMinor = 5_000L, pendingDebitsMinor = 0L, netAvailableMinor = 15_000L,
		)

		val result = service.getFinancialOverview(orgId, currentUser)

		assertEquals(false, result.isFeesDemoData)
		assertEquals(false, result.isFundraisingDemoData)
		assertEquals(15000L, result.feesAssignedMinor)
		assertEquals(5000L, result.feesCollectedMinor)
		assertEquals(10000L, result.outstandingMinor)
		assertEquals(42_00L, result.fundraisingMinor)
		assertEquals(18_900L, result.apparelSalesMinor)
		assertEquals(15_000L, result.pendingPayoutMinor)
		verify(exactly = 1) { membershipService.requireActiveMembership(orgId, currentUser) }
	}

	@Test
	fun `getReportsSnapshot returns real dollar values with an honest zeroed trend`() {
		every { membershipService.requireActiveMembership(orgId, currentUser) } returns membership()
		every { feeRepository.getFinancialSummary(orgId) } returns OrganizationFeeFinancialSummary(
			feesAssignedMinor = 15000L, feesCollectedMinor = 5000L, outstandingMinor = 10000L,
		)
		every { contributionRepository.sumConfirmedByOrganization(orgId) } returns 42_00L
		every { ledgerEntryRepository.sumByOrganizationTypeAndDirection(orgId, LedgerEntryType.GROSS_SALE, LedgerDirection.CREDIT) } returns 18_900L

		val result = service.getReportsSnapshot(orgId, currentUser)

		assertEquals(3, result.size)
		assertEquals(true, result.none { it.isDemoData })
		assertEquals(5000L, result.first { it.label == "Fee Collection" }.valueMinor)
		assertEquals(42_00L, result.first { it.label == "Fundraising" }.valueMinor)
		assertEquals(18_900L, result.first { it.label == "Apparel Sales" }.valueMinor)
		assertEquals(true, result.all { it.trendPercent == 0.0 })
	}

	@Test
	fun `getTeamPerformance is tagged demo for fundraising when a team has no active campaign`() {
		val team = Team(UUID.randomUUID(), orgId, "U12 Blue", "Soccer", "2025", TeamStatus.ACTIVE, null, Instant.now(), Instant.now())
		every { membershipService.requireActiveMembership(orgId, currentUser) } returns membership()
		every { teamRepository.findAll(orgId, 0, 10) } returns listOf(team)
		every { participantRepository.countActiveForTeam(team.id, orgId) } returns 5
		every { campaignRepository.findActiveByTeam(team.id, orgId) } returns null

		val result = service.getTeamPerformance(orgId, currentUser)

		assertEquals(1, result.size)
		assertEquals(5, result.first().participants)
		assertEquals(true, result.first().isFundraisingDemoData)
		assertEquals(null, result.first().fundraisingRaisedMinor)
	}

	@Test
	fun `getTeamPerformance returns real fundraising figures when a team has an active campaign`() {
		val team = Team(UUID.randomUUID(), orgId, "U12 Blue", "Soccer", "2025", TeamStatus.ACTIVE, null, Instant.now(), Instant.now())
		val campaign = Campaign(
			id = UUID.randomUUID(), organizationId = orgId, teamId = team.id, name = "Spring Trip Fund",
			slug = "spring-trip-fund", description = null, campaignType = CampaignType.TRAVEL,
			goalAmountMinor = 100_000L, currency = "USD", startDate = null, endDate = null,
			status = CampaignStatus.ACTIVE, publishedAt = Instant.now(), createdAt = Instant.now(), updatedAt = Instant.now(),
		)
		every { membershipService.requireActiveMembership(orgId, currentUser) } returns membership()
		every { teamRepository.findAll(orgId, 0, 10) } returns listOf(team)
		every { participantRepository.countActiveForTeam(team.id, orgId) } returns 5
		every { campaignRepository.findActiveByTeam(team.id, orgId) } returns campaign
		every { contributionRepository.sumConfirmedByCampaign(campaign.id) } returns 55_000L

		val result = service.getTeamPerformance(orgId, currentUser)

		assertEquals(false, result.first().isFundraisingDemoData)
		assertEquals(55_000L, result.first().fundraisingRaisedMinor)
		assertEquals(100_000L, result.first().fundraisingGoalMinor)
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
