package com.leaguelift.invitation.application

import com.leaguelift.audit.application.AuditService
import com.leaguelift.authorization.domain.RoleAssignmentContextType
import com.leaguelift.authorization.persistence.RoleAssignmentRepository
import com.leaguelift.common.error.ForbiddenException
import com.leaguelift.common.error.ValidationException
import com.leaguelift.common.web.CurrentUser
import com.leaguelift.invitation.domain.Invitation
import com.leaguelift.invitation.domain.InvitationStatus
import com.leaguelift.invitation.persistence.InvitationRepository
import com.leaguelift.membership.application.MembershipService
import com.leaguelift.membership.domain.MembershipRole
import com.leaguelift.membership.domain.MembershipStatus
import com.leaguelift.membership.domain.OrganizationMembership
import com.leaguelift.outbox.application.OutboxWriter
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

class InvitationServiceTest {

	private val invitationRepository = mockk<InvitationRepository>()
	private val membershipService = mockk<MembershipService>()
	private val roleAssignmentRepository = mockk<RoleAssignmentRepository>()
	private val auditService = mockk<AuditService>()
	private val outboxWriter = mockk<OutboxWriter>()
	private val service = InvitationService(invitationRepository, membershipService, roleAssignmentRepository, auditService, outboxWriter)

	private val organizationId = UUID.randomUUID()
	private val admin = CurrentUser(UUID.randomUUID(), "admin@example.com", "Admin")

	@Test
	fun `inviting OWNER is rejected`() {
		every { membershipService.requireManagerRole(organizationId, admin) } returns adminMembership()

		assertFailsWith<ValidationException> {
			service.invite(organizationId, "new@example.com", MembershipRole.OWNER, admin)
		}
	}

	@Test
	fun `a non-manager cannot invite`() {
		every { membershipService.requireManagerRole(organizationId, admin) } throws
			ForbiddenException("MEMBERSHIP_MANAGEMENT_DENIED", "Only owners and administrators can manage members.")

		assertFailsWith<ForbiddenException> {
			service.invite(organizationId, "new@example.com", MembershipRole.VIEWER, admin)
		}
	}

	@Test
	fun `a valid invitation is created and recorded`() {
		every { membershipService.requireManagerRole(organizationId, admin) } returns adminMembership()
		val insertedSlot = slot<String>()
		every {
			invitationRepository.insert(organizationId, "new@example.com", MembershipRole.VIEWER, admin.userId, any(), any(), any())
		} answers {
			Invitation(
				id = UUID.randomUUID(),
				organizationId = organizationId,
				email = "new@example.com",
				role = MembershipRole.VIEWER,
				status = InvitationStatus.PENDING,
				invitedByUserId = admin.userId,
				token = arg<String>(4),
				expiresAt = arg<Instant>(6),
				acceptedAt = null,
				createdAt = Instant.now(),
				updatedAt = Instant.now(),
			)
		}
		every { auditService.record(any(), any(), any(), any(), any(), any()) } just runs
		every { outboxWriter.write(any(), any(), any(), any(), capture(insertedSlot)) } just runs

		val result = service.invite(organizationId, "NEW@Example.com ", MembershipRole.VIEWER, admin)

		assertEquals("new@example.com", result.invitation.email) // normalized: trimmed + lowercased
		assertEquals(InvitationStatus.PENDING, result.invitation.status)
		assertEquals(43, result.rawToken.length)
	}

	@Test
	fun `accepting with a mismatched email is rejected`() {
		val invitation = pendingInvitation(email = "invitee@example.com")
		every { invitationRepository.findByTokenHash(any()) } returns invitation
		val wrongUser = CurrentUser(UUID.randomUUID(), "someone-else@example.com", "Someone Else")
		every { roleAssignmentRepository.findActiveForUserAndContext(any(), RoleAssignmentContextType.PARTICIPANT) } returns emptyList()

		assertFailsWith<ForbiddenException> {
			service.accept("tok", wrongUser)
		}
	}

	@Test
	fun `accepting an expired invitation is rejected and marks it expired`() {
		val invitation = pendingInvitation(email = "invitee@example.com", expiresAt = Instant.now().minusSeconds(60))
		every { invitationRepository.findByTokenHash(any()) } returns invitation
		every { invitationRepository.markStatus(invitation.id, InvitationStatus.EXPIRED) } returns 1
		val invitee = CurrentUser(UUID.randomUUID(), "invitee@example.com", "Invitee")
		every { roleAssignmentRepository.findActiveForUserAndContext(any(), RoleAssignmentContextType.PARTICIPANT) } returns emptyList()

		assertFailsWith<ValidationException> {
			service.accept("tok", invitee)
		}
	}

	@Test
	fun `athlete self account cannot accept organization invitation`() {
		val invitation = pendingInvitation(email = "athlete@example.com")
		every { invitationRepository.findByTokenHash(any()) } returns invitation
		every { roleAssignmentRepository.findActiveForUserAndContext(any(), RoleAssignmentContextType.PARTICIPANT) } returns listOf(mockk())
		val athlete = CurrentUser(UUID.randomUUID(), "athlete@example.com", "Athlete")

		assertFailsWith<ValidationException> {
			service.accept("tok", athlete)
		}
	}

	@Test
	fun `resending rotates token and records audit`() {
		val invitation = pendingInvitation(email = "invitee@example.com")
		every { membershipService.requireManagerRole(organizationId, admin) } returns adminMembership()
		every { invitationRepository.findById(invitation.id) } returnsMany listOf(invitation, invitation)
		every { invitationRepository.rotateToken(invitation.id, any(), any(), any()) } returns 1
		every { auditService.record(any(), any(), any(), any(), any(), any()) } just runs
		every { outboxWriter.write(any(), any(), any(), any(), any()) } just runs

		val resent = service.resend(organizationId, invitation.id, admin)

		assertEquals(invitation.id, resent.invitation.id)
		assertEquals(43, resent.rawToken.length)
		verify(exactly = 1) { invitationRepository.rotateToken(invitation.id, any(), any(), any()) }
	}

	private fun adminMembership() = OrganizationMembership(
		id = UUID.randomUUID(),
		organizationId = organizationId,
		userId = admin.userId,
		role = MembershipRole.ADMINISTRATOR,
		status = MembershipStatus.ACTIVE,
		createdAt = Instant.now(),
		updatedAt = Instant.now(),
	)

	private fun pendingInvitation(email: String, expiresAt: Instant = Instant.now().plusSeconds(3600)) = Invitation(
		id = UUID.randomUUID(),
		organizationId = organizationId,
		email = email,
		role = MembershipRole.VIEWER,
		status = InvitationStatus.PENDING,
		invitedByUserId = admin.userId,
		token = "tok",
		expiresAt = expiresAt,
		acceptedAt = null,
		createdAt = Instant.now(),
		updatedAt = Instant.now(),
	)
}
