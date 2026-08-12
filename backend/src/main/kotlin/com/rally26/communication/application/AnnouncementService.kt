package com.rally26.communication.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.rally26.audit.application.AuditService
import com.rally26.authorization.application.AuthorizationService
import com.rally26.authorization.domain.Capabilities
import com.rally26.common.error.ConflictException
import com.rally26.common.error.NotFoundException
import com.rally26.common.error.ValidationException
import com.rally26.common.web.CurrentUser
import com.rally26.common.web.PageRequest
import com.rally26.common.web.PageResponse
import com.rally26.communication.domain.Announcement
import com.rally26.communication.domain.AnnouncementAudience
import com.rally26.communication.domain.AnnouncementKind
import com.rally26.communication.domain.AnnouncementRecipientCandidate
import com.rally26.communication.domain.AnnouncementScopeType
import com.rally26.communication.domain.AnnouncementStatus
import com.rally26.communication.domain.DeliveryStatus
import com.rally26.communication.domain.MyAnnouncement
import com.rally26.communication.persistence.AnnouncementRepository
import com.rally26.membership.application.MembershipService
import com.rally26.outbox.application.OutboxWriter
import com.rally26.settings.application.NotificationDeliveryResolver
import com.rally26.settings.domain.NotificationDeliveryDecision
import com.rally26.settings.domain.NotificationTopic
import com.rally26.team.persistence.TeamRepository
import com.rally26.tournament.persistence.TournamentRepository
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.util.UUID

@Service
class AnnouncementService(
    private val repository: AnnouncementRepository,
    private val membershipService: MembershipService,
    private val authorizationService: AuthorizationService,
    private val teamRepository: TeamRepository,
    private val tournamentRepository: TournamentRepository,
    private val outboxWriter: OutboxWriter,
    private val auditService: AuditService,
    private val objectMapper: ObjectMapper,
    private val clock: Clock,
    private val notificationDeliveryResolver: NotificationDeliveryResolver? = null,
) {
    fun listForManagement(
        organizationId: UUID,
        scopeType: AnnouncementScopeType?,
        scopeId: UUID?,
        status: AnnouncementStatus?,
        page: PageRequest,
        currentUser: CurrentUser,
    ): PageResponse<Announcement> {
        requireListAccess(organizationId, scopeType, scopeId, currentUser)
        return PageResponse(
            repository.listForManagement(organizationId, scopeType, scopeId, status, page),
            page.page,
            page.size,
            repository.countForManagement(organizationId, scopeType, scopeId, status),
        )
    }

    fun get(
        organizationId: UUID,
        announcementId: UUID,
        currentUser: CurrentUser,
    ): Announcement {
        val announcement = requireAnnouncement(organizationId, announcementId)
        requireManage(announcement.organizationId, announcement.scopeType, announcement.scopeId, currentUser)
        return announcement
    }

    @Transactional
    fun createDraft(
        organizationId: UUID,
        scopeType: AnnouncementScopeType,
        scopeId: UUID?,
        idempotencyKey: String,
        title: String,
        body: String,
        audience: AnnouncementAudience,
        emailEnabled: Boolean,
        smsEnabled: Boolean,
        currentUser: CurrentUser,
    ): Announcement {
        val resolvedScopeId = resolveScope(organizationId, scopeType, scopeId)
        requireManage(organizationId, scopeType, resolvedScopeId, currentUser)
        validateAudience(scopeType, audience)
        val input = validate(title, body, idempotencyKey)
        val sourceKey = "GENERAL:${input.idempotencyKey}"
        repository.findBySourceKey(organizationId, sourceKey)?.let { existing ->
            if (existing.scopeType != scopeType ||
                existing.scopeId != resolvedScopeId ||
                existing.title != input.title ||
                existing.body != input.body
            ) {
                throw ConflictException(
                    "ANNOUNCEMENT_IDEMPOTENCY_CONFLICT",
                    "This idempotency key was already used for a different announcement.",
                )
            }
            return existing
        }
        return try {
            val announcement =
                repository.insert(
                    organizationId,
                    scopeType,
                    resolvedScopeId,
                    AnnouncementKind.GENERAL,
                    null,
                    null,
                    null,
                    sourceKey,
                    input.title,
                    input.body,
                    audience,
                    emailEnabled,
                    smsEnabled,
                    currentUser.userId,
                )
            audit(currentUser, announcement, "announcement.created")
            announcement
        } catch (_: DuplicateKeyException) {
            repository.findBySourceKey(organizationId, sourceKey)
                ?: throw ConflictException("ANNOUNCEMENT_IDEMPOTENCY_CONFLICT", "The announcement could not be created safely.")
        }
    }

    @Transactional
    fun updateDraft(
        organizationId: UUID,
        announcementId: UUID,
        title: String,
        body: String,
        audience: AnnouncementAudience,
        emailEnabled: Boolean,
        smsEnabled: Boolean,
        currentUser: CurrentUser,
    ): Announcement {
        val existing = requireAnnouncement(organizationId, announcementId)
        requireManage(organizationId, existing.scopeType, existing.scopeId, currentUser)
        validateAudience(existing.scopeType, audience)
        val input = validate(title, body, existing.sourceKey ?: "existing-key")
        if (existing.status !=
            AnnouncementStatus.DRAFT
        ) {
            throw ConflictException("ANNOUNCEMENT_NOT_DRAFT", "Only draft announcements can be edited.")
        }
        if (repository.updateDraft(announcementId, organizationId, input.title, input.body, audience, emailEnabled, smsEnabled) == 0) {
            throw ConflictException("ANNOUNCEMENT_NOT_DRAFT", "The announcement is no longer editable.")
        }
        val updated = requireAnnouncement(organizationId, announcementId)
        audit(currentUser, updated, "announcement.updated")
        return updated
    }

    @Transactional
    fun publish(
        organizationId: UUID,
        announcementId: UUID,
        currentUser: CurrentUser,
    ): Announcement {
        val existing = requireAnnouncement(organizationId, announcementId)
        requireManage(organizationId, existing.scopeType, existing.scopeId, currentUser)
        if (existing.status == AnnouncementStatus.PUBLISHED) return existing
        if (existing.status ==
            AnnouncementStatus.ARCHIVED
        ) {
            throw ConflictException("ANNOUNCEMENT_ARCHIVED", "Archived announcements cannot be published.")
        }
        return publishInternal(existing, currentUser)
    }

    @Transactional
    fun archive(
        organizationId: UUID,
        announcementId: UUID,
        currentUser: CurrentUser,
    ): Announcement {
        val existing = requireAnnouncement(organizationId, announcementId)
        requireManage(organizationId, existing.scopeType, existing.scopeId, currentUser)
        if (existing.status == AnnouncementStatus.ARCHIVED) return existing
        repository.archive(announcementId, organizationId, Instant.now(clock))
        val archived = requireAnnouncement(organizationId, announcementId)
        audit(currentUser, archived, "announcement.archived")
        return archived
    }

    fun listMine(
        currentUser: CurrentUser,
        page: PageRequest,
    ): PageResponse<MyAnnouncement> =
        PageResponse(
            repository.listMineSafe(currentUser.userId, page),
            page.page,
            page.size,
            repository.countMine(currentUser.userId),
        )

    fun markRead(
        currentUser: CurrentUser,
        announcementId: UUID,
    ) {
        if (repository.markRead(announcementId, currentUser.userId, Instant.now(clock)) == 0) {
            throw NotFoundException("ANNOUNCEMENT_NOT_FOUND", "The announcement could not be found.")
        }
    }

    @Transactional
    fun createSystemAndPublish(
        organizationId: UUID,
        scopeType: AnnouncementScopeType,
        scopeId: UUID,
        kind: AnnouncementKind,
        relatedEntityType: String,
        relatedEntityId: UUID,
        targetHouseholdId: UUID?,
        sourceKey: String,
        title: String,
        body: String,
        audience: AnnouncementAudience,
        emailEnabled: Boolean,
        smsEnabled: Boolean,
        currentUser: CurrentUser,
    ): Announcement {
        requireManage(organizationId, scopeType, scopeId, currentUser)
        validateAudience(scopeType, audience)
        val input = validate(title, body, sourceKey)
        repository.findBySourceKey(organizationId, sourceKey)?.let { return it }
        val created =
            try {
                repository.insert(
                    organizationId,
                    scopeType,
                    scopeId,
                    kind,
                    relatedEntityType,
                    relatedEntityId,
                    targetHouseholdId,
                    sourceKey,
                    input.title,
                    input.body,
                    audience,
                    emailEnabled,
                    smsEnabled,
                    currentUser.userId,
                )
            } catch (_: DuplicateKeyException) {
                repository.findBySourceKey(organizationId, sourceKey)
                    ?: throw ConflictException("REMINDER_IDEMPOTENCY_CONFLICT", "The reminder could not be created safely.")
            }
        audit(currentUser, created, "announcement.created")
        return publishInternal(created, currentUser)
    }

    private fun publishInternal(
        announcement: Announcement,
        currentUser: CurrentUser,
    ): Announcement {
        val candidates = resolveRecipients(announcement)
        val merged = mergeRecipients(candidates)
        val topic = notificationTopicFor(announcement.kind)
        var inserted = 0
        for ((key, candidate) in merged) {
            val delivery =
                notificationDeliveryResolver?.resolve(
                    userId = candidate.userId,
                    householdId = candidate.householdId,
                    topic = topic,
                    candidateEmail = candidate.email,
                    candidatePhone = candidate.phone,
                ) ?: NotificationDeliveryDecision(
                    inApp = candidate.userId != null,
                    email = candidate.email,
                    sms = candidate.phone,
                )
            val resolvedCandidate = candidate.copy(email = delivery.email, phone = delivery.sms)
            val emailStatus =
                if (announcement.emailEnabled &&
                    !delivery.email.isNullOrBlank()
                ) {
                    DeliveryStatus.PENDING
                } else {
                    DeliveryStatus.NONE
                }
            val smsStatus = if (announcement.smsEnabled && !delivery.sms.isNullOrBlank()) DeliveryStatus.PENDING else DeliveryStatus.NONE
            if (!delivery.inApp && emailStatus == DeliveryStatus.NONE && smsStatus == DeliveryStatus.NONE) continue
            repository.insertRecipient(
                announcement.id,
                announcement.organizationId,
                key,
                resolvedCandidate,
                delivery.inApp,
                emailStatus,
                smsStatus,
            )
            inserted++
        }
        if (inserted ==
            0
        ) {
            throw ValidationException(
                "No eligible recipients have an in-app, email, or opted-in SMS destination for this announcement.",
            )
        }
        val now = Instant.now(clock)
        if (repository.publish(announcement.id, announcement.organizationId, currentUser.userId, now) == 0) {
            return requireAnnouncement(announcement.organizationId, announcement.id)
        }
        outboxWriter.write(
            aggregateType = "announcement",
            aggregateId = announcement.id,
            organizationId = announcement.organizationId,
            eventType = "announcement.published",
            payloadJson =
                objectMapper.writeValueAsString(
                    mapOf(
                        "announcementId" to announcement.id.toString(),
                    ),
                ),
        )
        val published = requireAnnouncement(announcement.organizationId, announcement.id)
        audit(currentUser, published, "announcement.published", mapOf("recipientCount" to inserted))
        return published
    }

    private fun resolveRecipients(announcement: Announcement): List<AnnouncementRecipientCandidate> {
        if (announcement.targetHouseholdId != null) {
            return if (announcement.kind == AnnouncementKind.DOCUMENT_REMINDER && announcement.relatedEntityId != null) {
                repository.listUnacknowledgedDocumentGuardians(
                    announcement.organizationId,
                    announcement.targetHouseholdId,
                    announcement.relatedEntityId,
                )
            } else {
                repository.listHouseholdGuardians(announcement.organizationId, announcement.targetHouseholdId)
            }
        }
        val staff =
            when (announcement.scopeType) {
                AnnouncementScopeType.ORGANIZATION -> repository.listOrganizationStaff(announcement.organizationId)
                AnnouncementScopeType.TEAM -> repository.listTeamStaff(announcement.organizationId, announcement.scopeId)
                AnnouncementScopeType.TOURNAMENT -> repository.listTournamentStaff(announcement.organizationId, announcement.scopeId)
            }
        val guardians =
            when (announcement.scopeType) {
                AnnouncementScopeType.ORGANIZATION -> repository.listOrganizationGuardians(announcement.organizationId)
                AnnouncementScopeType.TEAM -> repository.listTeamGuardians(announcement.organizationId, announcement.scopeId)
                AnnouncementScopeType.TOURNAMENT -> emptyList()
            }
        val athletes =
            when (announcement.scopeType) {
                AnnouncementScopeType.ORGANIZATION -> repository.listOrganizationAthletes(announcement.organizationId)
                AnnouncementScopeType.TEAM -> repository.listTeamAthletes(announcement.organizationId, announcement.scopeId)
                AnnouncementScopeType.TOURNAMENT -> emptyList()
            }
        return when (announcement.audience) {
            AnnouncementAudience.ALL -> staff + guardians + athletes
            AnnouncementAudience.STAFF -> staff
            AnnouncementAudience.GUARDIANS -> guardians
            AnnouncementAudience.ATHLETES -> athletes
        }
    }

    private fun notificationTopicFor(kind: AnnouncementKind): NotificationTopic =
        when (kind) {
            AnnouncementKind.GENERAL -> NotificationTopic.ANNOUNCEMENTS
            AnnouncementKind.CAMPAIGN_LAUNCH -> NotificationTopic.FUNDRAISING
            AnnouncementKind.EVENT_REMINDER -> NotificationTopic.EVENTS_SCHEDULE
            AnnouncementKind.FEE_REMINDER -> NotificationTopic.FEES_PAYMENTS
            AnnouncementKind.DOCUMENT_REMINDER, AnnouncementKind.ELIGIBILITY_REMINDER -> NotificationTopic.DOCUMENTS_ELIGIBILITY
        }

    private fun mergeRecipients(candidates: List<AnnouncementRecipientCandidate>): Map<String, AnnouncementRecipientCandidate> {
        val merged = linkedMapOf<String, AnnouncementRecipientCandidate>()
        for (candidate in candidates) {
            val key =
                when {
                    candidate.userId != null -> "user:${candidate.userId}"
                    !candidate.email.isNullOrBlank() -> "email:${candidate.email.trim().lowercase()}"
                    !candidate.phone.isNullOrBlank() -> "phone:${candidate.phone.trim()}"
                    else -> continue
                }
            val prior = merged[key]
            merged[key] =
                if (prior == null) {
                    candidate
                } else {
                    prior.copy(
                        userId = prior.userId ?: candidate.userId,
                        householdId = prior.householdId ?: candidate.householdId,
                        email = prior.email ?: candidate.email,
                        phone = prior.phone ?: candidate.phone,
                    )
                }
        }
        return merged
    }

    private fun requireListAccess(
        organizationId: UUID,
        scopeType: AnnouncementScopeType?,
        scopeId: UUID?,
        currentUser: CurrentUser,
    ) {
        if (scopeType == null || scopeId == null) {
            membershipService.requireManagerRole(organizationId, currentUser)
        } else {
            requireManage(organizationId, scopeType, scopeId, currentUser)
        }
    }

    private fun requireManage(
        organizationId: UUID,
        scopeType: AnnouncementScopeType,
        scopeId: UUID,
        currentUser: CurrentUser,
    ) {
        when (scopeType) {
            AnnouncementScopeType.ORGANIZATION -> membershipService.requireManagerRole(organizationId, currentUser)
            AnnouncementScopeType.TEAM ->
                authorizationService.requireTeamCapability(
                    organizationId,
                    scopeId,
                    currentUser,
                    Capabilities.TEAM_COMMUNICATION_MANAGE,
                )
            AnnouncementScopeType.TOURNAMENT ->
                authorizationService.requireTournamentCapability(
                    organizationId,
                    scopeId,
                    currentUser,
                    Capabilities.TOURNAMENT_COMMUNICATION_MANAGE,
                )
        }
    }

    private fun resolveScope(
        organizationId: UUID,
        scopeType: AnnouncementScopeType,
        scopeId: UUID?,
    ): UUID =
        when (scopeType) {
            AnnouncementScopeType.ORGANIZATION -> organizationId
            AnnouncementScopeType.TEAM ->
                scopeId?.also {
                    teamRepository.findById(it, organizationId) ?: throw NotFoundException("TEAM_NOT_FOUND", "The team could not be found.")
                } ?: throw ValidationException("A team scope requires scopeId.")
            AnnouncementScopeType.TOURNAMENT ->
                scopeId?.also {
                    tournamentRepository.findById(it, organizationId)
                        ?: throw NotFoundException("TOURNAMENT_NOT_FOUND", "The tournament could not be found.")
                } ?: throw ValidationException("A tournament scope requires scopeId.")
        }

    private fun requireAnnouncement(
        organizationId: UUID,
        id: UUID,
    ) = repository.findById(id, organizationId)
        ?: throw NotFoundException("ANNOUNCEMENT_NOT_FOUND", "The announcement could not be found.")

    private fun validateAudience(
        scopeType: AnnouncementScopeType,
        audience: AnnouncementAudience,
    ) {
        if (scopeType == AnnouncementScopeType.TOURNAMENT && audience != AnnouncementAudience.STAFF) {
            throw ValidationException(
                "Tournament announcements currently support STAFF only because participating-team relationships are not modeled.",
            )
        }
    }

    private fun validate(
        title: String,
        body: String,
        idempotencyKey: String,
    ): Input {
        val normalizedTitle = title.trim()
        val normalizedBody = body.trim()
        val normalizedKey = idempotencyKey.trim()
        if (normalizedTitle.length !in 3..180) throw ValidationException("Announcement title must be between 3 and 180 characters.")
        if (normalizedBody.length !in 10..5000) throw ValidationException("Announcement body must be between 10 and 5,000 characters.")
        if (normalizedKey.length !in 8..160) throw ValidationException("Idempotency key must be between 8 and 160 characters.")
        return Input(normalizedTitle, normalizedBody, normalizedKey)
    }

    private fun audit(
        currentUser: CurrentUser,
        announcement: Announcement,
        action: String,
        extra: Map<String, Any?> = emptyMap(),
    ) = auditService.record(
        currentUser.userId,
        announcement.organizationId,
        action,
        "ANNOUNCEMENT",
        announcement.id,
        objectMapper.writeValueAsString(
            mapOf(
                "scopeType" to announcement.scopeType.name,
                "scopeId" to announcement.scopeId.toString(),
                "kind" to announcement.kind.name,
                "audience" to announcement.audience.name,
                "status" to announcement.status.name,
            ) + extra,
        ),
    )

    private data class Input(
        val title: String,
        val body: String,
        val idempotencyKey: String,
    )
}
