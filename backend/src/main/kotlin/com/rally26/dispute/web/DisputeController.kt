package com.rally26.dispute.web

import com.rally26.common.web.CurrentUser
import com.rally26.dispute.application.DisputeService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
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
}
