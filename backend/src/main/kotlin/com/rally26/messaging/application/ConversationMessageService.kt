package com.rally26.messaging.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.rally26.audit.application.AuditService
import com.rally26.authorization.application.AuthorizationService
import com.rally26.authorization.domain.Capabilities
import com.rally26.common.error.ConflictException
import com.rally26.common.error.ForbiddenException
import com.rally26.common.error.NotFoundException
import com.rally26.common.error.ValidationException
import com.rally26.common.web.CurrentUser
import com.rally26.communication.domain.DeliveryStatus
import com.rally26.messaging.domain.AthleteMessagingTeam
import com.rally26.messaging.domain.BroadcastMessage
import com.rally26.messaging.domain.ConversationContact
import com.rally26.messaging.domain.MessageAccessReason
import com.rally26.messaging.domain.MessageRecipientType
import com.rally26.messaging.domain.MessageThread
import com.rally26.messaging.domain.MessageThreadMember
import com.rally26.messaging.domain.MessageThreadMemberCandidate
import com.rally26.messaging.domain.MessageThreadStatus
import com.rally26.messaging.domain.MessageThreadType
import com.rally26.messaging.persistence.MessageRepository
import com.rally26.outbox.application.OutboxWriter
import com.rally26.team.persistence.TeamRepository
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.util.UUID

@Service
class ConversationMessageService(
    private val repository: MessageRepository,
    private val authorizationService: AuthorizationService,
    private val teamRepository: TeamRepository,
    private val outboxWriter: OutboxWriter,
    private val auditService: AuditService,
    private val objectMapper: ObjectMapper,
    private val clock: Clock,
    private val safeSportService: MessageSafeSportService? = null,
    private val membershipReconciliationService: MessageMembershipReconciliationService? = null,
) {
    fun listContacts(
        organizationId: UUID,
        teamId: UUID,
        currentUser: CurrentUser,
    ): List<ConversationContact> {
        requireTeamManage(organizationId, teamId, currentUser)
        val contacts = repository.listConversationContacts(organizationId, teamId)
        return safeSportService?.filterAdultOriginContacts(organizationId, teamId, contacts) ?: contacts
    }

    @Transactional
    fun createConversation(
        organizationId: UUID,
        teamId: UUID,
        idempotencyKey: String,
        title: String,
        targetUserIds: Collection<UUID>,
        emailEnabled: Boolean,
        smsEnabled: Boolean,
        initialMessageIdempotencyKey: String,
        initialMessage: String,
        currentUser: CurrentUser,
    ): MessageThread {
        requireTeamManage(organizationId, teamId, currentUser)
        val normalizedTargets = ConversationMemberPolicy.requireTargetIds(targetUserIds, currentUser.userId)
        val threadInput = BroadcastMessagePolicy.normalizeThread(title, idempotencyKey)
        val messageInput = BroadcastMessagePolicy.normalizeMessage(initialMessage, initialMessageIdempotencyKey)
        val rawContacts = repository.listConversationContacts(organizationId, teamId)
        val contacts = safeSportService?.filterAdultOriginContacts(organizationId, teamId, rawContacts) ?: rawContacts
        val byUserId = contacts.associateBy { it.userId }
        if (!byUserId.keys.containsAll(normalizedTargets)) {
            throw ValidationException("One or more selected recipients are not active guardians or athletes on this team.")
        }

        repository.findThreadByIdempotencyKey(organizationId, threadInput.idempotencyKey)?.let { existing ->
            verifyIdempotentConversation(existing, teamId, threadInput.title, normalizedTargets, emailEnabled, smsEnabled)
            sendConversationMessage(existing, messageInput.idempotencyKey, messageInput.body, currentUser)
            return repository.findThreadById(existing.id, organizationId) ?: existing
        }

        val thread =
            try {
                repository.insertConversationThread(
                    organizationId = organizationId,
                    teamId = teamId,
                    idempotencyKey = threadInput.idempotencyKey,
                    title = threadInput.title,
                    emailEnabled = emailEnabled,
                    smsEnabled = smsEnabled,
                    createdByUserId = currentUser.userId,
                )
            } catch (_: DuplicateKeyException) {
                val existing =
                    repository.findThreadByIdempotencyKey(organizationId, threadInput.idempotencyKey)
                        ?: throw ConflictException("MESSAGE_THREAD_IDEMPOTENCY_CONFLICT", "The conversation could not be created safely.")
                verifyIdempotentConversation(existing, teamId, threadInput.title, normalizedTargets, emailEnabled, smsEnabled)
                existing
            }

        if (repository.listActiveThreadMembers(thread.id).isEmpty()) {
            val creator =
                MessageThreadMemberCandidate(
                    userId = currentUser.userId,
                    memberType = MessageRecipientType.STAFF,
                    householdId = null,
                    participantId = null,
                    displayName = currentUser.displayName,
                    accessReason = MessageAccessReason.TARGETED,
                    canReply = true,
                )
            val selected =
                normalizedTargets.map { userId ->
                    val contact = byUserId.getValue(userId)
                    MessageThreadMemberCandidate(
                        userId = contact.userId,
                        memberType = contact.recipientType,
                        householdId = contact.householdId,
                        participantId = contact.participantId,
                        displayName = contact.displayName,
                        accessReason = MessageAccessReason.TARGETED,
                        canReply = true,
                    )
                }
            val members = ConversationMemberPolicy.merge(creator, selected, repository.listGuardianObserverLinks(organizationId, teamId))
            val now = Instant.now(clock)
            members.forEach { repository.insertThreadMember(organizationId, thread.id, it, now) }
        }

        verifyIdempotentConversation(thread, teamId, threadInput.title, normalizedTargets, emailEnabled, smsEnabled)
        sendConversationMessage(thread, messageInput.idempotencyKey, messageInput.body, currentUser)
        audit(
            currentUser,
            organizationId,
            "message.conversation.created",
            "message_thread",
            thread.id,
            mapOf("teamId" to teamId.toString(), "selectedRecipientCount" to normalizedTargets.size),
        )
        return repository.findThreadById(thread.id, organizationId) ?: thread
    }

    fun listAthleteTeams(currentUser: CurrentUser): List<AthleteMessagingTeam> =
        repository.listAthleteMessagingTeams(currentUser.userId).map { (organizationId, teamId, teamName) ->
            val enabled =
                safeSportService?.let { service ->
                    try {
                        service.requireAthleteMessagingEnabled(organizationId)
                        true
                    } catch (_: RuntimeException) {
                        false
                    }
                }
                    ?: false
            AthleteMessagingTeam(organizationId, teamId, teamName, enabled)
        }

    fun listAthleteContacts(
        organizationId: UUID,
        teamId: UUID,
        currentUser: CurrentUser,
    ): List<ConversationContact> {
        requireSafeSportService().requireAthleteMessagingEnabled(organizationId)
        if (!repository.isActiveAthleteOnTeam(organizationId, teamId, currentUser.userId)) {
            throw NotFoundException("TEAM_NOT_FOUND", "The athlete team messaging context could not be found.")
        }
        val restricted = requireSafeSportService().restrictedAthleteUserIdsForAllMessaging(organizationId, teamId)
        if (currentUser.userId in
            restricted
        ) {
            throw ForbiddenException(
                "ATHLETE_MESSAGING_RESTRICTED",
                "A guardian messaging restriction currently prevents athlete peer messaging for this account.",
            )
        }
        return repository.listAthletePeerContacts(organizationId, teamId, currentUser.userId).filterNot { it.userId in restricted }
    }

    @Transactional
    fun createAthleteConversation(
        organizationId: UUID,
        teamId: UUID,
        idempotencyKey: String,
        title: String,
        targetUserIds: Collection<UUID>,
        initialMessageIdempotencyKey: String,
        initialMessage: String,
        currentUser: CurrentUser,
    ): MessageThread {
        requireSafeSportService().requireAthleteMessagingEnabled(organizationId)
        if (!repository.isActiveAthleteOnTeam(
                organizationId,
                teamId,
                currentUser.userId,
            )
        ) {
            throw NotFoundException("TEAM_NOT_FOUND", "The athlete team messaging context could not be found.")
        }
        val restricted = requireSafeSportService().restrictedAthleteUserIdsForAllMessaging(organizationId, teamId)
        if (currentUser.userId in
            restricted
        ) {
            throw ForbiddenException(
                "ATHLETE_MESSAGING_RESTRICTED",
                "A guardian messaging restriction currently prevents athlete peer messaging for this account.",
            )
        }
        val targets = AthleteConversationPolicy.requireTargetIds(targetUserIds, currentUser.userId)
        val threadInput = BroadcastMessagePolicy.normalizeThread(title, idempotencyKey)
        val messageInput = BroadcastMessagePolicy.normalizeMessage(initialMessage, initialMessageIdempotencyKey)
        val peers = repository.listAthletePeerContacts(organizationId, teamId, currentUser.userId).filterNot { it.userId in restricted }
        val byUserId = peers.associateBy { it.userId }
        if (!byUserId.keys.containsAll(
                targets,
            )
        ) {
            throw ValidationException("One or more selected athletes are not currently eligible teammates.")
        }
        repository.findThreadByIdempotencyKey(organizationId, threadInput.idempotencyKey)?.let { existing ->
            verifyIdempotentAthleteConversation(existing, teamId, threadInput.title, targets)
            sendConversationMessage(existing, messageInput.idempotencyKey, messageInput.body, currentUser)
            return repository.findThreadById(existing.id, organizationId) ?: existing
        }
        val thread =
            try {
                repository.insertAthleteConversationThread(
                    organizationId,
                    teamId,
                    threadInput.idempotencyKey,
                    threadInput.title,
                    currentUser.userId,
                )
            } catch (
                _: DuplicateKeyException,
            ) {
                repository.findThreadByIdempotencyKey(organizationId, threadInput.idempotencyKey)
                    ?: throw ConflictException(
                        "MESSAGE_THREAD_IDEMPOTENCY_CONFLICT",
                        "The athlete conversation could not be created safely.",
                    )
            }
        if (repository.listActiveThreadMembers(thread.id).isEmpty()) {
            val creatorContact =
                repository.listConversationContacts(organizationId, teamId).firstOrNull {
                    it.userId == currentUser.userId &&
                        it.recipientType == MessageRecipientType.ATHLETE
                }
                    ?: throw ForbiddenException(
                        "ATHLETE_MESSAGING_TEAM_ACCESS_REQUIRED",
                        "Your athlete profile is not eligible for this team conversation.",
                    )
            val creator =
                MessageThreadMemberCandidate(
                    currentUser.userId,
                    MessageRecipientType.ATHLETE,
                    creatorContact.householdId,
                    creatorContact.participantId,
                    creatorContact.displayName,
                )
            val selected =
                targets.map { id ->
                    val c = byUserId.getValue(id)
                    MessageThreadMemberCandidate(c.userId, MessageRecipientType.ATHLETE, c.householdId, c.participantId, c.displayName)
                }
            val members = AthleteConversationPolicy.merge(creator, selected, repository.listGuardianObserverLinks(organizationId, teamId))
            val now = Instant.now(clock)
            members.forEach { repository.insertThreadMember(organizationId, thread.id, it, now) }
        }
        verifyIdempotentAthleteConversation(thread, teamId, threadInput.title, targets)
        sendConversationMessage(thread, messageInput.idempotencyKey, messageInput.body, currentUser)
        audit(
            currentUser,
            organizationId,
            "message.athlete_conversation.created",
            "message_thread",
            thread.id,
            mapOf(
                "teamId" to teamId.toString(),
                "selectedAthleteCount" to targets.size,
            ),
        )
        return repository.findThreadById(thread.id, organizationId) ?: thread
    }

    fun listMembers(
        threadId: UUID,
        currentUser: CurrentUser,
    ): List<MessageThreadMember> {
        val thread =
            repository.findThreadByIdForUser(threadId, currentUser.userId)
                ?: throw NotFoundException("MESSAGE_THREAD_NOT_FOUND", "The message thread could not be found.")
        if (thread.threadType !in setOf(MessageThreadType.CONVERSATION, MessageThreadType.ATHLETE_CONVERSATION)) {
            throw NotFoundException("MESSAGE_THREAD_NOT_FOUND", "The message thread could not be found.")
        }
        repository.findActiveThreadMember(threadId, currentUser.userId)
            ?: throw NotFoundException("MESSAGE_THREAD_NOT_FOUND", "The message thread could not be found.")
        return repository.listActiveThreadMembers(threadId)
    }

    @Transactional
    fun reply(
        threadId: UUID,
        idempotencyKey: String,
        body: String,
        currentUser: CurrentUser,
    ): BroadcastMessage {
        val thread =
            repository.findThreadByIdForUser(threadId, currentUser.userId)
                ?: throw NotFoundException("MESSAGE_THREAD_NOT_FOUND", "The message thread could not be found.")
        if (thread.threadType !in setOf(MessageThreadType.CONVERSATION, MessageThreadType.ATHLETE_CONVERSATION)) {
            throw ConflictException("MESSAGE_THREAD_NOT_CONVERSATION", "Broadcast threads do not accept recipient replies.")
        }
        if (thread.status != MessageThreadStatus.OPEN) {
            throw ConflictException("MESSAGE_THREAD_ARCHIVED", "Archived message threads cannot receive new replies.")
        }
        if (thread.safetyLockedAt != null) {
            throw ConflictException("MESSAGE_THREAD_SAFETY_LOCKED", "This conversation is temporarily locked for safety review.")
        }
        val member =
            repository.findActiveThreadMember(threadId, currentUser.userId)
                ?: throw NotFoundException("MESSAGE_THREAD_NOT_FOUND", "The message thread could not be found.")
        if (!member.canReply) {
            throw ForbiddenException("MESSAGE_REPLY_NOT_ALLOWED", "This guardian visibility membership is read-only.")
        }
        val input = BroadcastMessagePolicy.normalizeMessage(body, idempotencyKey)
        return sendConversationMessage(thread, input.idempotencyKey, input.body, currentUser)
    }

    private fun sendConversationMessage(
        thread: MessageThread,
        idempotencyKey: String,
        body: String,
        currentUser: CurrentUser,
    ): BroadcastMessage {
        membershipReconciliationService?.reconcileThread(thread)
        if (thread.threadType !in setOf(MessageThreadType.CONVERSATION, MessageThreadType.ATHLETE_CONVERSATION)) {
            throw ConflictException("MESSAGE_THREAD_NOT_CONVERSATION", "This thread is not a two-way conversation.")
        }
        if (thread.status != MessageThreadStatus.OPEN) {
            throw ConflictException("MESSAGE_THREAD_ARCHIVED", "Archived message threads cannot receive new messages.")
        }
        if (thread.safetyLockedAt != null) {
            throw ConflictException("MESSAGE_THREAD_SAFETY_LOCKED", "This conversation is temporarily locked for safety review.")
        }
        repository.findMessageByIdempotencyKey(thread.id, idempotencyKey)?.let { existing ->
            if (existing.body != body || existing.senderUserId != currentUser.userId) {
                throw ConflictException("MESSAGE_IDEMPOTENCY_CONFLICT", "This idempotency key was already used for a different message.")
            }
            return existing
        }
        val member =
            repository.findActiveThreadMember(thread.id, currentUser.userId)
                ?: throw ForbiddenException(
                    "MESSAGE_REPLY_NOT_ALLOWED",
                    "Only active conversation members may send messages in this thread.",
                )
        if (!member.canReply) {
            throw ForbiddenException("MESSAGE_REPLY_NOT_ALLOWED", "This guardian visibility membership is read-only.")
        }
        when (member.memberType) {
            MessageRecipientType.STAFF -> {
                requireTeamManage(thread.organizationId, thread.scopeId, currentUser)
            }

            MessageRecipientType.ATHLETE -> {
                if (!repository.isActiveAthleteOnTeam(thread.organizationId, thread.scopeId, currentUser.userId)) {
                    throw ForbiddenException("MESSAGE_REPLY_NOT_ALLOWED", "You are no longer an active athlete on this team.")
                }
                if (currentUser.userId in
                    (safeSportService?.restrictedAthleteUserIdsForAllMessaging(thread.organizationId, thread.scopeId) ?: emptySet())
                ) {
                    throw ForbiddenException(
                        "ATHLETE_MESSAGING_RESTRICTED",
                        "A guardian messaging restriction currently prevents messaging for this athlete account.",
                    )
                }
            }

            MessageRecipientType.GUARDIAN -> {
                if (member.accessReason == MessageAccessReason.TARGETED &&
                    currentUser.userId !in repository.activeGuardianUserIdsForTeam(thread.organizationId, thread.scopeId)
                ) {
                    throw ForbiddenException(
                        "MESSAGE_REPLY_NOT_ALLOWED",
                        "You are no longer an active guardian contact for this team conversation.",
                    )
                }
            }
        }
        if (thread.threadType == MessageThreadType.ATHLETE_CONVERSATION) {
            requireSafeSportService().requireAthleteMessagingEnabled(thread.organizationId)
            if (member.memberType != MessageRecipientType.ATHLETE ||
                !repository.isActiveAthleteOnTeam(thread.organizationId, thread.scopeId, currentUser.userId)
            ) {
                throw ForbiddenException(
                    "ATHLETE_MESSAGING_TEAM_ACCESS_REQUIRED",
                    "You are no longer eligible to send messages in this athlete team conversation.",
                )
            }
            if (currentUser.userId in
                requireSafeSportService().restrictedAthleteUserIdsForAllMessaging(thread.organizationId, thread.scopeId)
            ) {
                throw ForbiddenException(
                    "ATHLETE_MESSAGING_RESTRICTED",
                    "A guardian messaging restriction currently prevents athlete peer messaging for this account.",
                )
            }
        }
        if (member.memberType == MessageRecipientType.STAFF) {
            safeSportService?.requireAdultOriginAllowedForMembers(
                thread.organizationId,
                thread.scopeId,
                repository.listActiveThreadMembers(thread.id),
            )
        }

        val candidates = BroadcastRecipientPolicy.merge(repository.listConversationRecipients(thread.id, currentUser.userId))
        if (candidates.isEmpty()) {
            throw ValidationException("The conversation has no other active members to receive this message.")
        }
        val now = Instant.now(clock)
        val message =
            try {
                repository.insertMessage(thread.organizationId, thread.id, currentUser.userId, idempotencyKey, body, now)
            } catch (_: DuplicateKeyException) {
                repository.findMessageByIdempotencyKey(thread.id, idempotencyKey)
                    ?: throw ConflictException("MESSAGE_IDEMPOTENCY_CONFLICT", "The message could not be sent safely.")
            }
        var inserted = 0
        for ((key, candidate) in candidates) {
            val externalDelivery = candidate.accessReason == MessageAccessReason.TARGETED
            val emailStatus =
                if (externalDelivery &&
                    thread.emailEnabled &&
                    !candidate.email.isNullOrBlank()
                ) {
                    DeliveryStatus.PENDING
                } else {
                    DeliveryStatus.NONE
                }
            val smsStatus =
                if (externalDelivery &&
                    thread.smsEnabled &&
                    !candidate.phone.isNullOrBlank()
                ) {
                    DeliveryStatus.PENDING
                } else {
                    DeliveryStatus.NONE
                }
            inserted +=
                repository.insertRecipient(
                    message.id,
                    thread.organizationId,
                    key,
                    candidate,
                    inAppVisible = candidate.userId != null,
                    emailStatus = emailStatus,
                    smsStatus = smsStatus,
                )
        }
        if (inserted > 0) {
            outboxWriter.write(
                aggregateType = "message_entry",
                aggregateId = message.id,
                organizationId = thread.organizationId,
                eventType = "message.conversation_sent",
                payloadJson =
                    objectMapper.writeValueAsString(
                        mapOf("messageId" to message.id.toString(), "threadId" to thread.id.toString()),
                    ),
            )
            audit(
                currentUser,
                thread.organizationId,
                "message.conversation_sent",
                "message_entry",
                message.id,
                mapOf("threadId" to thread.id.toString(), "recipientCount" to inserted),
            )
        }
        return repository.findMessageById(message.id, thread.organizationId) ?: message
    }

    private fun verifyIdempotentConversation(
        thread: MessageThread,
        teamId: UUID,
        title: String,
        targetUserIds: Set<UUID>,
        emailEnabled: Boolean,
        smsEnabled: Boolean,
    ) {
        val selectedMemberIds =
            repository
                .listActiveThreadMembers(thread.id)
                .filter { it.memberType != MessageRecipientType.STAFF && it.accessReason == MessageAccessReason.TARGETED }
                .map { it.userId }
                .toSet()
        if (thread.threadType != MessageThreadType.CONVERSATION ||
            thread.scopeId != teamId ||
            thread.title != title ||
            thread.emailEnabled != emailEnabled ||
            thread.smsEnabled != smsEnabled ||
            (selectedMemberIds.isNotEmpty() && selectedMemberIds != targetUserIds)
        ) {
            throw ConflictException(
                "MESSAGE_THREAD_IDEMPOTENCY_CONFLICT",
                "This idempotency key was already used for a different message thread.",
            )
        }
    }

    private fun requireSafeSportService(): MessageSafeSportService =
        safeSportService ?: throw ConflictException(
            "ATHLETE_MESSAGING_REVIEW_REQUIRED",
            "Athlete-created messaging is unavailable until the SafeSport policy service is configured.",
        )

    private fun verifyIdempotentAthleteConversation(
        thread: MessageThread,
        teamId: UUID,
        title: String,
        targetUserIds: Set<UUID>,
    ) {
        val selected =
            repository
                .listActiveThreadMembers(thread.id)
                .filter {
                    it.memberType == MessageRecipientType.ATHLETE &&
                        it.userId != thread.createdByUserId
                }.map { it.userId }
                .toSet()
        if (thread.threadType != MessageThreadType.ATHLETE_CONVERSATION ||
            thread.scopeId != teamId ||
            thread.title != title ||
            (selected.isNotEmpty() && selected != targetUserIds)
        ) {
            throw ConflictException(
                "MESSAGE_THREAD_IDEMPOTENCY_CONFLICT",
                "This idempotency key was already used for a different athlete conversation.",
            )
        }
    }

    private fun requireTeamManage(
        organizationId: UUID,
        teamId: UUID,
        currentUser: CurrentUser,
    ) {
        teamRepository.findById(teamId, organizationId) ?: throw NotFoundException("TEAM_NOT_FOUND", "The team could not be found.")
        authorizationService.requireTeamCapability(organizationId, teamId, currentUser, Capabilities.TEAM_COMMUNICATION_MANAGE)
    }

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
