package com.rally26.messaging.web

import com.rally26.messaging.domain.MessageModerationEvent
import com.rally26.messaging.domain.MessageSafetyReport
import com.rally26.messaging.domain.MessageSafetyReportReason
import com.rally26.messaging.domain.MessageSafetyReportStatus
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class CreateMessageSafetyReportRequest(
    val reason: MessageSafetyReportReason,
    @field:Size(max = 2000) val details: String? = null,
)

data class ReviewMessageSafetyReportRequest(
    val status: MessageSafetyReportStatus,
    @field:Size(max = 2000) val note: String? = null,
)

data class SafetyLockThreadRequest(
    @field:NotBlank @field:Size(min = 5, max = 1000) val reason: String,
    val reportId: UUID? = null,
)

data class SafetyUnlockThreadRequest(
    @field:NotBlank @field:Size(min = 3, max = 2000) val note: String,
    val reportId: UUID? = null,
)

data class MessageSafetyReportResponse(
    val id: UUID,
    val organizationId: UUID,
    val threadId: UUID,
    val threadTitle: String,
    val messageId: UUID,
    val messageBody: String,
    val messageSenderDisplayName: String,
    val reporterUserId: UUID,
    val reporterDisplayName: String,
    val reason: String,
    val details: String?,
    val status: String,
    val assignedToUserId: UUID?,
    val resolutionNote: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val resolvedAt: Instant?,
    val threadSafetyLockedAt: Instant?,
    val threadSafetyLockReason: String?,
)

data class MessageModerationEventResponse(
    val id: UUID,
    val organizationId: UUID,
    val reportId: UUID?,
    val threadId: UUID,
    val messageId: UUID?,
    val actorUserId: UUID,
    val actorDisplayName: String,
    val eventType: String,
    val note: String?,
    val createdAt: Instant,
)

fun MessageSafetyReport.toResponse() =
    MessageSafetyReportResponse(
        id,
        organizationId,
        threadId,
        threadTitle,
        messageId,
        messageBody,
        messageSenderDisplayName,
        reporterUserId,
        reporterDisplayName,
        reason.name,
        details,
        status.name,
        assignedToUserId,
        resolutionNote,
        createdAt,
        updatedAt,
        resolvedAt,
        threadSafetyLockedAt,
        threadSafetyLockReason,
    )

fun MessageModerationEvent.toResponse() =
    MessageModerationEventResponse(
        id,
        organizationId,
        reportId,
        threadId,
        messageId,
        actorUserId,
        actorDisplayName,
        eventType.name,
        note,
        createdAt,
    )
