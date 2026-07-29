package com.leaguelift.fee.domain

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

enum class PaymentMethod { CASH, CHECK, VENMO, ZELLE, OTHER }

data class FeePayment(
	val id: UUID,
	val organizationId: UUID,
	val feeAssignmentId: UUID,
	val householdId: UUID,
	val amountMinor: Long,
	val currency: String,
	val method: PaymentMethod,
	val paidAt: LocalDate,
	val note: String?,
	val recordedByUserId: UUID,
	val voidedAt: Instant?,
	val voidedByUserId: UUID?,
	val voidReason: String?,
	val createdAt: Instant,
) {
	val isVoided: Boolean get() = voidedAt != null
}
