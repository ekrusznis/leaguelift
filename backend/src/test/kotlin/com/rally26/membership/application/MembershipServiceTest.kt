package com.rally26.membership.application

import com.rally26.common.error.ForbiddenException
import com.rally26.common.error.NotFoundException
import com.rally26.common.error.ValidationException
import com.rally26.common.web.CurrentUser
import com.rally26.membership.domain.MembershipRole
import com.rally26.membership.domain.MembershipStatus
import com.rally26.membership.domain.OrganizationMembership
import com.rally26.membership.persistence.MembershipRepository
import com.rally26.outbox.application.OutboxWriter
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
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
	private val outboxWriter = mockk<OutboxWriter>()
	private val service = MembershipService(membershipRepository, outboxWriter)

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
	fun `finance manager can view organization reports without receiving manager mutation access`() {
		val organizationId = UUID.randomUUID()
		val financeManager = CurrentUser(UUID.randomUUID(), "finance@example.com", "Finance")
		every { membershipRepository.findActiveMembership(organizationId, financeManager.userId) } returns
			membership(organizationId, financeManager.userId, MembershipRole.FINANCE_MANAGER)

		val result = service.requireReportingRole(organizationId, financeManager)

		assertEquals(MembershipRole.FINANCE_MANAGER, result.role)
		assertFailsWith<ForbiddenException> { service.requireManagerRole(organizationId, financeManager) }
	}

	@Test
	fun `viewer can view organization reports without receiving manager mutation access`() {
		val organizationId = UUID.randomUUID()
		val viewer = CurrentUser(UUID.randomUUID(), "report-viewer@example.com", "Report Viewer")
		every { membershipRepository.findActiveMembership(organizationId, viewer.userId) } returns
			membership(organizationId, viewer.userId, MembershipRole.VIEWER)

		val result = service.requireReportingRole(organizationId, viewer)

		assertEquals(MembershipRole.VIEWER, result.role)
		assertFailsWith<ForbiddenException> { service.requireManagerRole(organizationId, viewer) }
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
	fun `hasManagerRole returns true only for owner administrator or platform administrator`() {
		val organizationId = UUID.randomUUID()
		val admin = CurrentUser(UUID.randomUUID(), "manager-check@example.com", "Manager Check")
		every { membershipRepository.findActiveMembership(organizationId, admin.userId) } returns
			membership(organizationId, admin.userId, MembershipRole.ADMINISTRATOR)

		assertEquals(true, service.hasManagerRole(organizationId, admin))

		val viewer = CurrentUser(UUID.randomUUID(), "viewer-check@example.com", "Viewer Check")
		every { membershipRepository.findActiveMembership(organizationId, viewer.userId) } returns
			membership(organizationId, viewer.userId, MembershipRole.VIEWER)
		assertEquals(false, service.hasManagerRole(organizationId, viewer))

		val platformAdmin = CurrentUser(UUID.randomUUID(), "platform-check@example.com", "Platform", platformAdministrator = true)
		assertEquals(true, service.hasManagerRole(organizationId, platformAdmin))
	}

	@Test
	fun `administrator is denied owner-only actions`() {
		val organizationId = UUID.randomUUID()
		val admin = CurrentUser(UUID.randomUUID(), "admin5@example.com", "Admin5")
		every { membershipRepository.findActiveMembership(organizationId, admin.userId) } returns membership(organizationId, admin.userId, MembershipRole.ADMINISTRATOR)

		assertFailsWith<ForbiddenException> {
			service.requireOwnerRole(organizationId, admin)
		}
	}

	@Test
	fun `owner can perform owner-only actions`() {
		val organizationId = UUID.randomUUID()
		val owner = CurrentUser(UUID.randomUUID(), "owner2@example.com", "Owner2")
		every { membershipRepository.findActiveMembership(organizationId, owner.userId) } returns membership(organizationId, owner.userId, MembershipRole.OWNER)

		val result = service.requireOwnerRole(organizationId, owner)

		assertEquals(MembershipRole.OWNER, result.role)
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

	@Test
	fun `grantOwner enqueues a welcome email for a user's first-ever membership`() {
		val organizationId = UUID.randomUUID()
		val userId = UUID.randomUUID()
		val created = membership(organizationId, userId, MembershipRole.OWNER)
		every { membershipRepository.findAnyActiveMembershipForUser(userId) } returns null
		every { membershipRepository.insert(organizationId, userId, MembershipRole.OWNER) } returns created
		val payloadSlot = slot<String>()
		every { outboxWriter.write(any(), any(), any(), any(), capture(payloadSlot)) } just runs

		service.grantOwner(organizationId, userId)

		verify(exactly = 1) { outboxWriter.write("organization_membership", created.id, organizationId, "membership.first_granted", any()) }
		assertEquals(true, payloadSlot.captured.contains("\"role\":\"OWNER\""))
	}

	@Test
	fun `grantMembership does not enqueue a welcome email when the user already has another active membership`() {
		val organizationId = UUID.randomUUID()
		val userId = UUID.randomUUID()
		val existingElsewhere = membership(UUID.randomUUID(), userId, MembershipRole.VIEWER)
		every { membershipRepository.existsForUser(organizationId, userId) } returns false
		every { membershipRepository.findAnyActiveMembershipForUser(userId) } returns existingElsewhere
		every { membershipRepository.insert(organizationId, userId, MembershipRole.VIEWER) } returns
			membership(organizationId, userId, MembershipRole.VIEWER)

		service.grantMembership(organizationId, userId, MembershipRole.VIEWER)

		verify(exactly = 0) { outboxWriter.write(any(), any(), any(), any(), any()) }
	}

	@Test
	fun `grantMembership enqueues a welcome email for a user's first-ever membership`() {
		val organizationId = UUID.randomUUID()
		val userId = UUID.randomUUID()
		val created = membership(organizationId, userId, MembershipRole.VIEWER)
		every { membershipRepository.existsForUser(organizationId, userId) } returns false
		every { membershipRepository.findAnyActiveMembershipForUser(userId) } returns null
		every { membershipRepository.insert(organizationId, userId, MembershipRole.VIEWER) } returns created
		every { outboxWriter.write(any(), any(), any(), any(), any()) } just runs

		service.grantMembership(organizationId, userId, MembershipRole.VIEWER)

		verify(exactly = 1) { outboxWriter.write("organization_membership", created.id, organizationId, "membership.first_granted", any()) }
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
