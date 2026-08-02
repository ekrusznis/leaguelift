package com.leaguelift.financialcorrection.domain

import java.time.Instant
import java.util.UUID

enum class FinancialCorrectionType { REFUND, REVERSAL }
enum class FinancialCorrectionTargetType { CONTRIBUTION, SPONSORSHIP, ORDER, OFFLINE_FINANCIAL_RECORD }

data class FinancialCorrection(
    val id: UUID,
    val organizationId: UUID,
    val correctionType: FinancialCorrectionType,
    val targetType: FinancialCorrectionTargetType,
    val targetId: UUID,
    val amountMinor: Long,
    val currency: String,
    val reason: String,
    val providerReference: String?,
    val confirmationHash: String,
    val idempotencyKey: String,
    val createdByUserId: UUID,
    val createdAt: Instant,
)

data class FinancialCorrectionPreview(
    val correctionType: FinancialCorrectionType,
    val targetType: FinancialCorrectionTargetType,
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
