package com.rally26.communication.web

import com.rally26.communication.application.ReminderResourceType
import com.rally26.communication.domain.Announcement
import com.rally26.communication.domain.AnnouncementAudience
import com.rally26.communication.domain.AnnouncementScopeType
import com.rally26.communication.domain.MyAnnouncement
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class SaveAnnouncementRequest(
    @field:NotNull val scopeType: AnnouncementScopeType,
    val scopeId: UUID? = null,
    @field:NotBlank @field:Size(min = 8, max = 120) val idempotencyKey: String,
    @field:NotBlank @field:Size(max = 180) val title: String,
    @field:NotBlank @field:Size(max = 5000) val body: String,
    @field:NotNull val audience: AnnouncementAudience,
    val emailEnabled: Boolean = true,
    val smsEnabled: Boolean = false,
)

data class UpdateAnnouncementRequest(
    @field:NotBlank @field:Size(max = 180) val title: String,
    @field:NotBlank @field:Size(max = 5000) val body: String,
    @field:NotNull val audience: AnnouncementAudience,
    val emailEnabled: Boolean = true,
    val smsEnabled: Boolean = false,
)

data class SendReminderRequest(
    @field:NotNull val resourceType: ReminderResourceType,
    @field:NotNull val resourceId: UUID,
    @field:NotBlank @field:Size(min = 8, max = 100) val idempotencyKey: String,
    val emailEnabled: Boolean = true,
    val smsEnabled: Boolean = false,
)

data class AnnouncementResponse(
    val id: UUID,
    val organizationId: UUID,
    val scopeType: String,
    val scopeId: UUID,
    val scopeName: String?,
    val kind: String,
    val relatedEntityType: String?,
    val relatedEntityId: UUID?,
    val title: String,
    val body: String,
    val audience: String,
    val status: String,
    val emailEnabled: Boolean,
    val smsEnabled: Boolean,
    val publishedAt: Instant?,
    val recipientCount: Long,
    val emailSentCount: Long,
    val emailFailedCount: Long,
    val smsSentCount: Long,
    val smsFailedCount: Long,
    val createdAt: Instant,
    val updatedAt: Instant,
)

fun Announcement.toResponse() = AnnouncementResponse(
    id, organizationId, scopeType.name, scopeId, scopeName, kind.name, relatedEntityType, relatedEntityId,
    title, body, audience.name, status.name, emailEnabled, smsEnabled, publishedAt, recipientCount,
    emailSentCount, emailFailedCount, smsSentCount, smsFailedCount, createdAt, updatedAt,
)

data class MyAnnouncementResponse(
    val announcement: AnnouncementResponse,
    val readAt: Instant?,
)

fun MyAnnouncement.toResponse() = MyAnnouncementResponse(announcement.toResponse(), readAt)
