package com.leaguelift.dashboard.application

import com.leaguelift.authorization.domain.GuardianRelationship
import com.leaguelift.authorization.domain.GuardianRelationshipStatus
import com.leaguelift.authorization.persistence.GuardianRelationshipRepository
import com.leaguelift.common.error.ForbiddenException
import com.leaguelift.common.error.NotFoundException
import com.leaguelift.common.web.CurrentUser
import com.leaguelift.fee.domain.FeeAssignment
import com.leaguelift.fee.domain.FeeAssignmentStatus
import com.leaguelift.fee.persistence.FeeAdjustmentRepository
import com.leaguelift.fee.persistence.FeePaymentRepository
import com.leaguelift.fee.persistence.FeeRepository
import com.leaguelift.fundraising.domain.Campaign
import com.leaguelift.fundraising.domain.CampaignStatus
import com.leaguelift.fundraising.domain.CampaignType
import com.leaguelift.fundraising.persistence.CampaignRepository
import com.leaguelift.fundraising.persistence.ContributionRepository
import com.leaguelift.household.domain.AdultStatus
import com.leaguelift.household.domain.Household
import com.leaguelift.household.domain.HouseholdAdult
import com.leaguelift.household.domain.HouseholdStatus
import com.leaguelift.household.persistence.HouseholdRepository
import com.leaguelift.membership.persistence.MembershipRepository
import com.leaguelift.participant.persistence.ParticipantRepository
import com.leaguelift.team.persistence.TeamRepository
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ParentDashboardServiceTest {

	private val householdRepository = mockk<HouseholdRepository>()
	private val membershipRepository = mockk<MembershipRepository>()
	private val guardianRelationshipRepository = mockk<GuardianRelationshipRepository>()
	private val participantRepository = mockk<ParticipantRepository>()
	private val teamRepository = mockk<TeamRepository>()
	private val feeRepository = mockk<FeeRepository>()
	private val feePaymentRepository = mockk<FeePaymentRepository>()
	private val feeAdjustmentRepository = mockk<FeeAdjustmentRepository>()
	private val campaignRepository = mockk<CampaignRepository>()
	private val contributionRepository = mockk<ContributionRepository>()

	private val service = ParentDashboardService(
		householdRepository, membershipRepository, guardianRelationshipRepository, participantRepository, teamRepository,
		feeRepository, feePaymentRepository, feeAdjustmentRepository, campaignRepository, contributionRepository,
	)

	private val orgId = UUID.randomUUID()
	private val householdId = UUID.randomUUID()
	private val guardian = CurrentUser(UUID.randomUUID(), "sarah.johnson@example.com", "Sarah Johnson")

	@Test
	fun `getOverview throws NotFoundException when household does not exist`() {
		every { householdRepository.findById(householdId, orgId) } returns null

		assertFailsWith<NotFoundException> {
			service.getOverview(orgId, householdId, guardian)
		}
	}

	@Test
	fun `getOverview denies access when caller is neither an org member, a real guardian, nor a household adult`() {
		every { householdRepository.findById(householdId, orgId) } returns household()
		every { membershipRepository.findActiveMembership(orgId, guardian.userId) } returns null
		every { guardianRelationshipRepository.findActiveForHousehold(guardian.userId, householdId) } returns null
		every { householdRepository.listAdults(householdId, orgId) } returns listOf(adult("someone.else@example.com"))

		assertFailsWith<ForbiddenException> {
			service.getOverview(orgId, householdId, guardian)
		}
	}

	@Test
	fun `getOverview allows access via a real guardian_relationship without an email match`() {
		every { householdRepository.findById(householdId, orgId) } returns household()
		every { membershipRepository.findActiveMembership(orgId, guardian.userId) } returns null
		every { guardianRelationshipRepository.findActiveForHousehold(guardian.userId, householdId) } returns
			GuardianRelationship(
				UUID.randomUUID(), orgId, householdId, UUID.randomUUID(), guardian.userId,
				GuardianRelationshipStatus.ACTIVE, Instant.now(), Instant.now(),
			)

		val result = service.getOverview(orgId, householdId, guardian)

		assertEquals("Johnson Family", result.householdName)
	}

	@Test
	fun `getOverview allows access when caller email matches an active household adult`() {
		every { householdRepository.findById(householdId, orgId) } returns household()
		every { membershipRepository.findActiveMembership(orgId, guardian.userId) } returns null
		every { guardianRelationshipRepository.findActiveForHousehold(guardian.userId, householdId) } returns null
		every { householdRepository.listAdults(householdId, orgId) } returns listOf(adult(guardian.email))

		val result = service.getOverview(orgId, householdId, guardian)

		assertEquals("Johnson Family", result.householdName)
	}

	@Test
	fun `getOutstandingBalance is zero with no fee assignments`() {
		every { householdRepository.findById(householdId, orgId) } returns household()
		every { membershipRepository.findActiveMembership(orgId, guardian.userId) } returns null
		every { guardianRelationshipRepository.findActiveForHousehold(guardian.userId, householdId) } returns null
		every { householdRepository.listAdults(householdId, orgId) } returns listOf(adult(guardian.email))
		every { feeRepository.findByHousehold(householdId, orgId, 0, 50) } returns emptyList()

		val result = service.getOutstandingBalance(orgId, householdId, guardian)

		assertEquals(0, result.totalOutstandingMinor)
		assertEquals(0, result.lineItems.size)
	}

	@Test
	fun `getOutstandingBalance nets real payments and adjustments, excludes PAID assignments`() {
		val openAssignment = feeAssignment(status = FeeAssignmentStatus.OPEN, originalAmountMinor = 15000L)
		val paidAssignment = feeAssignment(status = FeeAssignmentStatus.PAID, originalAmountMinor = 5000L)
		every { householdRepository.findById(householdId, orgId) } returns household()
		every { membershipRepository.findActiveMembership(orgId, guardian.userId) } returns null
		every { guardianRelationshipRepository.findActiveForHousehold(guardian.userId, householdId) } returns null
		every { householdRepository.listAdults(householdId, orgId) } returns listOf(adult(guardian.email))
		every { feeRepository.findByHousehold(householdId, orgId, 0, 50) } returns listOf(openAssignment, paidAssignment)
		every { feePaymentRepository.sumActiveByAssignment(openAssignment.id, orgId) } returns 5000L
		every { feeAdjustmentRepository.sumActiveByAssignment(openAssignment.id, orgId) } returns 2000L

		val result = service.getOutstandingBalance(orgId, householdId, guardian)

		assertEquals(8000L, result.totalOutstandingMinor)
		assertEquals(1, result.lineItems.size, "the PAID assignment must not appear")
		assertEquals(8000L, result.lineItems.single().balanceMinor)
	}

	@Test
	fun `getAthletes returns real participants for the household`() {
		every { householdRepository.findById(householdId, orgId) } returns household()
		every { membershipRepository.findActiveMembership(orgId, guardian.userId) } returns null
		every { guardianRelationshipRepository.findActiveForHousehold(guardian.userId, householdId) } returns null
		every { householdRepository.listAdults(householdId, orgId) } returns listOf(adult(guardian.email))
		every { participantRepository.findByHousehold(householdId, orgId) } returns emptyList()

		val result = service.getAthletes(orgId, householdId, guardian)

		assertEquals(0, result.size)
	}

	@Test
	fun `getActiveFundraisers returns real confirmed contribution totals`() {
		val campaign = Campaign(
			id = UUID.randomUUID(), organizationId = orgId, teamId = null, name = "Spring Trip Fund",
			slug = "spring-trip-fund", description = null, campaignType = CampaignType.TRAVEL,
			goalAmountMinor = 100_000L, currency = "USD", startDate = null, endDate = null,
			status = CampaignStatus.ACTIVE, publishedAt = Instant.now(), createdAt = Instant.now(), updatedAt = Instant.now(),
		)
		every { householdRepository.findById(householdId, orgId) } returns household()
		every { membershipRepository.findActiveMembership(orgId, guardian.userId) } returns null
		every { guardianRelationshipRepository.findActiveForHousehold(guardian.userId, householdId) } returns null
		every { householdRepository.listAdults(householdId, orgId) } returns listOf(adult(guardian.email))
		every { campaignRepository.findAll(orgId, 0, 25) } returns listOf(campaign)
		every { contributionRepository.sumConfirmedByCampaign(campaign.id) } returns 34_500L

		val result = service.getActiveFundraisers(orgId, householdId, guardian)

		assertEquals(1, result.size)
		assertEquals(false, result.first().isRaisedDemoData)
		assertEquals(34_500L, result.first().raisedMinor)
	}

	private fun feeAssignment(status: FeeAssignmentStatus, originalAmountMinor: Long) = FeeAssignment(
		id = UUID.randomUUID(),
		organizationId = orgId,
		householdId = householdId,
		participantId = null,
		feeTemplateId = null,
		description = "Spring Registration",
		originalAmountMinor = originalAmountMinor,
		currency = "USD",
		dueDate = null,
		status = status,
		createdAt = Instant.now(),
		updatedAt = Instant.now(),
	)

	private fun household() = Household(
		id = householdId,
		organizationId = orgId,
		displayName = "Johnson Family",
		contactEmail = "sarah.johnson@example.com",
		contactPhone = null,
		notes = null,
		emailRemindersOptOut = false,
		smsRemindersOptIn = false,
		status = HouseholdStatus.ACTIVE,
		createdAt = Instant.now(),
		updatedAt = Instant.now(),
	)

	private fun adult(email: String) = HouseholdAdult(
		id = UUID.randomUUID(),
		householdId = householdId,
		organizationId = orgId,
		firstName = "Sarah",
		lastName = "Johnson",
		email = email,
		phone = null,
		relationship = "Parent",
		isPrimary = true,
		status = AdultStatus.ACTIVE,
		createdAt = Instant.now(),
		updatedAt = Instant.now(),
	)
}
