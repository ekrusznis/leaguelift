package com.leaguelift.dashboard.application

import com.leaguelift.common.web.CurrentUser
import com.leaguelift.dashboard.domain.DashboardRole
import com.leaguelift.household.domain.AdultStatus
import com.leaguelift.household.domain.HouseholdAdult
import com.leaguelift.household.persistence.HouseholdRepository
import com.leaguelift.membership.domain.MembershipRole
import com.leaguelift.membership.domain.MembershipStatus
import com.leaguelift.membership.domain.OrganizationMembership
import com.leaguelift.membership.persistence.MembershipRepository
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DashboardContextServiceTest {

	private val membershipRepository = mockk<MembershipRepository>()
	private val householdRepository = mockk<HouseholdRepository>()
	private val service = DashboardContextService(membershipRepository, householdRepository)

	private val currentUser = CurrentUser(UUID.randomUUID(), "person@example.com", "Person")

	@Test
	fun `org owner membership resolves to OWNER dashboard`() {
		every { membershipRepository.findAnyActiveMembershipForUser(currentUser.userId) } returns membership(MembershipRole.OWNER)

		val result = service.resolve(currentUser)

		assertEquals(DashboardRole.OWNER, result.role)
	}

	@Test
	fun `finance manager and viewer roles also resolve to OWNER dashboard`() {
		every { membershipRepository.findAnyActiveMembershipForUser(currentUser.userId) } returns membership(MembershipRole.FINANCE_MANAGER)
		assertEquals(DashboardRole.OWNER, service.resolve(currentUser).role)

		every { membershipRepository.findAnyActiveMembershipForUser(currentUser.userId) } returns membership(MembershipRole.VIEWER)
		assertEquals(DashboardRole.OWNER, service.resolve(currentUser).role)
	}

	@Test
	fun `team administrator membership resolves to COACH dashboard`() {
		every { membershipRepository.findAnyActiveMembershipForUser(currentUser.userId) } returns membership(MembershipRole.TEAM_ADMINISTRATOR)

		val result = service.resolve(currentUser)

		assertEquals(DashboardRole.COACH, result.role)
	}

	@Test
	fun `household adult email match resolves to PARENT dashboard when no membership exists`() {
		every { membershipRepository.findAnyActiveMembershipForUser(currentUser.userId) } returns null
		val adult = HouseholdAdult(
			id = UUID.randomUUID(),
			householdId = UUID.randomUUID(),
			organizationId = UUID.randomUUID(),
			firstName = "Person",
			lastName = "Example",
			email = currentUser.email,
			phone = null,
			relationship = "Parent",
			isPrimary = true,
			status = AdultStatus.ACTIVE,
			createdAt = Instant.now(),
			updatedAt = Instant.now(),
		)
		every { householdRepository.findActiveAdultByEmail(currentUser.email) } returns adult

		val result = service.resolve(currentUser)

		assertEquals(DashboardRole.PARENT, result.role)
		assertEquals(adult.householdId, result.householdId)
		assertEquals(adult.organizationId, result.organizationId)
	}

	@Test
	fun `falls back to ATHLETE dashboard when no membership or household match exists`() {
		every { membershipRepository.findAnyActiveMembershipForUser(currentUser.userId) } returns null
		every { householdRepository.findActiveAdultByEmail(currentUser.email) } returns null

		val result = service.resolve(currentUser)

		assertEquals(DashboardRole.ATHLETE, result.role)
		assertNull(result.organizationId)
		assertNull(result.householdId)
	}

	private fun membership(role: MembershipRole) = OrganizationMembership(
		id = UUID.randomUUID(),
		organizationId = UUID.randomUUID(),
		userId = currentUser.userId,
		role = role,
		status = MembershipStatus.ACTIVE,
		createdAt = Instant.now(),
		updatedAt = Instant.now(),
	)
}
