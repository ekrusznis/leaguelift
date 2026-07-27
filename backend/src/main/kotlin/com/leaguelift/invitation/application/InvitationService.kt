package com.leaguelift.invitation.application

import com.leaguelift.audit.application.AuditService
import com.leaguelift.common.error.ForbiddenException
import com.leaguelift.common.error.NotFoundException
import com.leaguelift.common.error.ValidationException
import com.leaguelift.common.web.CurrentUser
import com.leaguelift.invitation.domain.INVITABLE_ROLES
import com.leaguelift.invitation.domain.Invitation
import com.leaguelift.invitation.domain.InvitationStatus
import com.leaguelift.invitation.persistence.InvitationRepository
import com.leaguelift.membership.application.MembershipService
import com.leaguelift.membership.domain.MembershipRole
import com.leaguelift.outbox.application.OutboxWriter
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.UUID

private const val INVITATION_VALIDITY_DAYS = 7L

@Service
class InvitationService(
	private val invitationRepository: InvitationRepository,
	private val membershipService: MembershipService,
	private val auditService: AuditService,
	private val outboxWriter: OutboxWriter,
) {

	@Transactional
	fun invite(organizationId: UUID, email: String, role: MembershipRole, currentUser: CurrentUser): Invitation {
		membershipService.requireManagerRole(organizationId, currentUser)
		if (role !in INVITABLE_ROLES) {
			throw ValidationException(
				"Role must be one of: ${INVITABLE_ROLES.joinToString { it.name }}.",
				listOf(com.leaguelift.common.error.FieldError("role", "Not an invitable role.")),
			)
		}
		val normalizedEmail = email.trim().lowercase()
		val token = generateToken()
		val expiresAt = Instant.now().plus(Duration.ofDays(INVITATION_VALIDITY_DAYS))
		val invitation = invitationRepository.insert(organizationId, normalizedEmail, role, currentUser.userId, token, expiresAt)

		auditService.record(
			actorUserId = currentUser.userId,
			organizationId = organizationId,
			action = "membership.invited",
			entityType = "invitation",
			entityId = invitation.id,
		)
		// Payload intentionally omits the raw token — outbox events may be inspected by
		// platform admins (DESIGN-DOC.md section 20.2); the future email-sending worker
		// should look the invitation back up by ID rather than trusting a token that
		// traveled through this event.
		outboxWriter.write(
			aggregateType = "invitation",
			aggregateId = invitation.id,
			organizationId = organizationId,
			eventType = "membership.invited",
			payloadJson = """{"invitationId":"${invitation.id}","email":"$normalizedEmail","role":"${role.name}"}""",
		)
		return invitation
	}

	fun listPending(organizationId: UUID, currentUser: CurrentUser, offset: Int, limit: Int): List<Invitation> {
		membershipService.requireManagerRole(organizationId, currentUser)
		return invitationRepository.listPendingForOrganization(organizationId, offset, limit)
	}

	fun countPending(organizationId: UUID, currentUser: CurrentUser): Long {
		membershipService.requireManagerRole(organizationId, currentUser)
		return invitationRepository.countPendingForOrganization(organizationId)
	}

	@Transactional
	fun revoke(organizationId: UUID, invitationId: UUID, currentUser: CurrentUser) {
		membershipService.requireManagerRole(organizationId, currentUser)
		val invitation = invitationRepository.findById(invitationId)
			?.takeIf { it.organizationId == organizationId }
			?: throw NotFoundException("INVITATION_NOT_FOUND", "The invitation could not be found.")
		if (invitation.status != InvitationStatus.PENDING) {
			throw ValidationException("Only pending invitations can be revoked.")
		}
		invitationRepository.markStatus(invitationId, InvitationStatus.REVOKED)
		auditService.record(
			actorUserId = currentUser.userId,
			organizationId = organizationId,
			action = "membership.invitation_revoked",
			entityType = "invitation",
			entityId = invitationId,
		)
	}

	/**
	 * Accepts an invitation by token for the currently authenticated user. The
	 * invitation's email must match the caller's own email — this is what stops any
	 * authenticated user from redeeming a token meant for someone else.
	 */
	@Transactional
	fun accept(token: String, currentUser: CurrentUser): Invitation {
		val invitation = invitationRepository.findByToken(token)
			?: throw NotFoundException("INVITATION_NOT_FOUND", "This invitation link is invalid.")

		if (invitation.status == InvitationStatus.PENDING && invitation.expiresAt.isBefore(Instant.now())) {
			invitationRepository.markStatus(invitation.id, InvitationStatus.EXPIRED)
			throw ValidationException("This invitation has expired.")
		}
		if (invitation.status != InvitationStatus.PENDING) {
			throw ValidationException("This invitation is no longer pending.")
		}
		if (!invitation.email.equals(currentUser.email.trim(), ignoreCase = true)) {
			throw ForbiddenException(
				code = "INVITATION_EMAIL_MISMATCH",
				message = "This invitation was sent to a different email address.",
			)
		}

		membershipService.grantMembership(invitation.organizationId, currentUser.userId, invitation.role)
		invitationRepository.markStatus(invitation.id, InvitationStatus.ACCEPTED, acceptedAt = Instant.now())
		auditService.record(
			actorUserId = currentUser.userId,
			organizationId = invitation.organizationId,
			action = "membership.accepted",
			entityType = "invitation",
			entityId = invitation.id,
		)
		return invitationRepository.findById(invitation.id)!!
	}

	private fun generateToken(): String {
		val bytes = ByteArray(32)
		SecureRandom().nextBytes(bytes)
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
	}
}
