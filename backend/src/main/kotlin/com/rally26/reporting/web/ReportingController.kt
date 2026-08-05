package com.rally26.reporting.web

import com.rally26.common.web.CurrentUser
import com.rally26.reporting.application.ReportingService
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.util.UUID

/** Org reports (Phase 9 slice 1, ADR-025) — `from`/`to` default to the trailing 30 days when omitted; see `ReportingService`. */
@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/reports")
class ReportingController(
    private val reportingService: ReportingService,
) {
    @GetMapping("/revenue")
    fun revenue(
        @PathVariable organizationId: UUID,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate?,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): RevenueReportResponse {
        val report = reportingService.getRevenueReport(organizationId, from, to, currentUser)
        return RevenueReportResponse(
            report.from,
            report.to,
            report.totalMinor,
            report.bySourceType.map { it.toResponse() },
            report.byTeam.map { it.toResponse() },
        )
    }

    @GetMapping("/revenue/export", produces = ["text/csv"])
    fun exportRevenue(
        @PathVariable organizationId: UUID,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate?,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<String> {
        val csv = reportingService.exportRevenueReportCsv(organizationId, from, to, currentUser)
        return ResponseEntity
            .ok()
            .contentType(MediaType.parseMediaType("text/csv"))
            .header("Content-Disposition", "attachment; filename=\"revenue-$organizationId.csv\"")
            .body(csv)
    }

    @GetMapping("/campaigns")
    fun campaigns(
        @PathVariable organizationId: UUID,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate?,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): List<CampaignRevenueResponse> = reportingService.getCampaignBreakdown(organizationId, from, to, currentUser).map { it.toResponse() }

    @GetMapping("/products")
    fun products(
        @PathVariable organizationId: UUID,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate?,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): List<ProductPerformanceResponse> =
        reportingService.getProductPerformance(organizationId, from, to, currentUser).map { it.toResponse() }

    @GetMapping("/refunds")
    fun refunds(
        @PathVariable organizationId: UUID,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate?,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): RefundsReportResponse {
        val report = reportingService.getRefundsReport(organizationId, from, to, currentUser)
        return RefundsReportResponse(report.from, report.to, report.count, report.totalMinor, report.refunds.map { it.toResponse() })
    }

    @GetMapping("/fee-collections")
    fun feeCollections(
        @PathVariable organizationId: UUID,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate?,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): FeeCollectionsReportResponse {
        val report = reportingService.getFeeCollectionsReport(organizationId, from, to, currentUser)
        return FeeCollectionsReportResponse(
            report.from,
            report.to,
            report.collectedMinor,
            report.outstandingMinor,
            report.payments.map { it.toResponse() },
        )
    }

    @GetMapping("/households/{householdId}/fees")
    fun householdFees(
        @PathVariable organizationId: UUID,
        @PathVariable householdId: UUID,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate?,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): FeeCollectionsReportResponse {
        val report = reportingService.getHouseholdFeeReport(organizationId, householdId, from, to, currentUser)
        return FeeCollectionsReportResponse(
            report.from,
            report.to,
            report.collectedMinor,
            report.outstandingMinor,
            report.payments.map { it.toResponse() },
        )
    }
}
