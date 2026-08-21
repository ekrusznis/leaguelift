package com.rally26.fee.domain

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

enum class PaymentMethod { CASH, CHECK, VENMO, ZELLE, OTHER, STRIPE_ONLINE }

/** [PENDING_CHECKOUT] exists only between Stripe Checkout session creation and webhook confirmation — excluded from balance calculations ([com.rally26.fee.persistence.FeePaymentRepository.sumActiveByAssignment]) so an unpaid attempt never affects the household's outstanding balance. */
enum class FeePaymentStatus { PENDING_CHECKOUT, CONFIRMED, CANCELED }

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
    val status: FeePaymentStatus = FeePaymentStatus.CONFIRMED,
    val stripeCheckoutSessionId: String? = null,
    val stripePaymentIntentId: String? = null,
    val payerEmail: String? = null,
    val payerName: String? = null,
    /** Set only when this payment was actually refunded through Stripe (see FeeService.refundPayment) — distinct from a plain/force void, which never calls Stripe. */
    val stripeRefundId: String? = null,
) {
    val isVoided: Boolean get() = voidedAt != null
}
