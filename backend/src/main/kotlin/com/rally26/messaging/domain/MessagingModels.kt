package com.rally26.messaging.domain

import com.rally26.communication.domain.DeliveryStatus
import java.time.Instant
import java.util.UUID

enum class MessageScopeType { ORGANIZATION, TEAM }

enum class MessageAudience { ALL, STAFF, GUARDIANS, ATHLETES }

enum class MessageThreadStatus { OPEN, ARCHIVED }

enum class MessageRecipientType { STAFF, GUARDIAN, ATHLETE }

enum class MessageAccessReason { TARGETED, GUARDIAN_VISIBILITY }

data class MessageThread(
    val id: UUID,
    val organizationId: UUID,
    val scopeType: MessageScopeType,
    val scopeId: UUID,
    val scopeName: String?,
    val title: String,
    val audience: MessageAudience,
    val emailEnabled: Boolean,
    val smsEnabled: Boolean,
    val status: MessageThreadStatus,
    val createdByUserId: UUID,
    val archivedAt: Instant?,
    val messageCount: Long,
    val recipientCount: Long,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class BroadcastMessage(
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

data class MessageRecipientCandidate(
    val recipientType: MessageRecipientType,
    val userId: UUID?,
    val householdId: UUID?,
    val displayName: String,
    val email: String?,
    val phone: String?,
    val accessReason: MessageAccessReason = MessageAccessReason.TARGETED,
)

data class MessageRecipient(
    val id: UUID,
    val messageId: UUID,
    val recipientKey: String,
    val recipientType: MessageRecipientType,
    val userId: UUID?,
    val householdId: UUID?,
    val displayName: String,
    val email: String?,
    val phone: String?,
    val accessReason: MessageAccessReason,
    val inAppVisible: Boolean,
    val emailStatus: DeliveryStatus,
    val smsStatus: DeliveryStatus,
    val readAt: Instant?,
    val lastError: String?,
)

data class MyMessageThread(
    val thread: MessageThread,
    val unreadCount: Long,
    val lastMessageAt: Instant,
    val lastMessagePreview: String,
)

data class MyBroadcastMessage(
    val message: BroadcastMessage,
    val readAt: Instant?,
    val accessReason: MessageAccessReason,
)
