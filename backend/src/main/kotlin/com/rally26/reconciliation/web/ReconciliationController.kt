package com.rally26.reconciliation.web

import com.rally26.common.error.ValidationException
import com.rally26.common.web.CurrentUser
import com.rally26.common.web.PageResponse
import com.rally26.reconciliation.application.ReconciliationService
import com.rally26.reconciliation.domain.ReconciliationRunStatus
import com.rally26.reconciliation.domain.ReconciliationSeverity
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
class ReconciliationController(
    private val service: ReconciliationService,
) {
    @PostMapping
    fun run(
        @PathVariable organizationId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<ReconciliationResultResponse> =
        ResponseEntity
            .status(HttpStatus.CREATED)
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
        @RequestParam(required = false) status: ReconciliationRunStatus?,
        @RequestParam(defaultValue = "newest") sort: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): PageResponse<ReconciliationRunResponse> {
        val safePage = page.coerceAtLeast(0)
        val safeSize = size.coerceIn(1, 100)
        val ascending = parseAscending(sort)
        val items =
            service
                .searchRuns(
                    organizationId,
                    status,
                    ascending,
                    safePage * safeSize,
                    safeSize,
                    currentUser,
                ).map { it.toResponse() }
        return PageResponse(items, safePage, safeSize, service.countRunsFiltered(organizationId, status, currentUser))
    }

    @GetMapping("/{runId}/issues")
    fun listIssues(
        @PathVariable organizationId: UUID,
        @PathVariable runId: UUID,
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) severity: ReconciliationSeverity?,
        @RequestParam(required = false) resourceType: String?,
        @RequestParam(defaultValue = "newest") sort: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): PageResponse<ReconciliationIssueResponse> {
        val safePage = page.coerceAtLeast(0)
        val safeSize = size.coerceIn(1, 100)
        val ascending = parseAscending(sort)
        val items =
            service
                .listIssues(
                    organizationId,
                    runId,
                    q,
                    severity,
                    resourceType,
                    ascending,
                    safePage * safeSize,
                    safeSize,
                    currentUser,
                ).map { it.toResponse() }
        val total = service.countIssues(organizationId, runId, q, severity, resourceType, currentUser)
        return PageResponse(items, safePage, safeSize, total)
    }

    private fun parseAscending(sort: String): Boolean =
        when (sort.lowercase()) {
            "newest" -> false
            "oldest" -> true
            else -> throw ValidationException("sort must be 'newest' or 'oldest'.")
        }
}
