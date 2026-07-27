package com.leaguelift.invitation.application

import com.leaguelift.audit.application.AuditService
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
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class InvitationServiceTest {

	private val invitationRepository = mockk<InvitationRepository>()
	private val membershipService = mockk<MembershipService>()
	private val auditService = mockk<AuditService>()
	private val outboxWriter = mockk<OutboxWriter>()
	private val service = InvitationService(invitationRepository, membershipService, auditService, outboxWriter)

	private val organizationId = UUID.randomUUID()
	private val admin = CurrentUser(UUID.randomUUID(), "sub-admin", "admin@example.com", "Admin")

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
			invitationRepository.insert(organizationId, "new@example.com", MembershipRole.VIEWER, admin.userId, any(), any())
		} answers {
			Invitation(
				id = UUID.randomUUID(),
				organizationId = organizationId,
				email = "new@example.com",
				role = MembershipRole.VIEWER,
				status = InvitationStatus.PENDING,
				invitedByUserId = admin.userId,
				token = arg<String>(4),
				expiresAt = arg<Instant>(5),
				acceptedAt = null,
				createdAt = Instant.now(),
				updatedAt = Instant.now(),
			)
		}
		every { auditService.record(any(), any(), any(), any(), any(), any()) } just runs
		every { outboxWriter.write(any(), any(), any(), any(), capture(insertedSlot)) } just runs

		val result = service.invite(organizationId, "NEW@Example.com ", MembershipRole.VIEWER, admin)

		assertEquals("new@example.com", result.email) // normalized: trimmed + lowercased
		assertEquals(InvitationStatus.PENDING, result.status)
	}

	@Test
	fun `accepting with a mismatched email is rejected`() {
		val invitation = pendingInvitation(email = "invitee@example.com")
		every { invitationRepository.findByToken("tok") } returns invitation
		val wrongUser = CurrentUser(UUID.randomUUID(), "sub-wrong", "someone-else@example.com", "Someone Else")

		assertFailsWith<ForbiddenException> {
			service.accept("tok", wrongUser)
		}
	}

	@Test
	fun `accepting an expired invitation is rejected and marks it expired`() {
		val invitation = pendingInvitation(email = "invitee@example.com", expiresAt = Instant.now().minusSeconds(60))
		every { invitationRepository.findByToken("tok") } returns invitation
		every { invitationRepository.markStatus(invitation.id, InvitationStatus.EXPIRED) } returns 1
		val invitee = CurrentUser(UUID.randomUUID(), "sub-invitee", "invitee@example.com", "Invitee")

		assertFailsWith<ValidationException> {
			service.accept("tok", invitee)
		}
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
