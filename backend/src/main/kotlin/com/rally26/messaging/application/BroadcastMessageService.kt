package com.rally26.messaging.application

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
import com.rally26.communication.domain.AnnouncementRecipientCandidate
import com.rally26.communication.domain.AnnouncementRecipientType
import com.rally26.communication.domain.DeliveryStatus
import com.rally26.communication.persistence.AnnouncementRepository
import com.rally26.membership.application.MembershipService
import com.rally26.messaging.domain.BroadcastMessage
import com.rally26.messaging.domain.MessageAccessReason
import com.rally26.messaging.domain.MessageAudience
import com.rally26.messaging.domain.MessageRecipientCandidate
import com.rally26.messaging.domain.MessageRecipientType
import com.rally26.messaging.domain.MessageScopeType
import com.rally26.messaging.domain.MessageThread
import com.rally26.messaging.domain.MessageThreadStatus
import com.rally26.messaging.domain.MessageThreadType
import com.rally26.messaging.domain.MyBroadcastMessage
import com.rally26.messaging.domain.MyMessageThread
import com.rally26.messaging.persistence.MessageRepository
import com.rally26.outbox.application.OutboxWriter
import com.rally26.settings.application.NotificationDeliveryResolver
import com.rally26.settings.domain.NotificationDeliveryDecision
import com.rally26.settings.domain.NotificationTopic
import com.rally26.team.persistence.TeamRepository
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.util.UUID

@Service
class BroadcastMessageService(
    private val repository: MessageRepository,
    private val announcementRepository: AnnouncementRepository,
    private val membershipService: MembershipService,
    private val authorizationService: AuthorizationService,
    private val teamRepository: TeamRepository,
    private val outboxWriter: OutboxWriter,
    private val auditService: AuditService,
    private val objectMapper: ObjectMapper,
    private val clock: Clock,
    private val safeSportService: MessageSafeSportService? = null,
    private val notificationDeliveryResolver: NotificationDeliveryResolver? = null,
) {
    fun listForManagement(
        organizationId: UUID,
        scopeType: MessageScopeType?,
        scopeId: UUID?,
        status: MessageThreadStatus?,
        page: PageRequest,
        currentUser: CurrentUser,
    ): PageResponse<MessageThread> {
        requireListAccess(organizationId, scopeType, scopeId, currentUser)
        return PageResponse(
            repository.listForManagement(organizationId, scopeType, scopeId, status, page),
            page.page,
            page.size,
            repository.countForManagement(organizationId, scopeType, scopeId, status),
        )
    }

    @Transactional
    fun createThread(
        organizationId: UUID,
        scopeType: MessageScopeType,
        scopeId: UUID?,
        idempotencyKey: String,
        title: String,
        audience: MessageAudience,
        emailEnabled: Boolean,
        smsEnabled: Boolean,
        currentUser: CurrentUser,
    ): MessageThread {
        val input = BroadcastMessagePolicy.normalizeThread(title, idempotencyKey)
        val resolvedScopeId = resolveScope(organizationId, scopeType, scopeId)
        requireManage(organizationId, scopeType, resolvedScopeId, currentUser)
        repository.findThreadByIdempotencyKey(organizationId, input.idempotencyKey)?.let { existing ->
            if (existing.scopeType != scopeType ||
                existing.scopeId != resolvedScopeId ||
                existing.title != input.title ||
                existing.audience != audience ||
                existing.emailEnabled != emailEnabled ||
                existing.smsEnabled != smsEnabled
            ) {
                throw ConflictException(
                    "MESSAGE_THREAD_IDEMPOTENCY_CONFLICT",
                    "This idempotency key was already used for a different message thread.",
                )
            }
            return existing
        }
        val thread =
            try {
                repository.insertThread(
                    organizationId,
                    scopeType,
                    resolvedScopeId,
                    input.idempotencyKey,
                    input.title,
                    audience,
                    emailEnabled,
                    smsEnabled,
                    currentUser.userId,
                )
            } catch (_: DuplicateKeyException) {
                repository.findThreadByIdempotencyKey(organizationId, input.idempotencyKey)
                    ?: throw ConflictException("MESSAGE_THREAD_IDEMPOTENCY_CONFLICT", "The message thread could not be created safely.")
            }
        audit(
            currentUser,
            thread.organizationId,
            "message.thread.created",
            "message_thread",
            thread.id,
            mapOf(
                "scopeType" to scopeType.name,
                "audience" to audience.name,
            ),
        )
        return thread
    }

    fun getThread(
        organizationId: UUID,
        threadId: UUID,
        currentUser: CurrentUser,
    ): MessageThread {
        val thread = requireThread(organizationId, threadId)
        requireManage(organizationId, thread.scopeType, thread.scopeId, currentUser)
        return thread
    }

    @Transactional
    fun sendMessage(
        organizationId: UUID,
        threadId: UUID,
        idempotencyKey: String,
        body: String,
        currentUser: CurrentUser,
    ): BroadcastMessage {
        val thread = requireThread(organizationId, threadId)
        requireManage(organizationId, thread.scopeType, thread.scopeId, currentUser)
        if (thread.threadType != MessageThreadType.BROADCAST) {
            throw ConflictException("MESSAGE_THREAD_NOT_BROADCAST", "Two-way conversations must be replied to from the member inbox.")
        }
        if (thread.status != MessageThreadStatus.OPEN) {
            throw ConflictException("MESSAGE_THREAD_ARCHIVED", "Archived message threads cannot receive new messages.")
        }
        if (thread.safetyLockedAt != null) {
            throw ConflictException("MESSAGE_THREAD_SAFETY_LOCKED", "This message thread is temporarily locked for safety review.")
        }
        val input = BroadcastMessagePolicy.normalizeMessage(body, idempotencyKey)
        repository.findMessageByIdempotencyKey(threadId, input.idempotencyKey)?.let { existing ->
            if (existing.body != input.body) {
                throw ConflictException(
                    "MESSAGE_IDEMPOTENCY_CONFLICT",
                    "This idempotency key was already used for a different message.",
                )
            }
            return existing
        }

        val resolvedRecipients = resolveRecipients(thread)
        val policyFiltered =
            safeSportService?.filterAdultOriginRecipients(
                thread.organizationId,
                if (thread.scopeType == MessageScopeType.TEAM) thread.scopeId else null,
                resolvedRecipients,
            ) ?: resolvedRecipients
        val recipients = BroadcastRecipientPolicy.merge(policyFiltered)
        val resolvedDeliveries =
            recipients.mapValues { (_, candidate) ->
                val delivery =
                    notificationDeliveryResolver?.resolve(
                        userId = candidate.userId,
                        householdId = candidate.householdId,
                        topic = NotificationTopic.MESSAGES,
                        candidateEmail = candidate.email,
                        candidatePhone = candidate.phone,
                    ) ?: NotificationDeliveryDecision(
                        inApp = candidate.userId != null,
                        email = candidate.email,
                        sms = candidate.phone,
                    )
                candidate to delivery
            }
        val eligible =
            resolvedDeliveries.filterValues { (candidate, delivery) ->
                val requiredGuardianVisibility =
                    candidate.accessReason == MessageAccessReason.GUARDIAN_VISIBILITY && candidate.userId != null
                val externalDelivery = candidate.accessReason == MessageAccessReason.TARGETED
                requiredGuardianVisibility ||
                    delivery.inApp ||
                    (externalDelivery && thread.emailEnabled && !delivery.email.isNullOrBlank()) ||
                    (externalDelivery && thread.smsEnabled && !delivery.sms.isNullOrBlank())
            }
        if (eligible.isEmpty()) {
            throw ValidationException(
                "No eligible recipients have an enabled in-app, email, or consented SMS destination for this message.",
            )
        }

        val now = Instant.now(clock)
        val message =
            try {
                repository.insertMessage(organizationId, threadId, currentUser.userId, input.idempotencyKey, input.body, now)
            } catch (_: DuplicateKeyException) {
                repository.findMessageByIdempotencyKey(threadId, input.idempotencyKey)
                    ?: throw ConflictException("MESSAGE_IDEMPOTENCY_CONFLICT", "The message could not be sent safely.")
            }

        var inserted = 0
        for ((key, resolved) in eligible) {
            val (candidate, delivery) = resolved
            val requiredGuardianVisibility =
                candidate.accessReason == MessageAccessReason.GUARDIAN_VISIBILITY && candidate.userId != null
            val inApp = requiredGuardianVisibility || delivery.inApp
            val externalDelivery = candidate.accessReason == MessageAccessReason.TARGETED
            val emailStatus =
                if (externalDelivery && thread.emailEnabled && !delivery.email.isNullOrBlank()) {
                    DeliveryStatus.PENDING
                } else {
                    DeliveryStatus.NONE
                }
            val smsStatus =
                if (externalDelivery && thread.smsEnabled && !delivery.sms.isNullOrBlank()) {
                    DeliveryStatus.PENDING
                } else {
                    DeliveryStatus.NONE
                }
            val snapshotCandidate =
                if (externalDelivery) {
                    candidate.copy(email = delivery.email, phone = delivery.sms)
                } else {
                    candidate.copy(email = null, phone = null)
                }
            inserted += repository.insertRecipient(message.id, organizationId, key, snapshotCandidate, inApp, emailStatus, smsStatus)
        }
        if (inserted == 0) {
            // This can only happen on an idempotent race; the winning transaction owns the recipient snapshot.
            return repository.findMessageByIdempotencyKey(threadId, input.idempotencyKey) ?: message
        }

        outboxWriter.write(
            aggregateType = "message_entry",
            aggregateId = message.id,
            organizationId = organizationId,
            eventType = "message.broadcast_sent",
            payloadJson = objectMapper.writeValueAsString(mapOf("messageId" to message.id.toString(), "threadId" to thread.id.toString())),
        )
        audit(
            currentUser,
            organizationId,
            "message.broadcast_sent",
            "message_entry",
            message.id,
            mapOf(
                "threadId" to thread.id.toString(),
                "recipientCount" to inserted,
            ),
        )
        return repository.findMessageById(message.id, organizationId) ?: message
    }

    fun listMessagesForManagement(
        organizationId: UUID,
        threadId: UUID,
        page: PageRequest,
        currentUser: CurrentUser,
    ): PageResponse<BroadcastMessage> {
        val thread = requireThread(organizationId, threadId)
        requireManage(organizationId, thread.scopeType, thread.scopeId, currentUser)
        return PageResponse(
            repository.listMessagesForManagement(organizationId, threadId, page),
            page.page,
            page.size,
            repository.countMessagesForManagement(organizationId, threadId),
        )
    }

    @Transactional
    fun archiveThread(
        organizationId: UUID,
        threadId: UUID,
        currentUser: CurrentUser,
    ): MessageThread {
        val thread = requireThread(organizationId, threadId)
        requireManage(organizationId, thread.scopeType, thread.scopeId, currentUser)
        if (thread.status == MessageThreadStatus.ARCHIVED) return thread
        repository.archiveThread(threadId, organizationId, currentUser.userId, Instant.now(clock))
        val archived = requireThread(organizationId, threadId)
        audit(currentUser, organizationId, "message.thread.archived", "message_thread", threadId)
        return archived
    }

    fun listMine(
        currentUser: CurrentUser,
        page: PageRequest,
    ): PageResponse<MyMessageThread> =
        PageResponse(repository.listMine(currentUser.userId, page), page.page, page.size, repository.countMine(currentUser.userId))

    fun listMyMessages(
        currentUser: CurrentUser,
        threadId: UUID,
        page: PageRequest,
    ): PageResponse<MyBroadcastMessage> {
        val total = repository.countMyMessages(currentUser.userId, threadId)
        if (total == 0L) throw NotFoundException("MESSAGE_THREAD_NOT_FOUND", "The message thread could not be found.")
        return PageResponse(repository.listMyMessages(currentUser.userId, threadId, page), page.page, page.size, total)
    }

    fun markRead(
        currentUser: CurrentUser,
        messageId: UUID,
    ) {
        if (repository.markRead(messageId, currentUser.userId, Instant.now(clock)) == 0) {
            throw NotFoundException("MESSAGE_NOT_FOUND", "The message could not be found.")
        }
    }

    private fun resolveRecipients(thread: MessageThread): List<MessageRecipientCandidate> {
        val staff =
            when (thread.scopeType) {
                MessageScopeType.ORGANIZATION -> announcementRepository.listOrganizationStaff(thread.organizationId)
                MessageScopeType.TEAM -> announcementRepository.listTeamStaff(thread.organizationId, thread.scopeId)
            }
        val guardians =
            when (thread.scopeType) {
                MessageScopeType.ORGANIZATION -> announcementRepository.listOrganizationGuardians(thread.organizationId)
                MessageScopeType.TEAM -> announcementRepository.listTeamGuardians(thread.organizationId, thread.scopeId)
            }
        val athletes =
            when (thread.scopeType) {
                MessageScopeType.ORGANIZATION -> announcementRepository.listOrganizationAthletes(thread.organizationId)
                MessageScopeType.TEAM -> announcementRepository.listTeamAthletes(thread.organizationId, thread.scopeId)
            }

        val targeted =
            when (thread.audience) {
                MessageAudience.ALL -> staff + guardians + athletes
                MessageAudience.STAFF -> staff
                MessageAudience.GUARDIANS -> guardians
                MessageAudience.ATHLETES -> athletes
                MessageAudience.SELECTED -> throw ConflictException(
                    "MESSAGE_THREAD_NOT_BROADCAST",
                    "Selected-member conversations do not use broadcast audience resolution.",
                )
            }.map(::toMessageCandidate)

        if (thread.audience != MessageAudience.ATHLETES) return targeted
        val guardianVisibility =
            repository.listGuardianVisibilityCandidatesForActivatedAthletes(
                thread.organizationId,
                if (thread.scopeType == MessageScopeType.TEAM) thread.scopeId else null,
            )
        return targeted + guardianVisibility
    }

    private fun toMessageCandidate(candidate: AnnouncementRecipientCandidate): MessageRecipientCandidate =
        MessageRecipientCandidate(
            recipientType =
                when (candidate.recipientType) {
                    AnnouncementRecipientType.STAFF -> MessageRecipientType.STAFF
                    AnnouncementRecipientType.GUARDIAN -> MessageRecipientType.GUARDIAN
                    AnnouncementRecipientType.ATHLETE -> MessageRecipientType.ATHLETE
                },
            userId = candidate.userId,
            householdId = candidate.householdId,
            displayName = candidate.displayName,
            email = candidate.email,
            phone = candidate.phone,
        )

    private fun requireListAccess(
        organizationId: UUID,
        scopeType: MessageScopeType?,
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
        scopeType: MessageScopeType,
        scopeId: UUID,
        currentUser: CurrentUser,
    ) {
        when (scopeType) {
            MessageScopeType.ORGANIZATION -> membershipService.requireManagerRole(organizationId, currentUser)
            MessageScopeType.TEAM ->
                authorizationService.requireTeamCapability(
                    organizationId,
                    scopeId,
                    currentUser,
                    Capabilities.TEAM_COMMUNICATION_MANAGE,
                )
        }
    }

    private fun resolveScope(
        organizationId: UUID,
        scopeType: MessageScopeType,
        scopeId: UUID?,
    ): UUID =
        when (scopeType) {
            MessageScopeType.ORGANIZATION -> organizationId
            MessageScopeType.TEAM ->
                scopeId?.also {
                    teamRepository.findById(it, organizationId) ?: throw NotFoundException("TEAM_NOT_FOUND", "The team could not be found.")
                } ?: throw ValidationException("A team message thread requires scopeId.")
        }

    private fun requireThread(
        organizationId: UUID,
        threadId: UUID,
    ): MessageThread =
        repository.findThreadById(threadId, organizationId)
            ?: throw NotFoundException("MESSAGE_THREAD_NOT_FOUND", "The message thread could not be found.")

    private fun audit(
        currentUser: CurrentUser,
        organizationId: UUID,
        action: String,
        entityType: String,
        entityId: UUID,
        metadata: Map<String, Any> = emptyMap(),
    ) {
        auditService.record(currentUser.userId, organizationId, action, entityType, entityId, objectMapper.writeValueAsString(metadata))
    }
}
