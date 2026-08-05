package com.rally26.payout.web

import com.rally26.common.web.CurrentUser
import com.rally26.payout.application.PayoutAccountService
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/payout-account")
class PayoutAccountController(
    private val payoutAccountService: PayoutAccountService,
) {
    @GetMapping
    fun getStatus(
        @PathVariable organizationId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): PayoutAccountResponse? = payoutAccountService.getStatus(organizationId, currentUser)?.toResponse()

    @PostMapping("/onboarding-link")
    fun startOnboarding(
        @PathVariable organizationId: UUID,
        @Valid @RequestBody request: StartOnboardingRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): OnboardingLinkResponse {
        val url = payoutAccountService.startOnboarding(organizationId, request.refreshUrl, request.returnUrl, currentUser)
        return OnboardingLinkResponse(url)
    }

    @PostMapping("/refresh")
    fun refreshStatus(
        @PathVariable organizationId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): PayoutAccountResponse = payoutAccountService.refreshStatus(organizationId, currentUser).toResponse()

    @GetMapping("/summary")
    fun getPayoutSummary(
        @PathVariable organizationId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): PayoutSummaryResponse = payoutAccountService.getPayoutSummary(organizationId, currentUser).toResponse()

    @PostMapping("/transfer")
    fun triggerTransfer(
        @PathVariable organizationId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): PayoutSummaryResponse = payoutAccountService.triggerTransfer(organizationId, currentUser).toResponse()
}
