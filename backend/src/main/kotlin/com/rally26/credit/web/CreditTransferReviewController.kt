package com.rally26.credit.web

import com.rally26.common.web.CurrentUser
import com.rally26.credit.application.FamilyCreditService
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Manager-facing review queue for guardian-initiated P2P credit transfers
 * (Phase 23) — a transfer only moves value once approved here, mirroring
 * ProfileCorrectionController's PENDING/APPROVED/REJECTED review endpoints.
 */
@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/credit-transfers")
class CreditTransferReviewController(
    private val familyCreditService: FamilyCreditService,
) {
    @GetMapping("/pending")
    fun listPending(
        @PathVariable organizationId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): List<FamilyCreditTransferResponse> = familyCreditService.listPendingTransfers(organizationId, currentUser).map { it.toResponse() }

    @PostMapping("/{transferId}/approve")
    fun approve(
        @PathVariable organizationId: UUID,
        @PathVariable transferId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): FamilyCreditTransferResponse = familyCreditService.approveTransfer(organizationId, transferId, currentUser).toResponse()

    @PostMapping("/{transferId}/reject")
    fun reject(
        @PathVariable organizationId: UUID,
        @PathVariable transferId: UUID,
        @Valid @RequestBody request: ReviewCreditTransferRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): FamilyCreditTransferResponse =
        familyCreditService.rejectTransfer(organizationId, transferId, request.reviewNote, currentUser).toResponse()
}
