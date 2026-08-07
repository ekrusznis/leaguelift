package com.rally26.messaging.web

import com.rally26.common.web.CurrentUser
import com.rally26.common.web.PageRequest
import com.rally26.common.web.PageResponse
import com.rally26.messaging.application.MessageSafetyService
import com.rally26.messaging.domain.MessageSafetyReportStatus
import com.rally26.messaging.domain.MessageScopeType
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
@RequestMapping("/api/v1/me")
class MyMessageSafetyController(
    private val safetyService: MessageSafetyService,
) {
    @PostMapping("/messages/{messageId}/reports")
    fun reportMessage(
        @PathVariable messageId: UUID,
        @Valid @RequestBody request: CreateMessageSafetyReportRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<MessageSafetyReportResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(
            safetyService.reportMessage(messageId, request.reason, request.details, currentUser).toResponse(),
        )

    @GetMapping("/message-reports")
    fun myReports(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "25") size: Int,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): PageResponse<MessageSafetyReportResponse> {
        val result = safetyService.listMine(PageRequest(page, size), currentUser)
        return PageResponse(result.items.map { it.toResponse() }, result.page, result.size, result.totalElements)
    }
}

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}")
class MessageSafetyManagementController(
    private val safetyService: MessageSafetyService,
) {
    @GetMapping("/message-reports")
    fun listReports(
        @PathVariable organizationId: UUID,
        @RequestParam(required = false) scopeType: MessageScopeType?,
        @RequestParam(required = false) scopeId: UUID?,
        @RequestParam(required = false) status: MessageSafetyReportStatus?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "25") size: Int,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): PageResponse<MessageSafetyReportResponse> {
        val result = safetyService.listForManagement(organizationId, scopeType, scopeId, status, PageRequest(page, size), currentUser)
        return PageResponse(result.items.map { it.toResponse() }, result.page, result.size, result.totalElements)
    }

    @PatchMapping("/message-reports/{reportId}")
    fun reviewReport(
        @PathVariable organizationId: UUID,
        @PathVariable reportId: UUID,
        @Valid @RequestBody request: ReviewMessageSafetyReportRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): MessageSafetyReportResponse =
        safetyService.reviewReport(organizationId, reportId, request.status, request.note, currentUser).toResponse()

    @GetMapping("/message-reports/{reportId}/events")
    fun moderationEvents(
        @PathVariable organizationId: UUID,
        @PathVariable reportId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): List<MessageModerationEventResponse> =
        safetyService.listModerationEvents(organizationId, reportId, currentUser).map { it.toResponse() }

    @PostMapping("/message-threads/{threadId}/safety-lock")
    fun lockThread(
        @PathVariable organizationId: UUID,
        @PathVariable threadId: UUID,
        @Valid @RequestBody request: SafetyLockThreadRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): MessageThreadResponse =
        safetyService.lockThread(organizationId, threadId, request.reason, request.reportId, currentUser).toResponse()

    @PostMapping("/message-threads/{threadId}/safety-unlock")
    fun unlockThread(
        @PathVariable organizationId: UUID,
        @PathVariable threadId: UUID,
        @Valid @RequestBody request: SafetyUnlockThreadRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): MessageThreadResponse =
        safetyService.unlockThread(organizationId, threadId, request.note, request.reportId, currentUser).toResponse()
}
