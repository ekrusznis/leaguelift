package com.rally26.fee.web

import com.rally26.common.error.ValidationException
import com.rally26.common.web.CurrentUser
import com.rally26.common.web.PageResponse
import com.rally26.fee.application.FeeSearchService
import com.rally26.fee.domain.FeeAssignmentSearchCriteria
import com.rally26.fee.domain.FeeAssignmentSearchSort
import com.rally26.fee.domain.FeeAssignmentStatus
import com.rally26.fee.domain.FeeTemplateSearchCriteria
import com.rally26.fee.domain.FeeTemplateSearchSort
import com.rally26.fee.domain.FeeTemplateStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}")
class FeeSearchController(
    private val service: FeeSearchService,
) {
    @GetMapping("/fee-templates/search")
    fun searchTemplates(
        @PathVariable organizationId: UUID,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "25") size: Int,
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) status: FeeTemplateStatus?,
        @RequestParam(defaultValue = "NAME_ASC") sort: FeeTemplateSearchSort,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): PageResponse<FeeTemplateResponse> {
        validatePage(page, size)
        val criteria = FeeTemplateSearchCriteria(q, status, sort)
        val items = service.searchTemplates(organizationId, criteria, currentUser, page * size, size).map { it.toResponse() }
        val total = service.countTemplates(organizationId, criteria, currentUser)
        return PageResponse(items, page, size, total)
    }

    @GetMapping("/households/{householdId}/fee-assignments/search")
    fun searchHouseholdAssignments(
        @PathVariable organizationId: UUID,
        @PathVariable householdId: UUID,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "25") size: Int,
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) status: FeeAssignmentStatus?,
        @RequestParam(defaultValue = "false") overdueOnly: Boolean,
        @RequestParam(defaultValue = "DUE_DATE_ASC") sort: FeeAssignmentSearchSort,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): PageResponse<FeeAssignmentResponse> {
        validatePage(page, size)
        val criteria = FeeAssignmentSearchCriteria(q, status, overdueOnly, sort)
        val items =
            service
                .searchHouseholdAssignments(organizationId, householdId, criteria, currentUser, page * size, size)
                .map { it.toResponse() }
        val total = service.countHouseholdAssignments(organizationId, householdId, criteria, currentUser)
        return PageResponse(items, page, size, total)
    }

    @GetMapping("/fee-assignments/search")
    fun searchOrganizationAssignments(
        @PathVariable organizationId: UUID,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "25") size: Int,
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) status: FeeAssignmentStatus?,
        @RequestParam(defaultValue = "false") overdueOnly: Boolean,
        @RequestParam(defaultValue = "DUE_DATE_ASC") sort: FeeAssignmentSearchSort,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): PageResponse<FeeAssignmentSummaryResponse> {
        validatePage(page, size)
        val criteria = FeeAssignmentSearchCriteria(q, status, overdueOnly, sort)
        val items =
            service
                .searchOrganizationAssignments(organizationId, criteria, currentUser, page * size, size)
                .map { it.toResponse() }
        val total = service.countOrganizationAssignments(organizationId, criteria, currentUser)
        return PageResponse(items, page, size, total)
    }

    @GetMapping("/fee-assignments/search/export", produces = ["text/csv"])
    fun exportOrganizationAssignments(
        @PathVariable organizationId: UUID,
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) status: FeeAssignmentStatus?,
        @RequestParam(defaultValue = "false") overdueOnly: Boolean,
        @RequestParam(defaultValue = "DUE_DATE_ASC") sort: FeeAssignmentSearchSort,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<String> {
        val csv =
            service.exportOrganizationAssignmentsCsv(
                organizationId,
                FeeAssignmentSearchCriteria(q, status, overdueOnly, sort),
                currentUser,
            )
        return ResponseEntity
            .ok()
            .contentType(MediaType.parseMediaType("text/csv"))
            .header("Content-Disposition", "attachment; filename=\"collections-$organizationId.csv\"")
            .body(csv)
    }

    private fun validatePage(
        page: Int,
        size: Int,
    ) {
        if (page < 0 || size !in 1..100) {
            throw ValidationException("Page must be zero or greater and size must be between 1 and 100.")
        }
    }
}
