package com.rally26.fee.web

import com.rally26.fee.application.PaymentMethodAvailability
import com.rally26.fee.domain.AdjustmentType
import com.rally26.fee.domain.FeeAdjustment
import com.rally26.fee.domain.FeeAssignmentStatus
import com.rally26.fee.domain.FeeAssignmentSummary
import com.rally26.fee.domain.FeeAssignmentWithBalance
import com.rally26.fee.domain.FeePayment
import com.rally26.fee.domain.FeeTemplate
import com.rally26.fee.domain.OrganizationFeeFinancialSummary
import com.rally26.fee.domain.PaymentMethod
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class CreateFeeTemplateRequest(
    @field:NotBlank @field:Size(min = 1, max = 120) val name: String,
    @field:Size(max = 500) val description: String? = null,
    @field:NotNull @field:Min(0) val amountMinor: Long,
    @field:Size(min = 3, max = 3) val currency: String = "USD",
)

data class UpdateFeeTemplateRequest(
    @field:Size(min = 1, max = 120) val name: String? = null,
    @field:Size(max = 500) val description: String? = null,
    @field:Min(0) val amountMinor: Long? = null,
)

data class FeeTemplateResponse(
    val id: UUID,
    val organizationId: UUID,
    val name: String,
    val description: String?,
    val amountMinor: Long,
    val currency: String,
    val status: String,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class CreateFeeAssignmentRequest(
    val participantId: UUID? = null,
    val feeTemplateId: UUID? = null,
    @field:NotBlank @field:Size(min = 1, max = 255) val description: String,
    @field:NotNull @field:Min(0) val originalAmountMinor: Long,
    @field:Size(min = 3, max = 3) val currency: String = "USD",
    val dueDate: LocalDate? = null,
)

data class UpdateFeeAssignmentStatusRequest(
    @field:NotNull val status: FeeAssignmentStatus,
)

data class FeeAssignmentResponse(
    val id: UUID,
    val organizationId: UUID,
    val householdId: UUID,
    val participantId: UUID?,
    val feeTemplateId: UUID?,
    val description: String,
    val originalAmountMinor: Long,
    val currency: String,
    val dueDate: LocalDate?,
    val status: String,
    val paidMinor: Long,
    val adjustedMinor: Long,
    val balanceMinor: Long,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class FeeAssignmentSummaryResponse(
    val id: UUID,
    val organizationId: UUID,
    val householdId: UUID,
    val householdName: String,
    val participantId: UUID?,
    val participantName: String?,
    val feeTemplateId: UUID?,
    val description: String,
    val originalAmountMinor: Long,
    val currency: String,
    val dueDate: LocalDate?,
    val status: String,
    val paidMinor: Long,
    val adjustedMinor: Long,
    val balanceMinor: Long,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class FinancialSummaryResponse(
    val feesAssignedMinor: Long,
    val feesCollectedMinor: Long,
    val outstandingMinor: Long,
)

// --- Payments ---

data class RecordPaymentRequest(
    @field:NotNull @field:Min(1) val amountMinor: Long,
    @field:NotNull val method: PaymentMethod,
    @field:NotNull val paidAt: LocalDate,
    @field:Size(max = 500) val note: String? = null,
)

data class VoidRequest(
    @field:NotBlank @field:Size(min = 1, max = 500) val reason: String,
    /** Manual-reconciliation override for a confirmed card payment — see FeeService.voidPayment's doc comment. Ignored (and unnecessary) for non-card payments. */
    val force: Boolean = false,
)

data class RefundRequest(
    @field:NotBlank @field:Size(min = 1, max = 500) val reason: String,
)

data class FeePaymentResponse(
    val id: UUID,
    val feeAssignmentId: UUID,
    val amountMinor: Long,
    val currency: String,
    val method: String,
    val paidAt: LocalDate,
    val note: String?,
    val recordedByUserId: UUID,
    val voidedAt: Instant?,
    val voidReason: String?,
    val createdAt: Instant,
    val status: String,
    val stripeRefundId: String?,
)

// --- Online checkout ---

data class CreateFeeCheckoutSessionRequest(
    @field:NotNull @field:Min(1) val amountMinor: Long,
    @field:NotBlank val successUrl: String,
    @field:NotBlank val cancelUrl: String,
)

data class FeePaymentCheckoutResponse(
    val feePaymentId: UUID,
    val checkoutUrl: String,
)

data class PaymentMethodAvailabilityResponse(
    val method: String,
    val displayName: String,
    val available: Boolean,
    val note: String?,
)

fun PaymentMethodAvailability.toResponse() = PaymentMethodAvailabilityResponse(method, displayName, available, note)

// --- Adjustments ---

data class ApplyAdjustmentRequest(
    @field:NotNull val adjustmentType: AdjustmentType,
    @field:NotNull @field:Min(1) val amountMinor: Long,
    @field:Size(max = 500) val reason: String? = null,
)

data class FeeAdjustmentResponse(
    val id: UUID,
    val feeAssignmentId: UUID,
    val adjustmentType: String,
    val amountMinor: Long,
    val currency: String,
    val reason: String?,
    val createdByUserId: UUID,
    val voidedAt: Instant?,
    val voidReason: String?,
    val createdAt: Instant,
)

// --- Mappers ---

fun FeeTemplate.toResponse() =
    FeeTemplateResponse(
        id,
        organizationId,
        name,
        description,
        amountMinor,
        currency,
        status.name,
        createdAt,
        updatedAt,
    )

fun FeeAssignmentWithBalance.toResponse() =
    FeeAssignmentResponse(
        id = assignment.id,
        organizationId = assignment.organizationId,
        householdId = assignment.householdId,
        participantId = assignment.participantId,
        feeTemplateId = assignment.feeTemplateId,
        description = assignment.description,
        originalAmountMinor = assignment.originalAmountMinor,
        currency = assignment.currency,
        dueDate = assignment.dueDate,
        status = assignment.status.name,
        paidMinor = balance.paidMinor,
        adjustedMinor = balance.adjustedMinor,
        balanceMinor = balance.balanceMinor,
        createdAt = assignment.createdAt,
        updatedAt = assignment.updatedAt,
    )

fun FeeAssignmentSummary.toResponse() =
    FeeAssignmentSummaryResponse(
        id = id,
        organizationId = organizationId,
        householdId = householdId,
        householdName = householdName,
        participantId = participantId,
        participantName = participantName,
        feeTemplateId = feeTemplateId,
        description = description,
        originalAmountMinor = originalAmountMinor,
        currency = currency,
        dueDate = dueDate,
        status = status.name,
        paidMinor = balance.paidMinor,
        adjustedMinor = balance.adjustedMinor,
        balanceMinor = balance.balanceMinor,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

fun OrganizationFeeFinancialSummary.toResponse() = FinancialSummaryResponse(feesAssignedMinor, feesCollectedMinor, outstandingMinor)

fun FeePayment.toResponse() =
    FeePaymentResponse(
        id,
        feeAssignmentId,
        amountMinor,
        currency,
        method.name,
        paidAt,
        note,
        recordedByUserId,
        voidedAt,
        voidReason,
        createdAt,
        status.name,
        stripeRefundId,
    )

fun FeeAdjustment.toResponse() =
    FeeAdjustmentResponse(
        id,
        feeAssignmentId,
        adjustmentType.name,
        amountMinor,
        currency,
        reason,
        createdByUserId,
        voidedAt,
        voidReason,
        createdAt,
    )
