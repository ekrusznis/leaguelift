package com.rally26.financialcorrection.web

import com.rally26.financialcorrection.domain.FinancialCorrection
import com.rally26.financialcorrection.domain.FinancialCorrectionPreview
import com.rally26.financialcorrection.domain.FinancialCorrectionTargetType
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class PreviewFinancialCorrectionRequest(
    @field:NotNull val targetType: FinancialCorrectionTargetType,
    @field:NotNull val targetId: UUID,
    @field:Min(1) val amountMinor: Long? = null,
    @field:NotBlank @field:Size(min = 3, max = 1000) val reason: String,
)

data class ExecuteFinancialCorrectionRequest(
    @field:NotNull val targetType: FinancialCorrectionTargetType,
    @field:NotNull val targetId: UUID,
    @field:Min(1) val amountMinor: Long? = null,
    @field:NotBlank @field:Size(min = 3, max = 1000) val reason: String,
    @field:NotBlank @field:Size(min = 64, max = 64) val confirmationHash: String,
    @field:NotBlank @field:Size(min = 8, max = 120) val idempotencyKey: String,
)

data class FinancialCorrectionPreviewResponse(
    val correctionType: String,
    val targetType: String,
    val targetId: UUID,
    val targetLabel: String,
    val paymentSource: String,
    val originalAmountMinor: Long,
    val previouslyCorrectedMinor: Long,
    val requestedAmountMinor: Long,
    val remainingAfterMinor: Long,
    val currency: String,
    val willFullyCorrect: Boolean,
    val warnings: List<String>,
    val confirmationHash: String,
)

data class FinancialCorrectionResponse(
    val id: UUID,
    val organizationId: UUID,
    val correctionType: String,
    val targetType: String,
    val targetId: UUID,
    val amountMinor: Long,
    val currency: String,
    val reason: String,
    val providerReference: String?,
    val createdByUserId: UUID,
    val createdAt: Instant,
)

fun FinancialCorrectionPreview.toResponse() =
    FinancialCorrectionPreviewResponse(
        correctionType.name,
        targetType.name,
        targetId,
        targetLabel,
        paymentSource,
        originalAmountMinor,
        previouslyCorrectedMinor,
        requestedAmountMinor,
        remainingAfterMinor,
        currency,
        willFullyCorrect,
        warnings,
        confirmationHash,
    )

fun FinancialCorrection.toResponse() =
    FinancialCorrectionResponse(
        id,
        organizationId,
        correctionType.name,
        targetType.name,
        targetId,
        amountMinor,
        currency,
        reason,
        providerReference,
        createdByUserId,
        createdAt,
    )
