package com.rally26.fee.domain

/**
 * Pure balance/status computation — no repository dependency, directly unit-testable.
 * Clamped at zero: an over-payment/over-credit doesn't produce a negative "outstanding"
 * balance (DESIGN-DOC.md doesn't yet define a credit-balance/refund workflow for that
 * case — out of scope this slice).
 */
data class FeeBalance(val paidMinor: Long, val adjustedMinor: Long, val balanceMinor: Long)

data class FeeAssignmentWithBalance(val assignment: FeeAssignment, val balance: FeeBalance)

fun computeFeeBalance(originalAmountMinor: Long, paidMinor: Long, adjustedMinor: Long): FeeBalance {
	val balance = (originalAmountMinor - paidMinor - adjustedMinor).coerceAtLeast(0)
	return FeeBalance(paidMinor, adjustedMinor, balance)
}

/**
 * WAIVED/CANCELLED are terminal for this slice's purposes — recording a payment or
 * adjustment against either is blocked by the service layer, so this never needs to
 * move a fee *out* of those statuses.
 */
fun resolveStatusAfterBalanceChange(
	currentStatus: FeeAssignmentStatus,
	balance: FeeBalance,
	hasAnyActivePaymentOrAdjustment: Boolean,
): FeeAssignmentStatus {
	if (currentStatus == FeeAssignmentStatus.WAIVED || currentStatus == FeeAssignmentStatus.CANCELLED) return currentStatus
	return when {
		balance.balanceMinor <= 0 -> FeeAssignmentStatus.PAID
		hasAnyActivePaymentOrAdjustment -> FeeAssignmentStatus.PARTIALLY_PAID
		else -> FeeAssignmentStatus.OPEN
	}
}
