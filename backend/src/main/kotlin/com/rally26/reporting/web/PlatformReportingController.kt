package com.rally26.reporting.web

import com.rally26.common.web.CurrentUser
import com.rally26.reporting.application.ReportingService
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

/** Platform-wide report (Phase 9 slice 3, ADR-025) — platform-admin only, distinct from `ReportingController`'s org-scoped reports. */
@RestController
@RequestMapping("/api/v1/platform/reports")
class PlatformReportingController(
    private val reportingService: ReportingService,
) {
    @GetMapping("/overview")
    fun overview(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate?,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): PlatformReportResponse {
        val report = reportingService.getPlatformReport(from, to, currentUser)
        return PlatformReportResponse(
            report.from,
            report.to,
            report.newOrganizations,
            report.activeOrganizations,
            report.newCustomers,
            report.grossTransactionVolumeMinor,
            report.refundedMinor,
            report.refundRatePercent,
            report.webhookProcessed,
            report.webhookFailed,
            report.outboxPending,
            report.outboxDeadLetter,
            report.platformFeeRevenueMinor,
            report.stripeProcessingFeesMinor,
            report.netMarginAfterStripeFeesMinor,
        )
    }
}
