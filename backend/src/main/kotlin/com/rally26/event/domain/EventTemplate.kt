package com.rally26.event.domain

import java.time.Instant
import java.util.UUID

enum class EventTemplateStatus { ACTIVE, ARCHIVED }

data class EventTemplate(
    val id: UUID,
    val organizationId: UUID,
    val name: String,
    val eventType: EventType,
    val title: String?,
    val description: String?,
    val durationMinutes: Int?,
    val arrivalOffsetMinutes: Int?,
    val meetingOffsetMinutes: Int?,
    val timezone: String,
    val venueName: String?,
    val address: String?,
    val area: String?,
    val meetingPoint: String?,
    val directionsNotes: String?,
    val visibility: EventVisibility,
    val status: EventTemplateStatus,
    val createdByUserId: UUID,
    val updatedByUserId: UUID,
    val createdAt: Instant,
    val updatedAt: Instant,
)
