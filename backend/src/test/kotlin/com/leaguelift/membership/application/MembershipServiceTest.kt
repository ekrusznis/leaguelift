package com.leaguelift.membership.application

import com.leaguelift.common.error.ForbiddenException
import com.leaguelift.common.web.CurrentUser
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
import kotlin.test.assertFailsWith

/**
 * Unit-level version of DESIGN-DOC.md section 22.3 critical scenario 1: a user
 * without a membership in an organization must not be treated as authorized for it.
 */
class MembershipServiceTest {

	private val membershipRepository = mockk<MembershipRepository>()
	private val service = MembershipService(membershipRepository)

	@Test
	fun `user without a membership is denied access to the organization`() {
		val organizationId = UUID.randomUUID()
		val user = CurrentUser(UUID.randomUUID(), "sub-1", "a@example.com", "A", platformAdministrator = false)
		every { membershipRepository.findActiveMembership(organizationId, user.userId) } returns null

		assertFailsWith<ForbiddenException> {
			service.requireActiveMembership(organizationId, user)
		}
	}

	@Test
	fun `user with an active membership is granted access`() {
		val organizationId = UUID.randomUUID()
		val user = CurrentUser(UUID.randomUUID(), "sub-2", "b@example.com", "B", platformAdministrator = false)
		val membership = OrganizationMembership(
			id = UUID.randomUUID(),
			organizationId = organizationId,
			userId = user.userId,
			role = MembershipRole.OWNER,
			status = MembershipStatus.ACTIVE,
			createdAt = Instant.now(),
			updatedAt = Instant.now(),
		)
		every { membershipRepository.findActiveMembership(organizationId, user.userId) } returns membership

		val result = service.requireActiveMembership(organizationId, user)

		assertEquals(MembershipRole.OWNER, result.role)
	}

	@Test
	fun `platform administrator without a membership is still granted synthetic access`() {
		val organizationId = UUID.randomUUID()
		val admin = CurrentUser(UUID.randomUUID(), "sub-admin", "admin@example.com", "Admin", platformAdministrator = true)
		every { membershipRepository.findActiveMembership(organizationId, admin.userId) } returns null

		val result = service.requireActiveMembership(organizationId, admin)

		assertEquals(admin.userId, result.userId)
	}
}
