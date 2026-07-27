package com.leaguelift.fee.web

import com.leaguelift.common.web.CurrentUser
import com.leaguelift.common.web.PageResponse
import com.leaguelift.fee.application.FeeService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}")
class FeeAssignmentController(private val feeService: FeeService) {

    @GetMapping("/households/{householdId}/fee-assignments")
    fun list(
        @PathVariable organizationId: UUID,
        @PathVariable householdId: UUID,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): PageResponse<FeeAssignmentResponse> {
        val offset = page * size
        val items = feeService.listForHousehold(organizationId, householdId, currentUser, offset, size).map { it.toResponse() }
        val total = feeService.countForHousehold(organizationId, householdId, currentUser)
        return PageResponse(items, page, size, total)
    }

    @PostMapping("/households/{householdId}/fee-assignments")
    fun create(
        @PathVariable organizationId: UUID,
        @PathVariable householdId: UUID,
        @Valid @RequestBody request: CreateFeeAssignmentRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<FeeAssignmentResponse> {
        val assignment = feeService.createAssignment(
            organizationId, householdId, request.participantId, request.feeTemplateId,
            request.description, request.originalAmountMinor, request.currency, request.dueDate, currentUser,
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(assignment.toResponse())
    }

    @GetMapping("/fee-assignments/{assignmentId}")
    fun get(
        @PathVariable organizationId: UUID,
        @PathVariable assignmentId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): FeeAssignmentResponse = feeService.getAssignment(organizationId, assignmentId, currentUser).toResponse()

    @PatchMapping("/fee-assignments/{assignmentId}/status")
    fun updateStatus(
        @PathVariable organizationId: UUID,
        @PathVariable assignmentId: UUID,
        @Valid @RequestBody request: UpdateFeeAssignmentStatusRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): FeeAssignmentResponse = feeService.updateAssignmentStatus(organizationId, assignmentId, request.status, currentUser).toResponse()
}
