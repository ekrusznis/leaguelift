package com.rally26.messaging.domain

import com.rally26.communication.domain.DeliveryStatus
import java.time.Instant
import java.util.UUID

enum class MessageScopeType { ORGANIZATION, TEAM }

enum class MessageThreadType { BROADCAST, CONVERSATION, ATHLETE_CONVERSATION }

enum class MessageAudience { ALL, STAFF, GUARDIANS, ATHLETES, SELECTED }

enum class MessageThreadStatus { OPEN, ARCHIVED }

enum class MessageRecipientType { STAFF, GUARDIAN, ATHLETE }

enum class MessageAccessReason { TARGETED, GUARDIAN_VISIBILITY }

enum class MessageSafetyReportReason { HARASSMENT, BULLYING, INAPPROPRIATE_CONTENT, SAFETY_CONCERN, SPAM, OTHER }

enum class MessageSafetyReportStatus { OPEN, IN_REVIEW, RESOLVED, DISMISSED }

enum class MessageModerationEventType { REPORT_CREATED, REVIEW_STARTED, RESOLVED, DISMISSED, THREAD_LOCKED, THREAD_UNLOCKED }

enum class MessageSafeSportReviewStatus { PENDING, APPROVED, REJECTED }

enum class MessageRetentionMode { PRESERVE_NO_AUTOMATIC_DELETION }

enum class MessageContactRestrictionKind { ADULT_TO_MINOR, ALL_MESSAGING }

enum class MessageContactRestrictionStatus { ACTIVE, LIFTED }

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
    val threadType: MessageThreadType = MessageThreadType.BROADCAST,
    val safetyLockedAt: Instant? = null,
    val safetyLockReason: String? = null,
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

data class ConversationContact(
    val userId: UUID,
    val recipientType: MessageRecipientType,
    val householdId: UUID?,
    val participantId: UUID?,
    val displayName: String,
)

data class MessageThreadMemberCandidate(
    val userId: UUID,
    val memberType: MessageRecipientType,
    val householdId: UUID?,
    val participantId: UUID?,
    val displayName: String,
    val accessReason: MessageAccessReason = MessageAccessReason.TARGETED,
    val canReply: Boolean = true,
)

data class MessageThreadMember(
    val id: UUID,
    val organizationId: UUID,
    val threadId: UUID,
    val userId: UUID,
    val memberType: MessageRecipientType,
    val householdId: UUID?,
    val participantId: UUID?,
    val displayName: String,
    val accessReason: MessageAccessReason,
    val canReply: Boolean,
    val joinedAt: Instant,
    val leftAt: Instant?,
)

data class GuardianObserverLink(
    val athleteUserId: UUID,
    val member: MessageThreadMemberCandidate,
)

data class MyMessageThread(
    val thread: MessageThread,
    val unreadCount: Long,
    val lastMessageAt: Instant,
    val lastMessagePreview: String,
    val canReply: Boolean = false,
    val accessReason: MessageAccessReason = MessageAccessReason.TARGETED,
)

data class MyBroadcastMessage(
    val message: BroadcastMessage,
    val readAt: Instant?,
    val accessReason: MessageAccessReason,
)

data class MessageSafetyReportTarget(
    val organizationId: UUID,
    val threadId: UUID,
    val messageId: UUID,
    val scopeType: MessageScopeType,
    val scopeId: UUID,
)

data class MessageSafetyReport(
    val id: UUID,
    val organizationId: UUID,
    val threadId: UUID,
    val threadTitle: String,
    val messageId: UUID,
    val messageBody: String,
    val messageSenderDisplayName: String,
    val reporterUserId: UUID,
    val reporterDisplayName: String,
    val reason: MessageSafetyReportReason,
    val details: String?,
    val status: MessageSafetyReportStatus,
    val assignedToUserId: UUID?,
    val resolutionNote: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val resolvedAt: Instant?,
    val threadSafetyLockedAt: Instant?,
    val threadSafetyLockReason: String?,
)

data class MessageModerationEvent(
    val id: UUID,
    val organizationId: UUID,
    val reportId: UUID?,
    val threadId: UUID,
    val messageId: UUID?,
    val actorUserId: UUID,
    val actorDisplayName: String,
    val eventType: MessageModerationEventType,
    val note: String?,
    val createdAt: Instant,
)

data class MessageSafeSportPolicy(
    val organizationId: UUID,
    val reviewStatus: MessageSafeSportReviewStatus,
    val athleteMessagingEnabled: Boolean,
    val reviewReference: String?,
    val reviewedByUserId: UUID?,
    val reviewedAt: Instant?,
    val retentionMode: MessageRetentionMode,
    val guardianVisibilityRequired: Boolean,
    val adultMinorOpenTransparentRequired: Boolean,
    val parentDiscontinueRequestsEnforced: Boolean,
    val updatedAt: Instant,
)

data class GuardianMessagingParticipant(
    val organizationId: UUID,
    val participantId: UUID,
    val displayName: String,
)

data class MessageContactRestriction(
    val id: UUID,
    val organizationId: UUID,
    val participantId: UUID,
    val participantDisplayName: String,
    val requestedByUserId: UUID,
    val kind: MessageContactRestrictionKind,
    val note: String?,
    val status: MessageContactRestrictionStatus,
    val createdAt: Instant,
    val liftedAt: Instant?,
    val liftedByUserId: UUID?,
    val liftNote: String?,
)

data class AthleteMessagingTeam(
    val organizationId: UUID,
    val teamId: UUID,
    val teamName: String,
    val athleteMessagingEnabled: Boolean,
)
