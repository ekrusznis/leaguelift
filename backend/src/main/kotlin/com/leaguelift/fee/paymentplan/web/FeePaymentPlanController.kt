package com.leaguelift.fee.paymentplan.web

import com.leaguelift.common.web.CurrentUser
import com.leaguelift.fee.paymentplan.application.FeePaymentPlanService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/fee-assignments/{assignmentId}/payment-plan")
class FeePaymentPlanController(private val service: FeePaymentPlanService) {
    @GetMapping
    fun get(
        @PathVariable organizationId: UUID,
        @PathVariable assignmentId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<FeePaymentPlanResponse> {
        val plan = service.get(organizationId, assignmentId, currentUser) ?: return ResponseEntity.noContent().build()
        return ResponseEntity.ok(plan.toResponse())
    }

    @PostMapping
    fun create(
        @PathVariable organizationId: UUID,
        @PathVariable assignmentId: UUID,
        @Valid @RequestBody request: CreateFeePaymentPlanRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<FeePaymentPlanResponse> = ResponseEntity.status(HttpStatus.CREATED)
        .body(service.create(organizationId, assignmentId, request.toDomain(), request.note, currentUser).toResponse())

    @DeleteMapping
    fun cancel(
        @PathVariable organizationId: UUID,
        @PathVariable assignmentId: UUID,
        @Valid @RequestBody request: CancelFeePaymentPlanRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): FeePaymentPlanResponse = service.cancel(organizationId, assignmentId, request.reason, currentUser).toResponse()
}
