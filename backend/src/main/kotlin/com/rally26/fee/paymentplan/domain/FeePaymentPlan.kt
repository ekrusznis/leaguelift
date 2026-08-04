package com.rally26.fee.paymentplan.domain

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

enum class FeePaymentPlanStatus { ACTIVE, COMPLETED, CANCELLED }
enum class FeeInstallmentStatus { UPCOMING, DUE, OVERDUE, PARTIALLY_PAID, PAID, CANCELLED }

data class FeePaymentPlan(
    val id: UUID,
    val organizationId: UUID,
    val feeAssignmentId: UUID,
    val householdId: UUID,
    val status: FeePaymentPlanStatus,
    val totalMinor: Long,
    val currency: String,
    val note: String?,
    val createdByUserId: UUID,
    val cancelledByUserId: UUID?,
    val cancelledAt: Instant?,
    val cancelReason: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class FeeInstallment(
    val id: UUID,
    val organizationId: UUID,
    val paymentPlanId: UUID,
    val sequenceNumber: Int,
    val amountMinor: Long,
    val dueDate: LocalDate,
    val paidMinor: Long,
    val status: FeeInstallmentStatus,
    val createdAt: Instant,
)

data class FeePaymentPlanDetails(
    val plan: FeePaymentPlan,
    val installments: List<FeeInstallment>,
    val paidMinor: Long,
    val remainingMinor: Long,
)

data class NewInstallment(val amountMinor: Long, val dueDate: LocalDate)
