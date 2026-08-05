package com.rally26.outbox.web

import com.rally26.common.web.CurrentUser
import com.rally26.outbox.application.OutboxAdminService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Platform-admin outbox operational endpoints (DESIGN-DOC.md section 17, Phase 8
 * slice 1, ADR-022) — distinct from `DashboardController`'s bare `/platform/dashboard/
 * outbox-health` counts, this exposes the actual dead-letter/failed rows and a manual
 * reprocess action.
 */
@RestController
@RequestMapping("/api/v1/admin/outbox-events")
class OutboxAdminController(
    private val outboxAdminService: OutboxAdminService,
) {
    @GetMapping("/dead-letter")
    fun listDeadLetter(
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): List<OutboxEventResponse> = outboxAdminService.listDeadLetter(currentUser).map { it.toResponse() }

    @GetMapping("/failed")
    fun listFailed(
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): List<OutboxEventResponse> = outboxAdminService.listFailed(currentUser).map { it.toResponse() }

    @PostMapping("/{id}/reprocess")
    fun reprocess(
        @PathVariable id: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ) {
        outboxAdminService.reprocess(id, currentUser)
    }
}
