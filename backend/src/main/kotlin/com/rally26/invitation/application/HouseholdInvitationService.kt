package com.rally26.invitation.application

import com.rally26.audit.application.AuditService
import com.rally26.authorization.application.AuthorizationService
import com.rally26.authorization.domain.Capabilities
import com.rally26.authorization.domain.ResourceRole
import com.rally26.authorization.domain.RoleAssignmentContextType
import com.rally26.authorization.persistence.GuardianRelationshipRepository
import com.rally26.authorization.persistence.RoleAssignmentRepository
import com.rally26.common.error.ForbiddenException
import com.rally26.common.error.NotFoundException
import com.rally26.common.error.ValidationException
import com.rally26.common.web.CurrentUser
import com.rally26.household.domain.HouseholdAdult
import com.rally26.household.persistence.HouseholdRepository
import com.rally26.invitation.domain.HouseholdInvitation
import com.rally26.invitation.domain.HouseholdInvitationKind
import com.rally26.invitation.domain.HouseholdInvitationStatus
import com.rally26.invitation.persistence.HouseholdInvitationRepository
import com.rally26.membership.application.MembershipService
import com.rally26.outbox.application.OutboxWriter
import com.rally26.participant.domain.Participant
import com.rally26.participant.persistence.ParticipantRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.util.Base64
import java.util.UUID

private const val INVITATION_VALIDITY_DAYS = 7L

/**
 * COPPA-aligned default for "old enough to have their own login" — no existing per-athlete
 * age policy exists anywhere in this codebase to reuse (confirmed: `participant.date_of_birth`
 * is stored but never read to compute an age anywhere today). Founder-adjustable; not derived
 * from any other product decision.
 */
const val MINIMUM_ATHLETE_SELF_LOGIN_AGE = 13

@Service
class HouseholdInvitationService(
    private val householdInvitationRepository: HouseholdInvitationRepository,
    private val householdRepository: HouseholdRepository,
    private val participantRepository: ParticipantRepository,
    private val guardianRelationshipRepository: GuardianRelationshipRepository,
    private val roleAssignmentRepository: RoleAssignmentRepository,
    private val membershipService: MembershipService,
    private val authorizationService: AuthorizationService,
    private val auditService: AuditService,
    private val outboxWriter: OutboxWriter,
) {
    data class CreatedHouseholdInvitation(
        val invitation: HouseholdInvitation,
        val rawToken: String,
    )

    /**
     * Owner/Administrator, or a coach with roster-manage capability on one of this
     * athlete's active teams — matches the existing `TEAM_ROSTER_MANAGE` capability
     * already used elsewhere for roster mutations, extended here to guardian invites
     * since no existing household-authorization path considers team-scoped coach access
     * at all (`AuthorizationService.hasHouseholdCapability` only looks at org membership
     * or an already-active guardian relationship). Athlete comes first: this always
     * starts from a real, existing participant record, never a bare household.
     */
    @Transactional
    fun inviteGuardian(
        organizationId: UUID,
        participantId: UUID,
        firstName: String,
        lastName: String,
        email: String,
        relationship: String?,
        currentUser: CurrentUser,
    ): CreatedHouseholdInvitation {
        val participant = requireParticipant(organizationId, participantId)
        requireCanManageParticipantGuardians(organizationId, participant, currentUser)

        val normalizedEmail = email.trim().lowercase()
        val household =
            householdRepository.findById(participant.householdId, organizationId)
                ?: throw NotFoundException("HOUSEHOLD_NOT_FOUND", "The household could not be found.")
        val adult = findOrCreateAdult(household.id, organizationId, firstName, lastName, normalizedEmail, relationship)

        if (guardianRelationshipRepository.isAdultLinkedToAnyUser(adult.id)) {
            throw ValidationException("This contact is already linked to a Rally26 account.")
        }
        if (householdInvitationRepository.listPendingForHousehold(household.id, organizationId).any {
                it.kind == HouseholdInvitationKind.GUARDIAN && it.householdAdultId == adult.id
            }
        ) {
            throw ValidationException("A guardian invitation is already pending for this contact.")
        }

        return createInvitation(
            organizationId = organizationId,
            householdId = household.id,
            kind = HouseholdInvitationKind.GUARDIAN,
            householdAdultId = adult.id,
            participantId = participant.id,
            email = normalizedEmail,
            currentUser = currentUser,
        )
    }

    /**
     * Only an active guardian of this participant's household can invite the athlete —
     * deliberately narrower than [com.rally26.authorization.application.AuthorizationService.linkAthleteSelf]'s
     * own guardian-or-org-manager check, which still governs who can *finalize* the
     * grant on accept. Requires a recorded date of birth and blocks anyone under
     * [MINIMUM_ATHLETE_SELF_LOGIN_AGE].
     */
    @Transactional
    fun inviteAthlete(
        organizationId: UUID,
        participantId: UUID,
        email: String,
        currentUser: CurrentUser,
    ): CreatedHouseholdInvitation {
        val participant = requireParticipant(organizationId, participantId)
        if (guardianRelationshipRepository.findActiveForHousehold(currentUser.userId, participant.householdId) == null) {
            throw ForbiddenException(
                "HOUSEHOLD_ATHLETE_INVITE_DENIED",
                "Only an active guardian of this athlete's household can invite them.",
            )
        }
        val dateOfBirth = participant.dateOfBirth ?: throw ValidationException("Add this athlete's date of birth before inviting them.")
        val age = Period.between(dateOfBirth, LocalDate.now()).years
        if (age < MINIMUM_ATHLETE_SELF_LOGIN_AGE) {
            throw ValidationException("This athlete must be at least $MINIMUM_ATHLETE_SELF_LOGIN_AGE to have their own Rally26 login.")
        }
        if (roleAssignmentRepository.hasAnyActiveForResourceAndRole(
                RoleAssignmentContextType.PARTICIPANT,
                participant.id,
                ResourceRole.ATHLETE_SELF,
            )
        ) {
            throw ValidationException("This athlete already has their own Rally26 login.")
        }
        val normalizedEmail = email.trim().lowercase()
        if (householdInvitationRepository.listPendingForHousehold(participant.householdId, organizationId).any {
                it.kind == HouseholdInvitationKind.ATHLETE && it.participantId == participant.id
            }
        ) {
            throw ValidationException("An athlete invitation is already pending for this participant.")
        }

        return createInvitation(
            organizationId = organizationId,
            householdId = participant.householdId,
            kind = HouseholdInvitationKind.ATHLETE,
            householdAdultId = null,
            participantId = participant.id,
            email = normalizedEmail,
            currentUser = currentUser,
        )
    }

    fun listPendingForHousehold(
        organizationId: UUID,
        householdId: UUID,
        currentUser: CurrentUser,
    ): List<HouseholdInvitation> {
        if (!authorizationService.hasHouseholdCapability(organizationId, householdId, currentUser, Capabilities.HOUSEHOLD_VIEW)) {
            throw ForbiddenException("HOUSEHOLD_ACCESS_DENIED", "You do not have access to this household.")
        }
        return householdInvitationRepository.listPendingForHousehold(householdId, organizationId)
    }

    @Transactional
    fun revoke(
        organizationId: UUID,
        invitationId: UUID,
        currentUser: CurrentUser,
    ) {
        val invitation =
            householdInvitationRepository
                .findById(invitationId)
                ?.takeIf { it.organizationId == organizationId }
                ?: throw NotFoundException("HOUSEHOLD_INVITATION_NOT_FOUND", "The invitation could not be found.")
        val participant = requireParticipant(organizationId, invitation.participantId)
        if (invitation.kind == HouseholdInvitationKind.GUARDIAN) {
            requireCanManageParticipantGuardians(organizationId, participant, currentUser)
        } else if (guardianRelationshipRepository.findActiveForHousehold(currentUser.userId, invitation.householdId) == null &&
            !membershipService.hasManagerRole(organizationId, currentUser)
        ) {
            throw ForbiddenException("HOUSEHOLD_INVITATION_REVOKE_DENIED", "You cannot revoke this invitation.")
        }
        if (invitation.status != HouseholdInvitationStatus.PENDING) {
            throw ValidationException("Only pending invitations can be revoked.")
        }
        householdInvitationRepository.markStatus(invitationId, HouseholdInvitationStatus.REVOKED)
        auditService.record(currentUser.userId, organizationId, "household.invitation_revoked", "household_invitation", invitationId)
    }

    /**
     * The invitation's email must match the caller's own email, exactly as the existing
     * staff-invitation `InvitationService.accept` requires — this is what stops any
     * authenticated user from redeeming a token meant for someone else.
     */
    @Transactional
    fun accept(
        token: String,
        currentUser: CurrentUser,
    ): HouseholdInvitation {
        val invitation =
            householdInvitationRepository.findByTokenHash(sha256Hex(token))
                ?: throw NotFoundException("HOUSEHOLD_INVITATION_NOT_FOUND", "This invitation link is invalid.")

        if (invitation.status == HouseholdInvitationStatus.PENDING && invitation.expiresAt.isBefore(Instant.now())) {
            householdInvitationRepository.markStatus(invitation.id, HouseholdInvitationStatus.EXPIRED)
            throw ValidationException("This invitation has expired.")
        }
        if (invitation.status != HouseholdInvitationStatus.PENDING) {
            throw ValidationException("This invitation is no longer pending.")
        }
        if (!invitation.email.equals(currentUser.email.trim(), ignoreCase = true)) {
            throw ForbiddenException(
                code = "HOUSEHOLD_INVITATION_EMAIL_MISMATCH",
                message = "This invitation was sent to a different email address.",
            )
        }

        when (invitation.kind) {
            HouseholdInvitationKind.GUARDIAN -> {
                val adultId =
                    invitation.householdAdultId
                        ?: error(
                            "household_invitation ${invitation.id} is GUARDIAN with no household_adult_id — violates the DB check constraint",
                        )
                guardianRelationshipRepository.insert(invitation.organizationId, invitation.householdId, adultId, currentUser.userId)
            }
            HouseholdInvitationKind.ATHLETE -> {
                authorizationService.linkAthleteSelf(
                    organizationId = invitation.organizationId,
                    householdId = invitation.householdId,
                    participantId = invitation.participantId,
                    athleteUserId = currentUser.userId,
                    grantedByUserId = invitation.invitedByUserId,
                )
            }
        }

        householdInvitationRepository.markStatus(invitation.id, HouseholdInvitationStatus.ACCEPTED, acceptedAt = Instant.now())
        auditService.record(
            actorUserId = currentUser.userId,
            organizationId = invitation.organizationId,
            action = "household.invitation_accepted",
            entityType = "household_invitation",
            entityId = invitation.id,
        )
        return householdInvitationRepository.findById(invitation.id)!!
    }

    private fun createInvitation(
        organizationId: UUID,
        householdId: UUID,
        kind: HouseholdInvitationKind,
        householdAdultId: UUID?,
        participantId: UUID,
        email: String,
        currentUser: CurrentUser,
    ): CreatedHouseholdInvitation {
        val rawToken = generateToken()
        val tokenHash = sha256Hex(rawToken)
        val expiresAt = Instant.now().plus(Duration.ofDays(INVITATION_VALIDITY_DAYS))
        val invitation =
            householdInvitationRepository.insert(
                organizationId = organizationId,
                householdId = householdId,
                kind = kind,
                householdAdultId = householdAdultId,
                participantId = participantId,
                email = email,
                invitedByUserId = currentUser.userId,
                tokenHash = tokenHash,
                expiresAt = expiresAt,
            )
        auditService.record(
            actorUserId = currentUser.userId,
            organizationId = organizationId,
            action = if (kind == HouseholdInvitationKind.GUARDIAN) "household.guardian_invited" else "household.athlete_invited",
            entityType = "household_invitation",
            entityId = invitation.id,
        )
        outboxWriter.write(
            aggregateType = "household_invitation",
            aggregateId = invitation.id,
            organizationId = organizationId,
            eventType = "household.invitation_created",
            payloadJson = """{"invitationId":"${invitation.id}","email":"$email","kind":"${kind.name}","acceptToken":"$rawToken"}""",
        )
        return CreatedHouseholdInvitation(invitation, rawToken)
    }

    private fun findOrCreateAdult(
        householdId: UUID,
        organizationId: UUID,
        firstName: String,
        lastName: String,
        email: String,
        relationship: String?,
    ): HouseholdAdult {
        val existing = householdRepository.listAdults(householdId, organizationId).find { it.email?.lowercase() == email }
        if (existing != null) return existing
        return householdRepository.insertAdult(
            householdId = householdId,
            organizationId = organizationId,
            firstName = firstName.trim(),
            lastName = lastName.trim(),
            email = email,
            phone = null,
            relationship = relationship?.trim(),
            isPrimary = false,
        )
    }

    private fun requireParticipant(
        organizationId: UUID,
        participantId: UUID,
    ): Participant =
        participantRepository.findById(participantId, organizationId)
            ?: throw NotFoundException("PARTICIPANT_NOT_FOUND", "The athlete could not be found.")

    private fun requireCanManageParticipantGuardians(
        organizationId: UUID,
        participant: Participant,
        currentUser: CurrentUser,
    ) {
        if (currentUser.platformAdministrator || membershipService.hasManagerRole(organizationId, currentUser)) return
        val teamIds = participantRepository.listTeamAssignments(participant.id, organizationId).map { it.teamId }
        val canManageAnyTeam =
            teamIds.any { teamId ->
                authorizationService.hasTeamCapability(organizationId, teamId, currentUser, Capabilities.TEAM_ROSTER_MANAGE)
            }
        if (!canManageAnyTeam) {
            throw ForbiddenException(
                "HOUSEHOLD_GUARDIAN_INVITE_DENIED",
                "You do not have access to invite a guardian for this athlete.",
            )
        }
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
