package com.rally26.reconciliation.web

import com.rally26.common.web.CurrentUser
import com.rally26.common.web.PageResponse
import com.rally26.reconciliation.application.ReconciliationService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/reconciliation-runs")
class ReconciliationController(private val service: ReconciliationService) {
    @PostMapping
    fun run(
        @PathVariable organizationId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<ReconciliationResultResponse> = ResponseEntity.status(HttpStatus.CREATED)
        .body(service.run(organizationId, currentUser).toResponse())

    @GetMapping("/latest")
    fun latest(
        @PathVariable organizationId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<ReconciliationResultResponse> {
        val result = service.latest(organizationId, currentUser) ?: return ResponseEntity.noContent().build()
        return ResponseEntity.ok(result.toResponse())
    }

    @GetMapping("/{runId}")
    fun get(
        @PathVariable organizationId: UUID,
        @PathVariable runId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ReconciliationResultResponse = service.get(organizationId, runId, currentUser).toResponse()

    @GetMapping
    fun list(
        @PathVariable organizationId: UUID,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): PageResponse<ReconciliationRunResponse> {
        val normalizedSize = size.coerceIn(1, 100)
        val items = service.list(organizationId, page.coerceAtLeast(0) * normalizedSize, normalizedSize, currentUser).map { it.toResponse() }
        return PageResponse(items, page.coerceAtLeast(0), normalizedSize, service.count(organizationId, currentUser))
    }
}
