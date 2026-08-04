package com.rally26.invitation.application

import com.rally26.audit.application.AuditService
import com.rally26.authorization.domain.RoleAssignmentContextType
import com.rally26.authorization.persistence.RoleAssignmentRepository
import com.rally26.common.error.ForbiddenException
import com.rally26.common.error.NotFoundException
import com.rally26.common.error.ValidationException
import com.rally26.common.web.CurrentUser
import com.rally26.invitation.domain.INVITABLE_ROLES
import com.rally26.invitation.domain.Invitation
import com.rally26.invitation.domain.InvitationStatus
import com.rally26.invitation.persistence.InvitationRepository
import com.rally26.membership.application.MembershipService
import com.rally26.membership.domain.MembershipRole
import com.rally26.outbox.application.OutboxWriter
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.UUID

private const val INVITATION_VALIDITY_DAYS = 7L

@Service
class InvitationService(
	private val invitationRepository: InvitationRepository,
	private val membershipService: MembershipService,
	private val roleAssignmentRepository: RoleAssignmentRepository,
	private val auditService: AuditService,
	private val outboxWriter: OutboxWriter,
) {

	data class CreatedInvitation(
		val invitation: Invitation,
		val rawToken: String,
	)

	@Transactional
	fun invite(organizationId: UUID, email: String, role: MembershipRole, currentUser: CurrentUser): CreatedInvitation {
		membershipService.requireManagerRole(organizationId, currentUser)
		if (role !in INVITABLE_ROLES) {
			throw ValidationException(
				"Role must be one of: ${INVITABLE_ROLES.joinToString { it.name }}.",
				listOf(com.rally26.common.error.FieldError("role", "Not an invitable role.")),
			)
		}
		val normalizedEmail = email.trim().lowercase()
		val rawToken = generateToken()
		val tokenHash = sha256Hex(rawToken)
		val tokenReference = UUID.randomUUID().toString()
		val expiresAt = Instant.now().plus(Duration.ofDays(INVITATION_VALIDITY_DAYS))
		val invitation = invitationRepository.insert(
			organizationId = organizationId,
			email = normalizedEmail,
			role = role,
			invitedByUserId = currentUser.userId,
			tokenReference = tokenReference,
			tokenHash = tokenHash,
			expiresAt = expiresAt,
		)

		auditService.record(
			actorUserId = currentUser.userId,
			organizationId = organizationId,
			action = "membership.invited",
			entityType = "invitation",
			entityId = invitation.id,
		)
		// The persisted invitation row no longer stores the raw accept token; the email
		// worker receives it through this event payload while still looking up destination
		// metadata by invitation id.
		outboxWriter.write(
			aggregateType = "invitation",
			aggregateId = invitation.id,
			organizationId = organizationId,
			eventType = "membership.invited",
			payloadJson = """{"invitationId":"${invitation.id}","email":"$normalizedEmail","role":"${role.name}","acceptToken":"$rawToken"}""",
		)
		return CreatedInvitation(invitation, rawToken)
	}

	@Transactional
	fun resend(organizationId: UUID, invitationId: UUID, currentUser: CurrentUser): CreatedInvitation {
		membershipService.requireManagerRole(organizationId, currentUser)
		val invitation = invitationRepository.findById(invitationId)
			?.takeIf { it.organizationId == organizationId }
			?: throw NotFoundException("INVITATION_NOT_FOUND", "The invitation could not be found.")
		if (invitation.status != InvitationStatus.PENDING) {
			throw ValidationException("Only pending invitations can be resent.")
		}

		val rawToken = generateToken()
		val tokenHash = sha256Hex(rawToken)
		val tokenReference = UUID.randomUUID().toString()
		val expiresAt = Instant.now().plus(Duration.ofDays(INVITATION_VALIDITY_DAYS))
		invitationRepository.rotateToken(invitation.id, tokenReference, tokenHash, expiresAt)

		auditService.record(
			actorUserId = currentUser.userId,
			organizationId = organizationId,
			action = "membership.invitation_resent",
			entityType = "invitation",
			entityId = invitation.id,
		)
		outboxWriter.write(
			aggregateType = "invitation",
			aggregateId = invitation.id,
			organizationId = organizationId,
			eventType = "membership.invited",
			payloadJson =
				"""{"invitationId":"${invitation.id}","email":"${invitation.email}","role":"${invitation.role.name}","acceptToken":"$rawToken"}""",
		)

		val updated = invitationRepository.findById(invitation.id)!!
		return CreatedInvitation(updated, rawToken)
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
		val invitation = invitationRepository.findByTokenHash(sha256Hex(token))
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
		if (roleAssignmentRepository.findActiveForUserAndContext(currentUser.userId, RoleAssignmentContextType.PARTICIPANT).isNotEmpty()) {
			throw ValidationException("Athlete self-login accounts cannot accept staff or organization invitations.")
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

	private fun sha256Hex(value: String): String =
		MessageDigest.getInstance("SHA-256")
			.digest(value.toByteArray(Charsets.UTF_8))
			.joinToString("") { "%02x".format(it) }
}
