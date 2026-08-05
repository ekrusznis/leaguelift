package com.rally26.fee.paymentplan.web

import com.rally26.fee.paymentplan.domain.FeePaymentPlanDetails
import com.rally26.fee.paymentplan.domain.NewInstallment
import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class CreateFeePaymentPlanRequest(
    @field:Valid @field:Size(min = 2, max = 24) val installments: List<InstallmentRequest>,
    @field:Size(max = 1000) val note: String? = null,
)

data class InstallmentRequest(
    @field:NotNull @field:Min(1) val amountMinor: Long,
    @field:NotNull val dueDate: LocalDate,
)

data class CancelFeePaymentPlanRequest(
    @field:NotBlank @field:Size(max = 500) val reason: String,
)

data class FeePaymentPlanResponse(
    val id: UUID,
    val organizationId: UUID,
    val feeAssignmentId: UUID,
    val householdId: UUID,
    val status: String,
    val totalMinor: Long,
    val paidMinor: Long,
    val remainingMinor: Long,
    val currency: String,
    val note: String?,
    val cancelReason: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val installments: List<FeeInstallmentResponse>,
)

data class FeeInstallmentResponse(
    val id: UUID,
    val sequenceNumber: Int,
    val amountMinor: Long,
    val paidMinor: Long,
    val remainingMinor: Long,
    val dueDate: LocalDate,
    val status: String,
)

fun CreateFeePaymentPlanRequest.toDomain(): List<NewInstallment> = installments.map { NewInstallment(it.amountMinor, it.dueDate) }

fun FeePaymentPlanDetails.toResponse() =
    FeePaymentPlanResponse(
        id = plan.id,
        organizationId = plan.organizationId,
        feeAssignmentId = plan.feeAssignmentId,
        householdId = plan.householdId,
        status = plan.status.name,
        totalMinor = plan.totalMinor,
        paidMinor = paidMinor,
        remainingMinor = remainingMinor,
        currency = plan.currency,
        note = plan.note,
        cancelReason = plan.cancelReason,
        createdAt = plan.createdAt,
        updatedAt = plan.updatedAt,
        installments =
            installments.map {
                FeeInstallmentResponse(
                    it.id,
                    it.sequenceNumber,
                    it.amountMinor,
                    it.paidMinor,
                    (it.amountMinor - it.paidMinor).coerceAtLeast(0),
                    it.dueDate,
                    it.status.name,
                )
            },
    )
