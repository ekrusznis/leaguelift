package com.rally26.messaging.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.rally26.audit.application.AuditService
import com.rally26.common.error.ConflictException
import com.rally26.common.error.ForbiddenException
import com.rally26.common.error.NotFoundException
import com.rally26.common.web.CurrentUser
import com.rally26.membership.application.MembershipService
import com.rally26.messaging.domain.ConversationContact
import com.rally26.messaging.domain.GuardianMessagingParticipant
import com.rally26.messaging.domain.MessageContactRestriction
import com.rally26.messaging.domain.MessageContactRestrictionKind
import com.rally26.messaging.domain.MessageRecipientCandidate
import com.rally26.messaging.domain.MessageRecipientType
import com.rally26.messaging.domain.MessageSafeSportPolicy
import com.rally26.messaging.domain.MessageSafeSportReviewStatus
import com.rally26.messaging.domain.MessageThreadMember
import com.rally26.messaging.persistence.MessageSafeSportRepository
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.util.UUID

@Service
class MessageSafeSportService(
    private val repository: MessageSafeSportRepository,
    private val membershipService: MembershipService,
    private val auditService: AuditService,
    private val objectMapper: ObjectMapper,
    private val clock: Clock,
) {
    fun getPolicy(
        organizationId: UUID,
        currentUser: CurrentUser,
    ): MessageSafeSportPolicy {
        if (!currentUser.platformAdministrator) membershipService.requireManagerRole(organizationId, currentUser)
        repository.ensurePolicy(organizationId)
        return repository.findPolicy(organizationId) ?: error("Messaging SafeSport policy was not found.")
    }

    @Transactional
    fun recordReview(
        organizationId: UUID,
        reviewStatus: MessageSafeSportReviewStatus,
        athleteMessagingEnabled: Boolean,
        reviewReference: String?,
        currentUser: CurrentUser,
    ): MessageSafeSportPolicy {
        if (!currentUser.platformAdministrator) {
            throw ForbiddenException(
                "PLATFORM_ADMIN_REQUIRED",
                "Only a Rally26 platform administrator may record the athlete-messaging review gate.",
            )
        }
        val reference = MessageSafeSportPolicyRules.normalizeReviewReference(reviewStatus, reviewReference)
        MessageSafeSportPolicyRules.requireEnablementAllowed(reviewStatus, athleteMessagingEnabled, reference)
        repository.ensurePolicy(organizationId)
        val now = Instant.now(clock)
        repository.updatePolicy(
            organizationId,
            reviewStatus,
            athleteMessagingEnabled,
            reference,
            if (reviewStatus == MessageSafeSportReviewStatus.PENDING) null else currentUser.userId,
            if (reviewStatus == MessageSafeSportReviewStatus.PENDING) null else now,
            now,
        )
        auditService.record(
            currentUser.userId,
            organizationId,
            "message.safe_sport.review_recorded",
            "message_safe_sport_policy",
            organizationId,
            objectMapper.writeValueAsString(
                mapOf(
                    "reviewStatus" to reviewStatus.name,
                    "athleteMessagingEnabled" to athleteMessagingEnabled,
                    "reviewReference" to reference,
                ),
            ),
        )
        return repository.findPolicy(organizationId) ?: error("Messaging SafeSport policy was not found after update.")
    }

    fun listGuardianParticipants(currentUser: CurrentUser): List<GuardianMessagingParticipant> =
        repository.listGuardianParticipants(currentUser.userId)

    fun listMyRestrictions(currentUser: CurrentUser): List<MessageContactRestriction> = repository.listMine(currentUser.userId)

    @Transactional
    fun createRestriction(
        organizationId: UUID,
        participantId: UUID,
        kind: MessageContactRestrictionKind,
        note: String?,
        currentUser: CurrentUser,
    ): MessageContactRestriction {
        if (!repository.isGuardianForParticipant(currentUser.userId, organizationId, participantId)) {
            throw NotFoundException("PARTICIPANT_NOT_FOUND", "The athlete could not be found in your household.")
        }
        repository.findActiveRestriction(organizationId, participantId, kind)?.let { return it }
        val normalizedNote = MessageSafeSportPolicyRules.normalizeNote(note)
        val now = Instant.now(clock)
        val restriction =
            try {
                repository.insertRestriction(organizationId, participantId, currentUser.userId, kind, normalizedNote, now)
            } catch (_: DuplicateKeyException) {
                repository.findActiveRestriction(organizationId, participantId, kind)
                    ?: throw ConflictException(
                        "MESSAGE_CONTACT_RESTRICTION_CONFLICT",
                        "The communication restriction could not be recorded safely.",
                    )
            }
        auditService.record(
            currentUser.userId,
            organizationId,
            "message.contact_restriction.created",
            "message_contact_restriction",
            restriction.id,
            objectMapper.writeValueAsString(mapOf("participantId" to participantId.toString(), "kind" to kind.name)),
        )
        return restriction
    }

    @Transactional
    fun liftRestriction(
        restrictionId: UUID,
        note: String,
        currentUser: CurrentUser,
    ): MessageContactRestriction {
        val existing =
            repository.findRestriction(restrictionId)
                ?: throw NotFoundException("MESSAGE_CONTACT_RESTRICTION_NOT_FOUND", "The communication restriction could not be found.")
        if (existing.requestedByUserId !=
            currentUser.userId
        ) {
            throw NotFoundException("MESSAGE_CONTACT_RESTRICTION_NOT_FOUND", "The communication restriction could not be found.")
        }
        if (existing.status.name == "LIFTED") return existing
        val normalized =
            MessageSafeSportPolicyRules.normalizeNote(note)?.takeIf { it.length >= 3 }
                ?: throw ConflictException(
                    "MESSAGE_CONTACT_RESTRICTION_NOTE_REQUIRED",
                    "A short note is required when lifting a communication restriction.",
                )
        repository.liftRestriction(restrictionId, currentUser.userId, normalized, Instant.now(clock))
        val updated = repository.findRestriction(restrictionId) ?: existing
        auditService.record(
            currentUser.userId,
            existing.organizationId,
            "message.contact_restriction.lifted",
            "message_contact_restriction",
            existing.id,
            objectMapper.writeValueAsString(mapOf("participantId" to existing.participantId.toString(), "kind" to existing.kind.name)),
        )
        return updated
    }

    fun filterAdultOriginContacts(
        organizationId: UUID,
        teamId: UUID,
        contacts: List<ConversationContact>,
    ): List<ConversationContact> {
        val restricted = repository.listRestrictedAthleteUserIds(organizationId, teamId, includeAdultOnly = true)
        return contacts.filterNot { it.recipientType == MessageRecipientType.ATHLETE && it.userId in restricted }
    }

    fun filterAdultOriginRecipients(
        organizationId: UUID,
        teamId: UUID?,
        candidates: List<MessageRecipientCandidate>,
    ): List<MessageRecipientCandidate> {
        val restricted = repository.listRestrictedAthleteUserIds(organizationId, teamId, includeAdultOnly = true)
        return candidates.filterNot { it.recipientType == MessageRecipientType.ATHLETE && it.userId != null && it.userId in restricted }
    }

    fun requireAdultOriginAllowedForMembers(
        organizationId: UUID,
        teamId: UUID,
        members: List<MessageThreadMember>,
    ) {
        val restricted = repository.listRestrictedAthleteUserIds(organizationId, teamId, includeAdultOnly = true)
        if (members.any { it.memberType == MessageRecipientType.ATHLETE && it.userId in restricted }) {
            throw ConflictException(
                "ATHLETE_MESSAGE_CONTACT_RESTRICTED",
                "A guardian communication restriction prevents staff from messaging one or more athletes in this conversation.",
            )
        }
    }

    fun requireAthleteMessagingEnabled(organizationId: UUID) {
        repository.ensurePolicy(organizationId)
        val policy = repository.findPolicy(organizationId) ?: error("Messaging SafeSport policy was not found.")
        if (policy.reviewStatus != MessageSafeSportReviewStatus.APPROVED || !policy.athleteMessagingEnabled) {
            throw ConflictException(
                "ATHLETE_MESSAGING_REVIEW_REQUIRED",
                "Athlete-created messaging is disabled until the SafeSport/compliance review gate is approved and enabled.",
            )
        }
    }

    fun restrictedAthleteUserIdsForAllMessaging(
        organizationId: UUID,
        teamId: UUID,
    ): Set<UUID> = repository.listRestrictedAthleteUserIds(organizationId, teamId, includeAdultOnly = false)
}
