package com.rally26.event.web

import com.rally26.event.domain.EventTemplate
import com.rally26.event.domain.EventType
import com.rally26.event.domain.EventVisibility
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class SaveEventTemplateRequest(
    @field:NotBlank @field:Size(max = 120) val name: String,
    @field:NotNull val eventType: EventType,
    @field:Size(max = 200) val title: String? = null,
    @field:Size(max = 2000) val description: String? = null,
    @field:Min(1) @field:Max(1440) val durationMinutes: Int? = null,
    @field:Min(0) @field:Max(1440) val arrivalOffsetMinutes: Int? = null,
    @field:Min(0) @field:Max(1440) val meetingOffsetMinutes: Int? = null,
    @field:NotBlank @field:Size(max = 100) val timezone: String,
    @field:Size(max = 200) val venueName: String? = null,
    @field:Size(max = 300) val address: String? = null,
    @field:Size(max = 120) val area: String? = null,
    @field:Size(max = 300) val meetingPoint: String? = null,
    @field:Size(max = 1000) val directionsNotes: String? = null,
    @field:NotNull val visibility: EventVisibility,
)

data class EventTemplateResponse(
    val id: UUID,
    val organizationId: UUID,
    val name: String,
    val eventType: String,
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
    val visibility: String,
    val status: String,
    val createdAt: Instant,
    val updatedAt: Instant,
)

fun EventTemplate.toResponse() = EventTemplateResponse(
    id = id,
    organizationId = organizationId,
    name = name,
    eventType = eventType.name,
    title = title,
    description = description,
    durationMinutes = durationMinutes,
    arrivalOffsetMinutes = arrivalOffsetMinutes,
    meetingOffsetMinutes = meetingOffsetMinutes,
    timezone = timezone,
    venueName = venueName,
    address = address,
    area = area,
    meetingPoint = meetingPoint,
    directionsNotes = directionsNotes,
    visibility = visibility.name,
    status = status.name,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
