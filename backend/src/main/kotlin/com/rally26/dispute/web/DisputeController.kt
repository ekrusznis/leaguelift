package com.rally26.dispute.web

import com.rally26.common.error.ValidationException
import com.rally26.common.web.CurrentUser
import com.rally26.common.web.PageResponse
import com.rally26.dispute.application.DisputeService
import com.rally26.dispute.domain.DisputeSourceType
import com.rally26.dispute.domain.DisputeStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** Read-only dispute visibility (DESIGN-DOC.md §14.6 item #4) — no actions here; evidence submission stays manual, in the Stripe Dashboard, per docs/DISPUTE-CHARGEBACK-RUNBOOK.md. */
@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/disputes")
class DisputeController(
    private val disputeService: DisputeService,
) {
    @GetMapping
    fun list(
        @PathVariable organizationId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): List<DisputeResponse> = disputeService.list(organizationId, currentUser).map { it.toResponse() }

    @GetMapping("/search")
    fun search(
        @PathVariable organizationId: UUID,
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) status: DisputeStatus?,
        @RequestParam(required = false) sourceType: DisputeSourceType?,
        @RequestParam(defaultValue = "newest") sort: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): PageResponse<DisputeResponse> {
        val safePage = page.coerceAtLeast(0)
        val safeSize = size.coerceIn(1, 100)
        val ascending =
            when (sort.lowercase()) {
                "newest" -> false
                "oldest" -> true
                else -> throw ValidationException("sort must be 'newest' or 'oldest'.")
            }
        val items =
            disputeService
                .search(
                    organizationId,
                    q,
                    status,
                    sourceType,
                    ascending,
                    safePage * safeSize,
                    safeSize,
                    currentUser,
                ).map { it.toResponse() }
        val total = disputeService.count(organizationId, q, status, sourceType, currentUser)
        return PageResponse(items, safePage, safeSize, total)
    }
}
