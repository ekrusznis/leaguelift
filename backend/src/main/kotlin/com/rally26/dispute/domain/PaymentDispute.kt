package com.rally26.dispute.domain

import java.time.Instant
import java.util.UUID

/** Only the source types a Stripe payment_intent can actually belong to — matches the DB check constraint. TRANSFER/REFUND/CORRECTION never carry a payment_intent, so they can never be disputed directly. */
enum class DisputeSourceType { CONTRIBUTION, ORDER, SPONSORSHIP, FEE_PAYMENT }

enum class DisputeStatus { NEEDS_RESPONSE, UNDER_REVIEW, WON, LOST }

/**
 * A Stripe dispute/chargeback (DESIGN-DOC.md §14.6 item #4). Always against Rally26's
 * own Stripe account/charge (ADR-005's merchant-of-record model), routed back to an
 * organization via [sourceType]/[sourceId] — the same shape `LedgerEntry` already uses.
 * [evidenceDueBy] is stored purely for visibility; evidence submission itself happens
 * manually in the Stripe Dashboard, not in this app.
 */
data class PaymentDispute(
    val id: UUID,
    val organizationId: UUID,
    val sourceType: DisputeSourceType,
    val sourceId: UUID,
    val stripeDisputeId: String,
    val stripeChargeId: String,
    val amountMinor: Long,
    val currency: String,
    val reason: String,
    val status: DisputeStatus,
    val evidenceDueBy: Instant?,
    val openedAt: Instant,
    val resolvedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
)
