package com.rally26.fee.web

import com.rally26.common.web.CurrentUser
import com.rally26.common.web.PageResponse
import com.rally26.fee.application.FeeService
import com.rally26.fee.domain.FeeAssignmentStatus
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
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
class FeeAssignmentController(
    private val feeService: FeeService,
) {
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
        val assignment =
            feeService.createAssignment(
                organizationId,
                householdId,
                request.participantId,
                request.feeTemplateId,
                request.description,
                request.originalAmountMinor,
                request.currency,
                request.dueDate,
                currentUser,
            )
        return ResponseEntity.status(HttpStatus.CREATED).body(assignment.toResponse())
    }

    @GetMapping("/fee-assignments")
    fun listForOrganization(
        @PathVariable organizationId: UUID,
        @RequestParam(required = false) status: FeeAssignmentStatus?,
        @RequestParam(defaultValue = "false") overdueOnly: Boolean,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): PageResponse<FeeAssignmentSummaryResponse> {
        val offset = page * size
        val items = feeService.listForOrganization(organizationId, status, overdueOnly, currentUser, offset, size).map { it.toResponse() }
        val total = feeService.countForOrganization(organizationId, status, overdueOnly, currentUser)
        return PageResponse(items, page, size, total)
    }

    @GetMapping("/fee-assignments/export", produces = ["text/csv"])
    fun exportCollections(
        @PathVariable organizationId: UUID,
        @RequestParam(required = false) status: FeeAssignmentStatus?,
        @RequestParam(defaultValue = "false") overdueOnly: Boolean,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<String> {
        val csv = feeService.exportCollectionsCsv(organizationId, status, overdueOnly, currentUser)
        return ResponseEntity
            .ok()
            .contentType(MediaType.parseMediaType("text/csv"))
            .header("Content-Disposition", "attachment; filename=\"collections-$organizationId.csv\"")
            .body(csv)
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

    // --- Payments ---

    @PostMapping("/fee-assignments/{assignmentId}/payments")
    fun recordPayment(
        @PathVariable organizationId: UUID,
        @PathVariable assignmentId: UUID,
        @Valid @RequestBody request: RecordPaymentRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<FeeAssignmentResponse> {
        val result =
            feeService.recordPayment(
                organizationId,
                assignmentId,
                request.amountMinor,
                request.method,
                request.paidAt,
                request.note,
                currentUser,
            )
        return ResponseEntity.status(HttpStatus.CREATED).body(result.toResponse())
    }

    @GetMapping("/fee-assignments/{assignmentId}/payments")
    fun listPayments(
        @PathVariable organizationId: UUID,
        @PathVariable assignmentId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): List<FeePaymentResponse> = feeService.listPayments(organizationId, assignmentId, currentUser).map { it.toResponse() }

    @DeleteMapping("/fee-assignments/{assignmentId}/payments/{paymentId}")
    fun voidPayment(
        @PathVariable organizationId: UUID,
        @PathVariable assignmentId: UUID,
        @PathVariable paymentId: UUID,
        @Valid @RequestBody request: VoidRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): FeeAssignmentResponse = feeService.voidPayment(organizationId, assignmentId, paymentId, request.reason, currentUser).toResponse()

    // --- Adjustments ---

    @PostMapping("/fee-assignments/{assignmentId}/adjustments")
    fun applyAdjustment(
        @PathVariable organizationId: UUID,
        @PathVariable assignmentId: UUID,
        @Valid @RequestBody request: ApplyAdjustmentRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<FeeAssignmentResponse> {
        val result =
            feeService.applyAdjustment(
                organizationId,
                assignmentId,
                request.adjustmentType,
                request.amountMinor,
                request.reason,
                currentUser,
            )
        return ResponseEntity.status(HttpStatus.CREATED).body(result.toResponse())
    }

    @GetMapping("/fee-assignments/{assignmentId}/adjustments")
    fun listAdjustments(
        @PathVariable organizationId: UUID,
        @PathVariable assignmentId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): List<FeeAdjustmentResponse> = feeService.listAdjustments(organizationId, assignmentId, currentUser).map { it.toResponse() }

    @DeleteMapping("/fee-assignments/{assignmentId}/adjustments/{adjustmentId}")
    fun voidAdjustment(
        @PathVariable organizationId: UUID,
        @PathVariable assignmentId: UUID,
        @PathVariable adjustmentId: UUID,
        @Valid @RequestBody request: VoidRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): FeeAssignmentResponse =
        feeService.voidAdjustment(organizationId, assignmentId, adjustmentId, request.reason, currentUser).toResponse()
}
