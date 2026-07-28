package com.leaguelift.membership.application

import com.leaguelift.common.error.ForbiddenException
import com.leaguelift.common.error.NotFoundException
import com.leaguelift.common.error.ValidationException
import com.leaguelift.common.web.CurrentUser
import com.leaguelift.membership.domain.MembershipRole
import com.leaguelift.membership.domain.MembershipStatus
import com.leaguelift.membership.domain.OrganizationMembership
import com.leaguelift.membership.persistence.MembershipRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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
		val user = CurrentUser(UUID.randomUUID(), "a@example.com", "A", platformAdministrator = false)
		every { membershipRepository.findActiveMembership(organizationId, user.userId) } returns null

		assertFailsWith<ForbiddenException> {
			service.requireActiveMembership(organizationId, user)
		}
	}

	@Test
	fun `user with an active membership is granted access`() {
		val organizationId = UUID.randomUUID()
		val user = CurrentUser(UUID.randomUUID(), "b@example.com", "B", platformAdministrator = false)
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
		val admin = CurrentUser(UUID.randomUUID(), "admin@example.com", "Admin", platformAdministrator = true)
		every { membershipRepository.findActiveMembership(organizationId, admin.userId) } returns null

		val result = service.requireActiveMembership(organizationId, admin)

		assertEquals(admin.userId, result.userId)
	}

	@Test
	fun `viewer cannot manage members`() {
		val organizationId = UUID.randomUUID()
		val viewer = CurrentUser(UUID.randomUUID(), "viewer@example.com", "Viewer")
		every { membershipRepository.findActiveMembership(organizationId, viewer.userId) } returns membership(organizationId, viewer.userId, MembershipRole.VIEWER)

		assertFailsWith<ForbiddenException> {
			service.requireManagerRole(organizationId, viewer)
		}
	}

	@Test
	fun `administrator can manage members`() {
		val organizationId = UUID.randomUUID()
		val admin = CurrentUser(UUID.randomUUID(), "admin2@example.com", "Admin2")
		every { membershipRepository.findActiveMembership(organizationId, admin.userId) } returns membership(organizationId, admin.userId, MembershipRole.ADMINISTRATOR)

		val result = service.requireManagerRole(organizationId, admin)

		assertEquals(MembershipRole.ADMINISTRATOR, result.role)
	}

	@Test
	fun `the owner membership cannot be revoked`() {
		val organizationId = UUID.randomUUID()
		val owner = CurrentUser(UUID.randomUUID(), "owner@example.com", "Owner")
		val ownerMembership = membership(organizationId, owner.userId, MembershipRole.OWNER)
		every { membershipRepository.findActiveMembership(organizationId, owner.userId) } returns ownerMembership
		every { membershipRepository.findById(ownerMembership.id) } returns ownerMembership

		assertFailsWith<ValidationException> {
			service.revoke(organizationId, ownerMembership.id, owner)
		}
	}

	@Test
	fun `revoking a nonexistent membership is not found`() {
		val organizationId = UUID.randomUUID()
		val admin = CurrentUser(UUID.randomUUID(), "admin3@example.com", "Admin3")
		val missingId = UUID.randomUUID()
		every { membershipRepository.findActiveMembership(organizationId, admin.userId) } returns membership(organizationId, admin.userId, MembershipRole.ADMINISTRATOR)
		every { membershipRepository.findById(missingId) } returns null

		assertFailsWith<NotFoundException> {
			service.revoke(organizationId, missingId, admin)
		}
	}

	@Test
	fun `revoking a non-owner membership succeeds`() {
		val organizationId = UUID.randomUUID()
		val admin = CurrentUser(UUID.randomUUID(), "admin4@example.com", "Admin4")
		val target = membership(organizationId, UUID.randomUUID(), MembershipRole.VIEWER)
		every { membershipRepository.findActiveMembership(organizationId, admin.userId) } returns membership(organizationId, admin.userId, MembershipRole.ADMINISTRATOR)
		every { membershipRepository.findById(target.id) } returns target
		every { membershipRepository.revoke(target.id) } returns 1

		service.revoke(organizationId, target.id, admin)

		verify(exactly = 1) { membershipRepository.revoke(target.id) }
	}

	@Test
	fun `grantMembership rejects granting OWNER`() {
		assertFailsWith<IllegalArgumentException> {
			service.grantMembership(UUID.randomUUID(), UUID.randomUUID(), MembershipRole.OWNER)
		}
	}

	@Test
	fun `grantMembership rejects a user who is already a member`() {
		val organizationId = UUID.randomUUID()
		val userId = UUID.randomUUID()
		every { membershipRepository.existsForUser(organizationId, userId) } returns true

		assertFailsWith<ValidationException> {
			service.grantMembership(organizationId, userId, MembershipRole.VIEWER)
		}
	}

	private fun membership(organizationId: UUID, userId: UUID, role: MembershipRole) = OrganizationMembership(
		id = UUID.randomUUID(),
		organizationId = organizationId,
		userId = userId,
		role = role,
		status = MembershipStatus.ACTIVE,
		createdAt = Instant.now(),
		updatedAt = Instant.now(),
	)
}
