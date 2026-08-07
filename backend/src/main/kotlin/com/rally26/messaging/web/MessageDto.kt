package com.rally26.messaging.web

import com.rally26.messaging.domain.BroadcastMessage
import com.rally26.messaging.domain.MessageAudience
import com.rally26.messaging.domain.MessageScopeType
import com.rally26.messaging.domain.MessageThread
import com.rally26.messaging.domain.MyBroadcastMessage
import com.rally26.messaging.domain.MyMessageThread
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class CreateMessageThreadRequest(
    @field:NotNull val scopeType: MessageScopeType,
    val scopeId: UUID? = null,
    @field:NotBlank @field:Size(min = 8, max = 160) val idempotencyKey: String,
    @field:NotBlank @field:Size(max = 180) val title: String,
    @field:NotNull val audience: MessageAudience,
    val emailEnabled: Boolean = true,
    val smsEnabled: Boolean = false,
)

data class SendBroadcastMessageRequest(
    @field:NotBlank @field:Size(min = 8, max = 160) val idempotencyKey: String,
    @field:NotBlank @field:Size(max = 5000) val body: String,
)

data class MessageThreadResponse(
    val id: UUID,
    val organizationId: UUID,
    val scopeType: String,
    val scopeId: UUID,
    val scopeName: String?,
    val title: String,
    val audience: String,
    val emailEnabled: Boolean,
    val smsEnabled: Boolean,
    val status: String,
    val messageCount: Long,
    val recipientCount: Long,
    val archivedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class BroadcastMessageResponse(
    val id: UUID,
    val organizationId: UUID,
    val threadId: UUID,
    val senderUserId: UUID,
    val senderDisplayName: String,
    val body: String,
    val sentAt: Instant,
    val recipientCount: Long,
    val emailSentCount: Long,
    val emailFailedCount: Long,
    val smsSentCount: Long,
    val smsFailedCount: Long,
)

data class MyMessageThreadResponse(
    val thread: MessageThreadResponse,
    val unreadCount: Long,
    val lastMessageAt: Instant,
    val lastMessagePreview: String,
)

data class MyBroadcastMessageResponse(
    val message: BroadcastMessageResponse,
    val readAt: Instant?,
    val accessReason: String,
)

fun MessageThread.toResponse() =
    MessageThreadResponse(
        id = id,
        organizationId = organizationId,
        scopeType = scopeType.name,
        scopeId = scopeId,
        scopeName = scopeName,
        title = title,
        audience = audience.name,
        emailEnabled = emailEnabled,
        smsEnabled = smsEnabled,
        status = status.name,
        messageCount = messageCount,
        recipientCount = recipientCount,
        archivedAt = archivedAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

fun BroadcastMessage.toResponse() =
    BroadcastMessageResponse(
        id = id,
        organizationId = organizationId,
        threadId = threadId,
        senderUserId = senderUserId,
        senderDisplayName = senderDisplayName,
        body = body,
        sentAt = sentAt,
        recipientCount = recipientCount,
        emailSentCount = emailSentCount,
        emailFailedCount = emailFailedCount,
        smsSentCount = smsSentCount,
        smsFailedCount = smsFailedCount,
    )

fun MyMessageThread.toResponse() = MyMessageThreadResponse(thread.toResponse(), unreadCount, lastMessageAt, lastMessagePreview)

fun MyBroadcastMessage.toResponse() = MyBroadcastMessageResponse(message.toResponse(), readAt, accessReason.name)
