package com.rally26.event.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.rally26.audit.application.AuditService
import com.rally26.common.error.ConflictException
import com.rally26.common.error.NotFoundException
import com.rally26.common.error.ValidationException
import com.rally26.common.web.CurrentUser
import com.rally26.event.domain.EventTemplate
import com.rally26.event.domain.EventTemplateStatus
import com.rally26.event.domain.EventType
import com.rally26.event.domain.EventVisibility
import com.rally26.event.persistence.EventTemplateRepository
import com.rally26.membership.application.MembershipService
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.ZoneId
import java.util.UUID

@Service
class EventTemplateService(
    private val repository: EventTemplateRepository,
    private val membershipService: MembershipService,
    private val auditService: AuditService,
    private val objectMapper: ObjectMapper,
) {

    fun list(organizationId: UUID, includeArchived: Boolean, currentUser: CurrentUser): List<EventTemplate> {
        if (includeArchived) {
            membershipService.requireManagerRole(organizationId, currentUser)
        } else {
            membershipService.requireActiveMembership(organizationId, currentUser)
        }
        return repository.listForOrganization(organizationId, includeArchived)
    }

    @Transactional
    fun create(
        organizationId: UUID,
        name: String,
        eventType: EventType,
        title: String?,
        description: String?,
        durationMinutes: Int?,
        arrivalOffsetMinutes: Int?,
        meetingOffsetMinutes: Int?,
        timezone: String,
        venueName: String?,
        address: String?,
        area: String?,
        meetingPoint: String?,
        directionsNotes: String?,
        visibility: EventVisibility,
        currentUser: CurrentUser,
    ): EventTemplate {
        membershipService.requireManagerRole(organizationId, currentUser)
        val input = normalize(
            name, title, description, durationMinutes, arrivalOffsetMinutes, meetingOffsetMinutes,
            timezone, venueName, address, area, meetingPoint, directionsNotes,
        )
        val template = try {
            repository.insert(
                organizationId, input.name, eventType, input.title, input.description,
                input.durationMinutes, input.arrivalOffsetMinutes, input.meetingOffsetMinutes,
                input.timezone, input.venueName, input.address, input.area, input.meetingPoint,
                input.directionsNotes, visibility, currentUser.userId,
            )
        } catch (_: DuplicateKeyException) {
            throw duplicateName()
        }
        auditService.record(
            currentUser.userId,
            organizationId,
            "event_template.created",
            "event_template",
            template.id,
            objectMapper.writeValueAsString(mapOf("name" to template.name, "eventType" to template.eventType.name)),
        )
        return template
    }

    @Transactional
    fun update(
        organizationId: UUID,
        templateId: UUID,
        name: String,
        eventType: EventType,
        title: String?,
        description: String?,
        durationMinutes: Int?,
        arrivalOffsetMinutes: Int?,
        meetingOffsetMinutes: Int?,
        timezone: String,
        venueName: String?,
        address: String?,
        area: String?,
        meetingPoint: String?,
        directionsNotes: String?,
        visibility: EventVisibility,
        currentUser: CurrentUser,
    ): EventTemplate {
        membershipService.requireManagerRole(organizationId, currentUser)
        val existing = requireTemplate(organizationId, templateId)
        if (existing.status != EventTemplateStatus.ACTIVE) {
            throw ConflictException("EVENT_TEMPLATE_ARCHIVED", "Archived event templates cannot be edited.")
        }
        val input = normalize(
            name, title, description, durationMinutes, arrivalOffsetMinutes, meetingOffsetMinutes,
            timezone, venueName, address, area, meetingPoint, directionsNotes,
        )
        val updated = try {
            repository.update(
                templateId, organizationId, input.name, eventType, input.title, input.description,
                input.durationMinutes, input.arrivalOffsetMinutes, input.meetingOffsetMinutes,
                input.timezone, input.venueName, input.address, input.area, input.meetingPoint,
                input.directionsNotes, visibility, currentUser.userId,
            )
        } catch (_: DuplicateKeyException) {
            throw duplicateName()
        }
        if (updated == 0) {
            throw ConflictException("EVENT_TEMPLATE_ARCHIVED", "This event template is no longer active.")
        }
        auditService.record(
            currentUser.userId,
            organizationId,
            "event_template.updated",
            "event_template",
            templateId,
            objectMapper.writeValueAsString(mapOf("name" to input.name, "eventType" to eventType.name)),
        )
        return repository.findById(templateId, organizationId)!!
    }

    @Transactional
    fun archive(organizationId: UUID, templateId: UUID, currentUser: CurrentUser): EventTemplate {
        membershipService.requireManagerRole(organizationId, currentUser)
        val existing = requireTemplate(organizationId, templateId)
        if (existing.status == EventTemplateStatus.ARCHIVED) return existing
        val archived = repository.archive(templateId, organizationId, currentUser.userId)
        if (archived == 0) {
            return repository.findById(templateId, organizationId)
                ?: throw NotFoundException("EVENT_TEMPLATE_NOT_FOUND", "The event template could not be found.")
        }
        auditService.record(
            currentUser.userId,
            organizationId,
            "event_template.archived",
            "event_template",
            templateId,
            objectMapper.writeValueAsString(mapOf("name" to existing.name)),
        )
        return repository.findById(templateId, organizationId)!!
    }

    private fun requireTemplate(organizationId: UUID, templateId: UUID): EventTemplate =
        repository.findById(templateId, organizationId)
            ?: throw NotFoundException("EVENT_TEMPLATE_NOT_FOUND", "The event template could not be found.")

    private fun normalize(
        name: String,
        title: String?,
        description: String?,
        durationMinutes: Int?,
        arrivalOffsetMinutes: Int?,
        meetingOffsetMinutes: Int?,
        timezone: String,
        venueName: String?,
        address: String?,
        area: String?,
        meetingPoint: String?,
        directionsNotes: String?,
    ): NormalizedTemplateInput {
        val normalizedName = name.trim()
        if (normalizedName.length !in 2..120) {
            throw ValidationException("Template name must be between 2 and 120 characters.")
        }
        validateRange("Duration", durationMinutes, 1, 1440)
        validateRange("Arrival offset", arrivalOffsetMinutes, 0, 1440)
        validateRange("Meeting offset", meetingOffsetMinutes, 0, 1440)
        val normalizedTimezone = timezone.trim()
        try {
            ZoneId.of(normalizedTimezone)
        } catch (_: Exception) {
            throw ValidationException("Timezone must be a valid IANA time zone id (e.g. America/New_York).")
        }
        return NormalizedTemplateInput(
            name = normalizedName,
            title = normalizeOptional(title, 200, "Title"),
            description = normalizeOptional(description, 2000, "Description"),
            durationMinutes = durationMinutes,
            arrivalOffsetMinutes = arrivalOffsetMinutes,
            meetingOffsetMinutes = meetingOffsetMinutes,
            timezone = normalizedTimezone,
            venueName = normalizeOptional(venueName, 200, "Venue"),
            address = normalizeOptional(address, 300, "Address"),
            area = normalizeOptional(area, 120, "Field/court/area"),
            meetingPoint = normalizeOptional(meetingPoint, 300, "Meeting point"),
            directionsNotes = normalizeOptional(directionsNotes, 1000, "Directions notes"),
        )
    }

    private fun validateRange(label: String, value: Int?, minimum: Int, maximum: Int) {
        if (value != null && value !in minimum..maximum) {
            throw ValidationException("$label must be between $minimum and $maximum minutes.")
        }
    }

    private fun normalizeOptional(value: String?, maximum: Int, label: String): String? {
        val normalized = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (normalized.length > maximum) throw ValidationException("$label must not exceed $maximum characters.")
        return normalized
    }

    private fun duplicateName() =
        ConflictException("EVENT_TEMPLATE_NAME_CONFLICT", "An active event template already uses this name.")

    private data class NormalizedTemplateInput(
        val name: String,
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
    )
}
