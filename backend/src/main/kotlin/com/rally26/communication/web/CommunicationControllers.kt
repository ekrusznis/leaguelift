package com.rally26.communication.web

import com.rally26.common.web.CurrentUser
import com.rally26.common.web.PageRequest
import com.rally26.common.web.PageResponse
import com.rally26.communication.application.AnnouncementService
import com.rally26.communication.application.ReminderService
import com.rally26.communication.domain.AnnouncementScopeType
import com.rally26.communication.domain.AnnouncementStatus
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/announcements")
class AnnouncementController(private val service: AnnouncementService) {
    @GetMapping
    fun list(
        @PathVariable organizationId: UUID,
        @RequestParam(required = false) scopeType: AnnouncementScopeType?,
        @RequestParam(required = false) scopeId: UUID?,
        @RequestParam(required = false) status: AnnouncementStatus?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "25") size: Int,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): PageResponse<AnnouncementResponse> {
        val result = service.listForManagement(organizationId, scopeType, scopeId, status, PageRequest(page, size), currentUser)
        return PageResponse(result.items.map { it.toResponse() }, result.page, result.size, result.totalElements)
    }

    @PostMapping
    fun create(
        @PathVariable organizationId: UUID,
        @Valid @RequestBody request: SaveAnnouncementRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<AnnouncementResponse> = ResponseEntity.status(HttpStatus.CREATED).body(
        service.createDraft(
            organizationId, request.scopeType, request.scopeId, request.idempotencyKey, request.title, request.body,
            request.audience, request.emailEnabled, request.smsEnabled, currentUser,
        ).toResponse(),
    )

    @GetMapping("/{announcementId}")
    fun get(
        @PathVariable organizationId: UUID,
        @PathVariable announcementId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ) = service.get(organizationId, announcementId, currentUser).toResponse()

    @PatchMapping("/{announcementId}")
    fun update(
        @PathVariable organizationId: UUID,
        @PathVariable announcementId: UUID,
        @Valid @RequestBody request: UpdateAnnouncementRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ) = service.updateDraft(
        organizationId, announcementId, request.title, request.body, request.audience,
        request.emailEnabled, request.smsEnabled, currentUser,
    ).toResponse()

    @PostMapping("/{announcementId}/publish")
    fun publish(
        @PathVariable organizationId: UUID,
        @PathVariable announcementId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ) = service.publish(organizationId, announcementId, currentUser).toResponse()

    @PostMapping("/{announcementId}/archive")
    fun archive(
        @PathVariable organizationId: UUID,
        @PathVariable announcementId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ) = service.archive(organizationId, announcementId, currentUser).toResponse()
}

@RestController
@RequestMapping("/api/v1/me/announcements")
class MyAnnouncementController(private val service: AnnouncementService) {
    @GetMapping
    fun list(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "25") size: Int,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): PageResponse<MyAnnouncementResponse> {
        val result = service.listMine(currentUser, PageRequest(page, size))
        return PageResponse(result.items.map { it.toResponse() }, result.page, result.size, result.totalElements)
    }

    @PostMapping("/{announcementId}/read")
    fun markRead(@PathVariable announcementId: UUID, @AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Void> {
        service.markRead(currentUser, announcementId)
        return ResponseEntity.noContent().build()
    }
}

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/reminders")
class ReminderController(private val service: ReminderService) {
    @PostMapping
    fun send(
        @PathVariable organizationId: UUID,
        @Valid @RequestBody request: SendReminderRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<AnnouncementResponse> = ResponseEntity.status(HttpStatus.CREATED).body(
        service.send(
            organizationId, request.resourceType, request.resourceId, request.idempotencyKey,
            request.emailEnabled, request.smsEnabled, currentUser,
        ).toResponse(),
    )
}
