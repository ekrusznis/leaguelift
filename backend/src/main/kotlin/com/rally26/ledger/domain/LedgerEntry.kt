package com.rally26.ledger.domain

import java.time.Instant
import java.util.UUID

/**
 * The subset of DESIGN-DOC.md section 8.6's full entry-type list this slice
 * actually computes a rule for. SALES_TAX, SHIPPING_COLLECTED,
 * FULFILLMENT_SHIPPING_COST, TEAM_ALLOCATION, HOUSEHOLD_CREDIT,
 * PAYOUT, and MANUAL_ADJUSTMENT remain design target.
 */
enum class LedgerEntryType {
    CONTRIBUTION,
    GROSS_SALE,
    PRODUCTION_COST,
    RALLY26_PLATFORM_FEE,
    ORGANIZATION_EARNING,
    TRANSFER,
    REFUND,
    OFFLINE_SETTLEMENT,
    MANUAL_ADJUSTMENT,
    /** A Stripe dispute/chargeback (2026-08-12) — reverses the gross amount, distinct from REFUND for accounting/reporting purposes even though the ORGANIZATION_EARNING reversal it produces is identical in shape. */
    CHARGEBACK,
    /** Stripe's own non-refundable per-dispute fee. Rally26 absorbs this (founder decision, 2026-08-12) — see LedgerService.recordDisputeOpened, which deliberately never pairs this with an ORGANIZATION_EARNING debit. */
    CHARGEBACK_FEE,
    /** Stripe's real per-charge processing fee (~2.9%+30¢), fetched from Stripe's own Balance Transaction API. Rally26 absorbs this (founder decision, 2026-08-13, same reasoning as CHARGEBACK_FEE) — internal margin-visibility only, see LedgerService.recordStripeProcessingFee, which deliberately never pairs this with an ORGANIZATION_EARNING debit. */
    STRIPE_PROCESSING_FEE,
}

enum class LedgerDirection { CREDIT, DEBIT }

enum class LedgerSourceType { CONTRIBUTION, ORDER, TRANSFER, REFUND, SPONSORSHIP, CORRECTION, FEE_PAYMENT, DISPUTE }

/**
 * Append-only (DESIGN-DOC.md section 8.6) — corrections are always a new
 * reversing row, never an edit of an existing one. [includedInTransferEntryId]
 * is the payout-eligibility "already paid out" marker, set only on
 * ORGANIZATION_EARNING credit/reversal-debit rows once a transfer has
 * consumed them (LedgerService.recordTransfer).
 */
data class LedgerEntry(
    val id: UUID,
    val organizationId: UUID,
    val accountCode: String,
    val entryType: LedgerEntryType,
    val direction: LedgerDirection,
    val amountMinor: Long,
    val currency: String,
    val sourceType: LedgerSourceType,
    val sourceId: UUID,
    val externalReference: String?,
    val description: String?,
    val includedInTransferEntryId: UUID?,
    val effectiveAt: Instant,
    val createdAt: Instant,
)

/** Credits increase what an org is owed; debits decrease it. Used to net a set of entries into a single signed total. */
fun LedgerEntry.signedAmountMinor(): Long = if (direction == LedgerDirection.CREDIT) amountMinor else -amountMinor
