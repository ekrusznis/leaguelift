package com.rally26.event.web

import com.rally26.common.web.CurrentUser
import com.rally26.event.application.EventTemplateService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/event-templates")
class EventTemplateController(
    private val service: EventTemplateService,
) {
    @GetMapping
    fun list(
        @PathVariable organizationId: UUID,
        @RequestParam(defaultValue = "false") includeArchived: Boolean,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): List<EventTemplateResponse> = service.list(organizationId, includeArchived, currentUser).map { it.toResponse() }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @PathVariable organizationId: UUID,
        @Valid @RequestBody request: SaveEventTemplateRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): EventTemplateResponse =
        service
            .create(
                organizationId,
                request.name,
                request.eventType,
                request.title,
                request.description,
                request.durationMinutes,
                request.arrivalOffsetMinutes,
                request.meetingOffsetMinutes,
                request.timezone,
                request.venueName,
                request.address,
                request.area,
                request.meetingPoint,
                request.directionsNotes,
                request.visibility,
                currentUser,
            ).toResponse()

    @PutMapping("/{templateId}")
    fun update(
        @PathVariable organizationId: UUID,
        @PathVariable templateId: UUID,
        @Valid @RequestBody request: SaveEventTemplateRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): EventTemplateResponse =
        service
            .update(
                organizationId,
                templateId,
                request.name,
                request.eventType,
                request.title,
                request.description,
                request.durationMinutes,
                request.arrivalOffsetMinutes,
                request.meetingOffsetMinutes,
                request.timezone,
                request.venueName,
                request.address,
                request.area,
                request.meetingPoint,
                request.directionsNotes,
                request.visibility,
                currentUser,
            ).toResponse()

    @PostMapping("/{templateId}/archive")
    fun archive(
        @PathVariable organizationId: UUID,
        @PathVariable templateId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): EventTemplateResponse = service.archive(organizationId, templateId, currentUser).toResponse()
}
