package com.rally26.communication.domain

import java.time.Instant
import java.util.UUID

enum class AnnouncementScopeType { ORGANIZATION, TEAM, TOURNAMENT }
enum class AnnouncementKind { GENERAL, CAMPAIGN_LAUNCH, EVENT_REMINDER, FEE_REMINDER, DOCUMENT_REMINDER }
enum class AnnouncementAudience { ALL, STAFF, GUARDIANS, ATHLETES }
enum class AnnouncementStatus { DRAFT, PUBLISHED, ARCHIVED }
enum class AnnouncementRecipientType { STAFF, GUARDIAN, ATHLETE }
enum class DeliveryStatus { NONE, PENDING, SENT, FAILED, SKIPPED }

data class Announcement(
    val id: UUID,
    val organizationId: UUID,
    val scopeType: AnnouncementScopeType,
    val scopeId: UUID,
    val scopeName: String?,
    val kind: AnnouncementKind,
    val relatedEntityType: String?,
    val relatedEntityId: UUID?,
    val targetHouseholdId: UUID?,
    val sourceKey: String?,
    val title: String,
    val body: String,
    val audience: AnnouncementAudience,
    val status: AnnouncementStatus,
    val emailEnabled: Boolean,
    val smsEnabled: Boolean,
    val createdByUserId: UUID,
    val publishedByUserId: UUID?,
    val publishedAt: Instant?,
    val archivedAt: Instant?,
    val recipientCount: Long,
    val emailSentCount: Long,
    val emailFailedCount: Long,
    val smsSentCount: Long,
    val smsFailedCount: Long,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class AnnouncementRecipientCandidate(
    val recipientType: AnnouncementRecipientType,
    val userId: UUID?,
    val householdId: UUID?,
    val displayName: String,
    val email: String?,
    val phone: String?,
)

data class AnnouncementRecipient(
    val id: UUID,
    val announcementId: UUID,
    val recipientKey: String,
    val recipientType: AnnouncementRecipientType,
    val userId: UUID?,
    val householdId: UUID?,
    val displayName: String,
    val email: String?,
    val phone: String?,
    val inAppVisible: Boolean,
    val emailStatus: DeliveryStatus,
    val smsStatus: DeliveryStatus,
    val readAt: Instant?,
    val lastError: String?,
)

data class MyAnnouncement(
    val announcement: Announcement,
    val recipientId: UUID,
    val readAt: Instant?,
)
