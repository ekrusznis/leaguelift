package com.rally26.invitation.application

import com.rally26.audit.application.AuditService
import com.rally26.common.error.ForbiddenException
import com.rally26.common.error.NotFoundException
import com.rally26.common.error.ValidationException
import com.rally26.common.web.CurrentUser
import com.rally26.invitation.domain.OwnershipTransferInvitation
import com.rally26.invitation.domain.OwnershipTransferInvitationStatus
import com.rally26.invitation.persistence.OwnershipTransferInvitationRepository
import com.rally26.membership.application.MembershipService
import com.rally26.outbox.application.OutboxWriter
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.UUID

private const val INVITATION_VALIDITY_DAYS = 7L

/** See V101's migration comment for why this is a separate table/flow from [Invitation]. */
@Service
class OwnershipTransferInvitationService(
    private val ownershipTransferInvitationRepository: OwnershipTransferInvitationRepository,
    private val membershipService: MembershipService,
    private val auditService: AuditService,
    private val outboxWriter: OutboxWriter,
) {
    data class CreatedOwnershipTransferInvitation(
        val invitation: OwnershipTransferInvitation,
        val rawToken: String,
    )

    /**
     * Owner only ([MembershipService.requireOwnerRole]). Unlike [MembershipService.transferOwnership]
     * (which requires the target to already be an Administrator member), this reaches
     * someone who may not be a member yet — accept handles both cases.
     */
    @Transactional
    fun invite(
        organizationId: UUID,
        email: String,
        currentUser: CurrentUser,
    ): CreatedOwnershipTransferInvitation {
        membershipService.requireOwnerRole(organizationId, currentUser)
        val normalizedEmail = email.trim().lowercase()
        if (normalizedEmail == currentUser.email.trim().lowercase()) {
            throw ValidationException("You are already the organization owner.")
        }
        if (ownershipTransferInvitationRepository.findPendingForOrganization(organizationId) != null) {
            throw ValidationException(
                "An ownership-transfer invitation is already pending for this organization. Revoke it before sending another.",
            )
        }

        val rawToken = generateToken()
        val tokenHash = sha256Hex(rawToken)
        val expiresAt = Instant.now().plus(Duration.ofDays(INVITATION_VALIDITY_DAYS))
        val invitation =
            ownershipTransferInvitationRepository.insert(
                organizationId = organizationId,
                email = normalizedEmail,
                invitedByUserId = currentUser.userId,
                tokenHash = tokenHash,
                expiresAt = expiresAt,
            )
        auditService.record(
            actorUserId = currentUser.userId,
            organizationId = organizationId,
            action = "membership.ownership_transfer_invited",
            entityType = "ownership_transfer_invitation",
            entityId = invitation.id,
        )
        outboxWriter.write(
            aggregateType = "ownership_transfer_invitation",
            aggregateId = invitation.id,
            organizationId = organizationId,
            eventType = "ownership_transfer.invitation_created",
            payloadJson = """{"invitationId":"${invitation.id}","email":"$normalizedEmail","acceptToken":"$rawToken"}""",
        )
        return CreatedOwnershipTransferInvitation(invitation, rawToken)
    }

    fun findPendingForOrganization(
        organizationId: UUID,
        currentUser: CurrentUser,
    ): OwnershipTransferInvitation? {
        membershipService.requireOwnerRole(organizationId, currentUser)
        return ownershipTransferInvitationRepository.findPendingForOrganization(organizationId)
    }

    @Transactional
    fun revoke(
        organizationId: UUID,
        invitationId: UUID,
        currentUser: CurrentUser,
    ) {
        membershipService.requireOwnerRole(organizationId, currentUser)
        val invitation =
            ownershipTransferInvitationRepository
                .findById(invitationId)
                ?.takeIf { it.organizationId == organizationId }
                ?: throw NotFoundException("OWNERSHIP_TRANSFER_INVITATION_NOT_FOUND", "The invitation could not be found.")
        if (invitation.status != OwnershipTransferInvitationStatus.PENDING) {
            throw ValidationException("Only pending invitations can be revoked.")
        }
        ownershipTransferInvitationRepository.markStatus(invitationId, OwnershipTransferInvitationStatus.REVOKED)
        auditService.record(
            currentUser.userId,
            organizationId,
            "membership.ownership_transfer_revoked",
            "ownership_transfer_invitation",
            invitationId,
        )
    }

    /**
     * The invitation's email must match the caller's own email — same guard as every
     * other invitation-accept flow in this codebase, stopping anyone but the intended
     * recipient from redeeming the token.
     */
    @Transactional
    fun accept(
        token: String,
        currentUser: CurrentUser,
    ): OwnershipTransferInvitation {
        val invitation =
            ownershipTransferInvitationRepository.findByTokenHash(sha256Hex(token))
                ?: throw NotFoundException("OWNERSHIP_TRANSFER_INVITATION_NOT_FOUND", "This invitation link is invalid.")

        if (invitation.status == OwnershipTransferInvitationStatus.PENDING && invitation.expiresAt.isBefore(Instant.now())) {
            ownershipTransferInvitationRepository.markStatus(invitation.id, OwnershipTransferInvitationStatus.EXPIRED)
            throw ValidationException("This invitation has expired.")
        }
        if (invitation.status != OwnershipTransferInvitationStatus.PENDING) {
            throw ValidationException("This invitation is no longer pending.")
        }
        if (!invitation.email.equals(currentUser.email.trim(), ignoreCase = true)) {
            throw ForbiddenException(
                code = "OWNERSHIP_TRANSFER_INVITATION_EMAIL_MISMATCH",
                message = "This invitation was sent to a different email address.",
            )
        }

        membershipService.finalizeOwnershipTransfer(invitation.organizationId, currentUser.userId)
        ownershipTransferInvitationRepository.markStatus(
            invitation.id,
            OwnershipTransferInvitationStatus.ACCEPTED,
            acceptedAt = Instant.now(),
        )
        return ownershipTransferInvitationRepository.findById(invitation.id)!!
    }

    private fun generateToken(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun sha256Hex(value: String): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
