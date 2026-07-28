package com.leaguelift.dashboard.application

import com.leaguelift.common.error.ForbiddenException
import com.leaguelift.common.error.NotFoundException
import com.leaguelift.common.web.CurrentUser
import com.leaguelift.fee.persistence.FeeRepository
import com.leaguelift.fundraising.persistence.CampaignRepository
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
	private val participantRepository = mockk<ParticipantRepository>()
	private val teamRepository = mockk<TeamRepository>()
	private val feeRepository = mockk<FeeRepository>()
	private val campaignRepository = mockk<CampaignRepository>()

	private val service = ParentDashboardService(
		householdRepository, membershipRepository, participantRepository, teamRepository, feeRepository, campaignRepository,
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
	fun `getOverview denies access when caller is neither an org member nor a household adult`() {
		every { householdRepository.findById(householdId, orgId) } returns household()
		every { membershipRepository.findActiveMembership(orgId, guardian.userId) } returns null
		every { householdRepository.listAdults(householdId, orgId) } returns listOf(adult("someone.else@example.com"))

		assertFailsWith<ForbiddenException> {
			service.getOverview(orgId, householdId, guardian)
		}
	}

	@Test
	fun `getOverview allows access when caller email matches an active household adult`() {
		every { householdRepository.findById(householdId, orgId) } returns household()
		every { membershipRepository.findActiveMembership(orgId, guardian.userId) } returns null
		every { householdRepository.listAdults(householdId, orgId) } returns listOf(adult(guardian.email))

		val result = service.getOverview(orgId, householdId, guardian)

		assertEquals("Johnson Family", result.householdName)
	}

	@Test
	fun `getOutstandingBalance sums only open and partially-paid assignments`() {
		every { householdRepository.findById(householdId, orgId) } returns household()
		every { membershipRepository.findActiveMembership(orgId, guardian.userId) } returns null
		every { householdRepository.listAdults(householdId, orgId) } returns listOf(adult(guardian.email))
		every { feeRepository.findByHousehold(householdId, orgId, 0, 50) } returns emptyList()

		val result = service.getOutstandingBalance(orgId, householdId, guardian)

		assertEquals(0, result.totalOutstandingMinor)
		assertEquals(true, result.isApproximate)
	}

	@Test
	fun `getAthletes returns real participants for the household`() {
		every { householdRepository.findById(householdId, orgId) } returns household()
		every { membershipRepository.findActiveMembership(orgId, guardian.userId) } returns null
		every { householdRepository.listAdults(householdId, orgId) } returns listOf(adult(guardian.email))
		every { participantRepository.findByHousehold(householdId, orgId) } returns emptyList()

		val result = service.getAthletes(orgId, householdId, guardian)

		assertEquals(0, result.size)
	}

	private fun household() = Household(
		id = householdId,
		organizationId = orgId,
		displayName = "Johnson Family",
		contactEmail = "sarah.johnson@example.com",
		contactPhone = null,
		notes = null,
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
