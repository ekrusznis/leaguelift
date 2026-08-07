package com.rally26.messaging.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.rally26.audit.application.AuditService
import com.rally26.authorization.application.AuthorizationService
import com.rally26.authorization.domain.Capabilities
import com.rally26.common.error.NotFoundException
import com.rally26.common.error.ValidationException
import com.rally26.common.web.CurrentUser
import com.rally26.common.web.PageRequest
import com.rally26.common.web.PageResponse
import com.rally26.membership.application.MembershipService
import com.rally26.messaging.domain.MessageModerationEvent
import com.rally26.messaging.domain.MessageModerationEventType
import com.rally26.messaging.domain.MessageSafetyReport
import com.rally26.messaging.domain.MessageSafetyReportReason
import com.rally26.messaging.domain.MessageSafetyReportStatus
import com.rally26.messaging.domain.MessageScopeType
import com.rally26.messaging.domain.MessageThread
import com.rally26.messaging.persistence.MessageRepository
import com.rally26.messaging.persistence.MessageSafetyRepository
import com.rally26.team.persistence.TeamRepository
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.util.UUID

@Service
class MessageSafetyService(
    private val safetyRepository: MessageSafetyRepository,
    private val messageRepository: MessageRepository,
    private val membershipService: MembershipService,
    private val authorizationService: AuthorizationService,
    private val teamRepository: TeamRepository,
    private val auditService: AuditService,
    private val objectMapper: ObjectMapper,
    private val clock: Clock,
) {
    @Transactional
    fun reportMessage(
        messageId: UUID,
        reason: MessageSafetyReportReason,
        details: String?,
        currentUser: CurrentUser,
    ): MessageSafetyReport {
        val target =
            safetyRepository.findReportableMessageForUser(messageId, currentUser.userId)
                ?: throw NotFoundException("MESSAGE_NOT_FOUND", "The message could not be found.")
        val normalizedDetails = MessageSafetyPolicy.normalizeDetails(details)
        safetyRepository.findActiveReport(messageId, currentUser.userId)?.let { return it }
        val now = Instant.now(clock)
        val report =
            try {
                safetyRepository.insertReport(target, currentUser.userId, reason, normalizedDetails, now)
            } catch (_: DuplicateKeyException) {
                safetyRepository.findActiveReport(messageId, currentUser.userId)
                    ?: throw ValidationException("This message could not be reported safely. Please try again.")
            }
        safetyRepository.insertModerationEvent(
            organizationId = target.organizationId,
            reportId = report.id,
            threadId = target.threadId,
            messageId = messageId,
            actorUserId = currentUser.userId,
            eventType = MessageModerationEventType.REPORT_CREATED,
            note = normalizedDetails,
            now = now,
        )
        audit(
            currentUser,
            target.organizationId,
            "message.safety_report.created",
            "message_safety_report",
            report.id,
            mapOf("threadId" to target.threadId.toString(), "messageId" to messageId.toString(), "reason" to reason.name),
        )
        return safetyRepository.findReportById(report.id, target.organizationId) ?: report
    }

    fun listMine(
        page: PageRequest,
        currentUser: CurrentUser,
    ): PageResponse<MessageSafetyReport> =
        PageResponse(
            safetyRepository.listMine(currentUser.userId, page),
            page.page,
            page.size,
            safetyRepository.countMine(currentUser.userId),
        )

    fun listForManagement(
        organizationId: UUID,
        scopeType: MessageScopeType?,
        scopeId: UUID?,
        status: MessageSafetyReportStatus?,
        page: PageRequest,
        currentUser: CurrentUser,
    ): PageResponse<MessageSafetyReport> {
        requireListAccess(organizationId, scopeType, scopeId, currentUser)
        return PageResponse(
            safetyRepository.listForManagement(organizationId, scopeType, scopeId, status, page),
            page.page,
            page.size,
            safetyRepository.countForManagement(organizationId, scopeType, scopeId, status),
        )
    }

    fun listModerationEvents(
        organizationId: UUID,
        reportId: UUID,
        currentUser: CurrentUser,
    ): List<MessageModerationEvent> {
        val report = requireManagedReport(organizationId, reportId, currentUser)
        return safetyRepository.listModerationEvents(report.organizationId, report.id)
    }

    @Transactional
    fun reviewReport(
        organizationId: UUID,
        reportId: UUID,
        status: MessageSafetyReportStatus,
        note: String?,
        currentUser: CurrentUser,
    ): MessageSafetyReport {
        val report = requireManagedReport(organizationId, reportId, currentUser)
        if (report.status == status) return report
        val normalizedNote = MessageSafetyPolicy.normalizeReview(report.status, status, note)
        val eventType =
            when (status) {
                MessageSafetyReportStatus.OPEN -> error("OPEN is rejected by MessageSafetyPolicy")
                MessageSafetyReportStatus.IN_REVIEW -> MessageModerationEventType.REVIEW_STARTED
                MessageSafetyReportStatus.RESOLVED -> MessageModerationEventType.RESOLVED
                MessageSafetyReportStatus.DISMISSED -> MessageModerationEventType.DISMISSED
            }
        val now = Instant.now(clock)
        safetyRepository.updateReportStatus(reportId, organizationId, status, currentUser.userId, normalizedNote, now)
        safetyRepository.insertModerationEvent(
            organizationId,
            report.id,
            report.threadId,
            report.messageId,
            currentUser.userId,
            eventType,
            normalizedNote,
            now,
        )
        audit(
            currentUser,
            organizationId,
            "message.safety_report.${status.name.lowercase()}",
            "message_safety_report",
            report.id,
            mapOf("threadId" to report.threadId.toString(), "messageId" to report.messageId.toString()),
        )
        return safetyRepository.findReportById(reportId, organizationId)
            ?: throw NotFoundException("MESSAGE_REPORT_NOT_FOUND", "The message safety report could not be found.")
    }

    @Transactional
    fun lockThread(
        organizationId: UUID,
        threadId: UUID,
        reason: String,
        reportId: UUID?,
        currentUser: CurrentUser,
    ): MessageThread {
        val thread = requireManagedThread(organizationId, threadId, currentUser)
        val linkedReport = reportId?.let { requireManagedReport(organizationId, it, currentUser) }
        if (linkedReport != null && linkedReport.threadId != threadId) {
            throw ValidationException("The selected safety report does not belong to this message thread.")
        }
        if (thread.safetyLockedAt != null) return thread
        val normalizedReason = MessageSafetyPolicy.normalizeLockReason(reason)
        val now = Instant.now(clock)
        safetyRepository.lockThread(organizationId, threadId, currentUser.userId, normalizedReason, now)
        safetyRepository.insertModerationEvent(
            organizationId,
            linkedReport?.id,
            threadId,
            linkedReport?.messageId,
            currentUser.userId,
            MessageModerationEventType.THREAD_LOCKED,
            normalizedReason,
            now,
        )
        audit(
            currentUser,
            organizationId,
            "message.thread.safety_locked",
            "message_thread",
            threadId,
            mapOf("reason" to normalizedReason),
        )
        return messageRepository.findThreadById(threadId, organizationId)
            ?: throw NotFoundException("MESSAGE_THREAD_NOT_FOUND", "The message thread could not be found.")
    }

    @Transactional
    fun unlockThread(
        organizationId: UUID,
        threadId: UUID,
        note: String,
        reportId: UUID?,
        currentUser: CurrentUser,
    ): MessageThread {
        val thread = requireManagedThread(organizationId, threadId, currentUser)
        val linkedReport = reportId?.let { requireManagedReport(organizationId, it, currentUser) }
        if (linkedReport != null && linkedReport.threadId != threadId) {
            throw ValidationException("The selected safety report does not belong to this message thread.")
        }
        if (thread.safetyLockedAt == null) return thread
        val normalizedNote = MessageSafetyPolicy.normalizeUnlockNote(note)
        val now = Instant.now(clock)
        safetyRepository.unlockThread(organizationId, threadId, now)
        safetyRepository.insertModerationEvent(
            organizationId,
            linkedReport?.id,
            threadId,
            linkedReport?.messageId,
            currentUser.userId,
            MessageModerationEventType.THREAD_UNLOCKED,
            normalizedNote,
            now,
        )
        audit(
            currentUser,
            organizationId,
            "message.thread.safety_unlocked",
            "message_thread",
            threadId,
            mapOf("note" to normalizedNote),
        )
        return messageRepository.findThreadById(threadId, organizationId)
            ?: throw NotFoundException("MESSAGE_THREAD_NOT_FOUND", "The message thread could not be found.")
    }

    private fun requireManagedReport(
        organizationId: UUID,
        reportId: UUID,
        currentUser: CurrentUser,
    ): MessageSafetyReport {
        val report =
            safetyRepository.findReportById(reportId, organizationId)
                ?: throw NotFoundException("MESSAGE_REPORT_NOT_FOUND", "The message safety report could not be found.")
        val thread =
            messageRepository.findThreadById(report.threadId, organizationId)
                ?: throw NotFoundException("MESSAGE_THREAD_NOT_FOUND", "The message thread could not be found.")
        requireManage(organizationId, thread.scopeType, thread.scopeId, currentUser)
        return report
    }

    private fun requireManagedThread(
        organizationId: UUID,
        threadId: UUID,
        currentUser: CurrentUser,
    ): MessageThread {
        val thread =
            messageRepository.findThreadById(threadId, organizationId)
                ?: throw NotFoundException("MESSAGE_THREAD_NOT_FOUND", "The message thread could not be found.")
        requireManage(organizationId, thread.scopeType, thread.scopeId, currentUser)
        return thread
    }

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
            MessageScopeType.TEAM -> {
                teamRepository.findById(scopeId, organizationId)
                    ?: throw NotFoundException("TEAM_NOT_FOUND", "The team could not be found.")
                authorizationService.requireTeamCapability(organizationId, scopeId, currentUser, Capabilities.TEAM_COMMUNICATION_MANAGE)
            }
        }
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
