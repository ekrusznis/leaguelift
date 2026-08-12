package com.rally26.ledger.application

import com.rally26.config.PayoutProperties
import com.rally26.config.PlatformFeeProperties
import com.rally26.fundraising.domain.Contribution
import com.rally26.ledger.domain.LedgerDirection
import com.rally26.ledger.domain.LedgerEntry
import com.rally26.ledger.domain.LedgerEntryType
import com.rally26.ledger.domain.LedgerSourceType
import com.rally26.ledger.persistence.LedgerEntryRepository
import com.rally26.order.domain.Order
import com.rally26.order.domain.OrderItem
import com.rally26.sponsorship.domain.Sponsorship
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.util.UUID

/** `{ eligibleMinor, heldMinor, pendingDebitsMinor, netAvailableMinor }` — netAvailableMinor can be negative, shown honestly rather than hidden (ADR-017 negative-balance decision). */
data class PayoutSummary(
    val eligibleMinor: Long,
    val heldMinor: Long,
    val pendingDebitsMinor: Long,
    val netAvailableMinor: Long,
)

/**
 * Application-layer orchestration over DESIGN-DOC.md section 8.6's ledger: computes
 * and records the CONTRIBUTION/GROSS_SALE/PRODUCTION_COST/RALLY26_PLATFORM_FEE/
 * ORGANIZATION_EARNING entries at confirmation time, and TRANSFER/REFUND entries when
 * money later moves. Every write here is a new append-only row (LedgerEntryRepository
 * has no update-in-place besides the included-in-transfer marker) — corrections are
 * always a new reversing entry, never an edit.
 */
@Service
class LedgerService(
    private val ledgerEntryRepository: LedgerEntryRepository,
    private val platformFeeProperties: PlatformFeeProperties,
    private val payoutProperties: PayoutProperties,
) {
    @Transactional
    fun recordConfirmedContribution(contribution: Contribution) {
        val gross = contribution.amountMinor
        val fee = platformFeeProperties.feeMinorOf(gross)
        val net = gross - fee
        ledgerEntryRepository.insert(
            organizationId = contribution.organizationId,
            accountCode = LedgerEntryType.CONTRIBUTION.name,
            entryType = LedgerEntryType.CONTRIBUTION,
            direction = LedgerDirection.CREDIT,
            amountMinor = gross,
            currency = contribution.currency,
            sourceType = LedgerSourceType.CONTRIBUTION,
            sourceId = contribution.id,
            externalReference = null,
            description = "Confirmed campaign contribution",
        )
        ledgerEntryRepository.insert(
            organizationId = contribution.organizationId,
            accountCode = LedgerEntryType.RALLY26_PLATFORM_FEE.name,
            entryType = LedgerEntryType.RALLY26_PLATFORM_FEE,
            direction = LedgerDirection.DEBIT,
            amountMinor = fee,
            currency = contribution.currency,
            sourceType = LedgerSourceType.CONTRIBUTION,
            sourceId = contribution.id,
            externalReference = null,
            description = "Platform fee (${platformFeeProperties.feeBasisPoints} bps)",
        )
        ledgerEntryRepository.insert(
            organizationId = contribution.organizationId,
            accountCode = LedgerEntryType.ORGANIZATION_EARNING.name,
            entryType = LedgerEntryType.ORGANIZATION_EARNING,
            direction = LedgerDirection.CREDIT,
            amountMinor = net,
            currency = contribution.currency,
            sourceType = LedgerSourceType.CONTRIBUTION,
            sourceId = contribution.id,
            externalReference = null,
            description = "Organization earning from confirmed contribution",
        )
    }

    /**
     * Structurally identical to [recordConfirmedContribution] — a sponsorship purchase is,
     * accounting-wise, the same shape as a campaign contribution (a single gross payment,
     * no separate production cost the way an order has). Reuses `LedgerEntryType.CONTRIBUTION`
     * rather than introducing a distinct `SPONSORSHIP` entry type this slice (ADR-018):
     * `LedgerSourceType.SPONSORSHIP` on [Sponsorship]'s own `sourceType` already gives full
     * traceability back to the sponsorship without proliferating entry types for a category
     * that behaves identically to an existing one.
     */
    @Transactional
    fun recordConfirmedSponsorship(sponsorship: Sponsorship) {
        val gross = sponsorship.amountMinor
        val fee = platformFeeProperties.feeMinorOf(gross)
        val net = gross - fee
        ledgerEntryRepository.insert(
            organizationId = sponsorship.organizationId,
            accountCode = LedgerEntryType.CONTRIBUTION.name,
            entryType = LedgerEntryType.CONTRIBUTION,
            direction = LedgerDirection.CREDIT,
            amountMinor = gross,
            currency = sponsorship.currency,
            sourceType = LedgerSourceType.SPONSORSHIP,
            sourceId = sponsorship.id,
            externalReference = null,
            description = "Confirmed sponsorship purchase",
        )
        ledgerEntryRepository.insert(
            organizationId = sponsorship.organizationId,
            accountCode = LedgerEntryType.RALLY26_PLATFORM_FEE.name,
            entryType = LedgerEntryType.RALLY26_PLATFORM_FEE,
            direction = LedgerDirection.DEBIT,
            amountMinor = fee,
            currency = sponsorship.currency,
            sourceType = LedgerSourceType.SPONSORSHIP,
            sourceId = sponsorship.id,
            externalReference = null,
            description = "Platform fee (${platformFeeProperties.feeBasisPoints} bps)",
        )
        ledgerEntryRepository.insert(
            organizationId = sponsorship.organizationId,
            accountCode = LedgerEntryType.ORGANIZATION_EARNING.name,
            entryType = LedgerEntryType.ORGANIZATION_EARNING,
            direction = LedgerDirection.CREDIT,
            amountMinor = net,
            currency = sponsorship.currency,
            sourceType = LedgerSourceType.SPONSORSHIP,
            sourceId = sponsorship.id,
            externalReference = null,
            description = "Organization earning from confirmed sponsorship",
        )
    }

    /**
     * Structurally identical to [recordConfirmedContribution] — an online fee
     * payment takes Rally26's standard platform-fee cut like every other
     * confirmed-payment method, per founder direction (2026-08-12). Reuses
     * `LedgerEntryType.CONTRIBUTION` rather than introducing a distinct
     * `FEE_PAYMENT` entry type, same reuse precedent as [recordConfirmedSponsorship]
     * — `LedgerSourceType.FEE_PAYMENT` on the source fields already gives full
     * traceability back to the `fee_payment` row.
     */
    @Transactional
    fun recordConfirmedFeePayment(
        organizationId: UUID,
        feePaymentId: UUID,
        amountMinor: Long,
        currency: String,
    ) {
        val gross = amountMinor
        val fee = platformFeeProperties.feeMinorOf(gross)
        val net = gross - fee
        ledgerEntryRepository.insert(
            organizationId = organizationId,
            accountCode = LedgerEntryType.CONTRIBUTION.name,
            entryType = LedgerEntryType.CONTRIBUTION,
            direction = LedgerDirection.CREDIT,
            amountMinor = gross,
            currency = currency,
            sourceType = LedgerSourceType.FEE_PAYMENT,
            sourceId = feePaymentId,
            externalReference = null,
            description = "Confirmed online fee payment",
        )
        ledgerEntryRepository.insert(
            organizationId = organizationId,
            accountCode = LedgerEntryType.RALLY26_PLATFORM_FEE.name,
            entryType = LedgerEntryType.RALLY26_PLATFORM_FEE,
            direction = LedgerDirection.DEBIT,
            amountMinor = fee,
            currency = currency,
            sourceType = LedgerSourceType.FEE_PAYMENT,
            sourceId = feePaymentId,
            externalReference = null,
            description = "Platform fee (${platformFeeProperties.feeBasisPoints} bps)",
        )
        ledgerEntryRepository.insert(
            organizationId = organizationId,
            accountCode = LedgerEntryType.ORGANIZATION_EARNING.name,
            entryType = LedgerEntryType.ORGANIZATION_EARNING,
            direction = LedgerDirection.CREDIT,
            amountMinor = net,
            currency = currency,
            sourceType = LedgerSourceType.FEE_PAYMENT,
            sourceId = feePaymentId,
            externalReference = null,
            description = "Organization earning from confirmed online fee payment",
        )
    }

    @Transactional
    fun recordConfirmedOrder(
        order: Order,
        orderItems: List<OrderItem>,
    ) {
        val gross = orderItems.sumOf { it.unitPriceMinor * it.quantity }
        val cost = orderItems.sumOf { it.unitCostMinor * it.quantity }
        val fee = platformFeeProperties.feeMinorOf(gross)
        val net = gross - cost - fee
        ledgerEntryRepository.insert(
            organizationId = order.organizationId,
            accountCode = LedgerEntryType.GROSS_SALE.name,
            entryType = LedgerEntryType.GROSS_SALE,
            direction = LedgerDirection.CREDIT,
            amountMinor = gross,
            currency = order.currency,
            sourceType = LedgerSourceType.ORDER,
            sourceId = order.id,
            externalReference = null,
            description = "Confirmed store order",
        )
        ledgerEntryRepository.insert(
            organizationId = order.organizationId,
            accountCode = LedgerEntryType.PRODUCTION_COST.name,
            entryType = LedgerEntryType.PRODUCTION_COST,
            direction = LedgerDirection.DEBIT,
            amountMinor = cost,
            currency = order.currency,
            sourceType = LedgerSourceType.ORDER,
            sourceId = order.id,
            externalReference = null,
            description = "Printify production cost (snapshotted at order time)",
        )
        ledgerEntryRepository.insert(
            organizationId = order.organizationId,
            accountCode = LedgerEntryType.RALLY26_PLATFORM_FEE.name,
            entryType = LedgerEntryType.RALLY26_PLATFORM_FEE,
            direction = LedgerDirection.DEBIT,
            amountMinor = fee,
            currency = order.currency,
            sourceType = LedgerSourceType.ORDER,
            sourceId = order.id,
            externalReference = null,
            description = "Platform fee (${platformFeeProperties.feeBasisPoints} bps)",
        )
        // A below-cost order (production cost + fee exceeding gross) produces a negative
        // organization earning — recorded honestly as a DEBIT rather than a negative-amount
        // CREDIT (amount_minor is check-constrained >= 0), which also means it's picked up
        // immediately by findPendingOrganizationEarningDebits with no holding period.
        ledgerEntryRepository.insert(
            organizationId = order.organizationId,
            accountCode = LedgerEntryType.ORGANIZATION_EARNING.name,
            entryType = LedgerEntryType.ORGANIZATION_EARNING,
            direction = if (net >= 0) LedgerDirection.CREDIT else LedgerDirection.DEBIT,
            amountMinor = kotlin.math.abs(net),
            currency = order.currency,
            sourceType = LedgerSourceType.ORDER,
            sourceId = order.id,
            externalReference = null,
            description = "Organization earning from confirmed order",
        )
    }

    /**
     * Records money the organization received outside Rally26. The matching
     * OFFLINE_SETTLEMENT debit documents that the funds were already settled
     * externally. No ORGANIZATION_EARNING row is created, so this record can never
     * be included in a Rally26 payout transfer.
     */
    @Transactional
    fun recordOfflineContribution(
        contribution: Contribution,
        paymentReference: String?,
    ) {
        recordOfflineReceipt(
            organizationId = contribution.organizationId,
            entryType = LedgerEntryType.CONTRIBUTION,
            accountCode = LedgerEntryType.CONTRIBUTION.name,
            amountMinor = contribution.amountMinor,
            currency = contribution.currency,
            sourceType = LedgerSourceType.CONTRIBUTION,
            sourceId = contribution.id,
            paymentReference = paymentReference,
            description = "Offline campaign contribution recorded",
        )
    }

    @Transactional
    fun recordOfflineSponsorship(
        sponsorship: Sponsorship,
        paymentReference: String?,
    ) {
        recordOfflineReceipt(
            organizationId = sponsorship.organizationId,
            entryType = LedgerEntryType.CONTRIBUTION,
            accountCode = LedgerEntryType.CONTRIBUTION.name,
            amountMinor = sponsorship.amountMinor,
            currency = sponsorship.currency,
            sourceType = LedgerSourceType.SPONSORSHIP,
            sourceId = sponsorship.id,
            paymentReference = paymentReference,
            description = "Offline sponsorship purchase recorded",
        )
    }

    @Transactional
    fun recordOfflineOrder(
        order: Order,
        orderItems: List<OrderItem>,
        paymentReference: String?,
    ) {
        val gross = orderItems.sumOf { it.unitPriceMinor * it.quantity }
        val cost = orderItems.sumOf { it.unitCostMinor * it.quantity }
        recordOfflineReceipt(
            organizationId = order.organizationId,
            entryType = LedgerEntryType.GROSS_SALE,
            accountCode = LedgerEntryType.GROSS_SALE.name,
            amountMinor = gross,
            currency = order.currency,
            sourceType = LedgerSourceType.ORDER,
            sourceId = order.id,
            paymentReference = paymentReference,
            description = "Offline store order recorded",
        )
        ledgerEntryRepository.insert(
            organizationId = order.organizationId,
            accountCode = LedgerEntryType.PRODUCTION_COST.name,
            entryType = LedgerEntryType.PRODUCTION_COST,
            direction = LedgerDirection.DEBIT,
            amountMinor = cost,
            currency = order.currency,
            sourceType = LedgerSourceType.ORDER,
            sourceId = order.id,
            externalReference = paymentReference,
            description = "Manual vendor cost snapshotted for offline order",
        )
    }

    private fun recordOfflineReceipt(
        organizationId: UUID,
        entryType: LedgerEntryType,
        accountCode: String,
        amountMinor: Long,
        currency: String,
        sourceType: LedgerSourceType,
        sourceId: UUID,
        paymentReference: String?,
        description: String,
    ) {
        ledgerEntryRepository.insert(
            organizationId = organizationId,
            accountCode = accountCode,
            entryType = entryType,
            direction = LedgerDirection.CREDIT,
            amountMinor = amountMinor,
            currency = currency,
            sourceType = sourceType,
            sourceId = sourceId,
            externalReference = paymentReference,
            description = description,
        )
        ledgerEntryRepository.insert(
            organizationId = organizationId,
            accountCode = LedgerEntryType.OFFLINE_SETTLEMENT.name,
            entryType = LedgerEntryType.OFFLINE_SETTLEMENT,
            direction = LedgerDirection.DEBIT,
            amountMinor = amountMinor,
            currency = currency,
            sourceType = sourceType,
            sourceId = sourceId,
            externalReference = paymentReference,
            description = "Funds received directly by the organization outside Rally26",
        )
    }

    fun getPayoutSummary(organizationId: UUID): PayoutSummary {
        val cutoff = holdingPeriodCutoff()
        val eligible = ledgerEntryRepository.findEligibleOrganizationEarningCredits(organizationId, cutoff).sumOf { it.amountMinor }
        val held = ledgerEntryRepository.findHeldOrganizationEarningCredits(organizationId, cutoff).sumOf { it.amountMinor }
        val pendingDebits = ledgerEntryRepository.findPendingOrganizationEarningDebits(organizationId).sumOf { it.amountMinor }
        return PayoutSummary(
            eligibleMinor = eligible,
            heldMinor = held,
            pendingDebitsMinor = pendingDebits,
            netAvailableMinor = eligible - pendingDebits,
        )
    }

    /**
     * The exact set of ORGANIZATION_EARNING entries a transfer right now would consume —
     * eligible (holding period elapsed) credits plus any pending reversal debits, which
     * together net to [PayoutSummary.netAvailableMinor]. Returned as entries (not just the
     * total) so [recordTransfer] can mark every one of them included-in-transfer.
     */
    fun getTransferableEntries(organizationId: UUID): List<LedgerEntry> {
        val cutoff = holdingPeriodCutoff()
        return ledgerEntryRepository.findEligibleOrganizationEarningCredits(organizationId, cutoff) +
            ledgerEntryRepository.findPendingOrganizationEarningDebits(organizationId)
    }

    @Transactional
    fun recordTransfer(
        organizationId: UUID,
        amountMinor: Long,
        currency: String,
        stripeTransferId: String,
        includedEntryIds: List<UUID>,
    ): LedgerEntry {
        val transferEntry =
            ledgerEntryRepository.insert(
                organizationId = organizationId,
                accountCode = LedgerEntryType.TRANSFER.name,
                entryType = LedgerEntryType.TRANSFER,
                direction = LedgerDirection.DEBIT,
                amountMinor = amountMinor,
                currency = currency,
                sourceType = LedgerSourceType.TRANSFER,
                sourceId = UUID.randomUUID(),
                externalReference = stripeTransferId,
                description = "Payout transfer to organization's connected Stripe account",
            )
        ledgerEntryRepository.markIncludedInTransfer(includedEntryIds, transferEntry.id)
        return transferEntry
    }

    @Transactional
    fun recordRefund(
        organizationId: UUID,
        sourceType: LedgerSourceType,
        sourceId: UUID,
        grossAmountMinor: Long,
        currency: String,
        stripeRefundId: String,
    ) {
        ledgerEntryRepository.insert(
            organizationId = organizationId,
            accountCode = LedgerEntryType.REFUND.name,
            entryType = LedgerEntryType.REFUND,
            direction = LedgerDirection.DEBIT,
            amountMinor = grossAmountMinor,
            currency = currency,
            sourceType = sourceType,
            sourceId = sourceId,
            externalReference = stripeRefundId,
            description = "Refund reversing the original gross amount",
        )
        // The platform fee is not returned on refund (ADR-017); the org's earning is
        // reversed net of that fee. This is a DEBIT, so — like a below-cost order — it's
        // picked up immediately by findPendingOrganizationEarningDebits, deducted from the
        // org's next payout even if the original earning was already transferred out.
        val orgPortionMinor = grossAmountMinor - platformFeeProperties.feeMinorOf(grossAmountMinor)
        ledgerEntryRepository.insert(
            organizationId = organizationId,
            accountCode = LedgerEntryType.ORGANIZATION_EARNING.name,
            entryType = LedgerEntryType.ORGANIZATION_EARNING,
            direction = LedgerDirection.DEBIT,
            amountMinor = orgPortionMinor,
            currency = currency,
            sourceType = sourceType,
            sourceId = sourceId,
            externalReference = stripeRefundId,
            description = "Reversal of organization earning due to refund",
        )
    }

    @Transactional
    fun recordCorrectionRefund(
        organizationId: UUID,
        correctionId: UUID,
        grossAmountMinor: Long,
        currency: String,
        providerReference: String,
    ) {
        ledgerEntryRepository.insert(
            organizationId,
            LedgerEntryType.REFUND.name,
            LedgerEntryType.REFUND,
            LedgerDirection.DEBIT,
            grossAmountMinor,
            currency,
            LedgerSourceType.CORRECTION,
            correctionId,
            providerReference,
            "Controlled refund reversing part or all of the original gross amount",
        )
        val organizationPortion = grossAmountMinor - platformFeeProperties.feeMinorOf(grossAmountMinor)
        ledgerEntryRepository.insert(
            organizationId,
            LedgerEntryType.ORGANIZATION_EARNING.name,
            LedgerEntryType.ORGANIZATION_EARNING,
            LedgerDirection.DEBIT,
            organizationPortion,
            currency,
            LedgerSourceType.CORRECTION,
            correctionId,
            providerReference,
            "Controlled refund reversal of organization earning",
        )
    }

    @Transactional
    fun reverseOfflineSource(
        organizationId: UUID,
        originalSourceType: LedgerSourceType,
        originalSourceId: UUID,
        correctionId: UUID,
        reason: String,
    ) {
        val originals = ledgerEntryRepository.findBySource(organizationId, originalSourceType, originalSourceId)
        if (originals.isEmpty()) throw IllegalStateException("Offline source has no ledger entries to reverse.")
        for (entry in originals) {
            ledgerEntryRepository.insert(
                organizationId = organizationId,
                accountCode = entry.accountCode,
                entryType = LedgerEntryType.MANUAL_ADJUSTMENT,
                direction = if (entry.direction == LedgerDirection.CREDIT) LedgerDirection.DEBIT else LedgerDirection.CREDIT,
                amountMinor = entry.amountMinor,
                currency = entry.currency,
                sourceType = LedgerSourceType.CORRECTION,
                sourceId = correctionId,
                externalReference = entry.id.toString(),
                description = "Offline reversal: $reason",
            )
        }
    }

    /**
     * A Stripe dispute was opened (DESIGN-DOC.md §14.6 item #4). Structurally identical
     * to [recordRefund] — CHARGEBACK debit for the gross amount, ORGANIZATION_EARNING
     * debit net of the platform fee — plus one additional CHARGEBACK_FEE debit for
     * Stripe's own non-refundable per-dispute fee. Rally26 absorbs that fee (founder
     * decision, 2026-08-12): it's tagged with the organization for traceability/
     * reporting, but deliberately gets no matching ORGANIZATION_EARNING debit, so it
     * never reduces what the org is owed or appears in getPayoutSummary/getTransferableEntries.
     */
    @Transactional
    fun recordDisputeOpened(
        organizationId: UUID,
        sourceType: LedgerSourceType,
        sourceId: UUID,
        grossAmountMinor: Long,
        currency: String,
        disputeFeeMinor: Long,
        stripeDisputeId: String,
    ) {
        ledgerEntryRepository.insert(
            organizationId = organizationId,
            accountCode = LedgerEntryType.CHARGEBACK.name,
            entryType = LedgerEntryType.CHARGEBACK,
            direction = LedgerDirection.DEBIT,
            amountMinor = grossAmountMinor,
            currency = currency,
            sourceType = sourceType,
            sourceId = sourceId,
            externalReference = stripeDisputeId,
            description = "Stripe dispute opened, reversing the original gross amount",
        )
        val orgPortionMinor = grossAmountMinor - platformFeeProperties.feeMinorOf(grossAmountMinor)
        ledgerEntryRepository.insert(
            organizationId = organizationId,
            accountCode = LedgerEntryType.ORGANIZATION_EARNING.name,
            entryType = LedgerEntryType.ORGANIZATION_EARNING,
            direction = LedgerDirection.DEBIT,
            amountMinor = orgPortionMinor,
            currency = currency,
            sourceType = sourceType,
            sourceId = sourceId,
            externalReference = stripeDisputeId,
            description = "Reversal of organization earning due to Stripe dispute",
        )
        if (disputeFeeMinor > 0) {
            ledgerEntryRepository.insert(
                organizationId = organizationId,
                accountCode = LedgerEntryType.CHARGEBACK_FEE.name,
                entryType = LedgerEntryType.CHARGEBACK_FEE,
                direction = LedgerDirection.DEBIT,
                amountMinor = disputeFeeMinor,
                currency = currency,
                sourceType = sourceType,
                sourceId = sourceId,
                externalReference = stripeDisputeId,
                description = "Stripe's non-refundable dispute fee, absorbed by Rally26 (not billed to the organization)",
            )
        }
    }

    /** A previously-opened dispute was won — reverses [recordDisputeOpened]'s CHARGEBACK/ORGANIZATION_EARNING debits with matching credits. The CHARGEBACK_FEE is never reinstated: Stripe's dispute fee is non-refundable even when Rally26 wins. */
    @Transactional
    fun recordDisputeWon(
        organizationId: UUID,
        sourceType: LedgerSourceType,
        sourceId: UUID,
        grossAmountMinor: Long,
        currency: String,
        stripeDisputeId: String,
    ) {
        ledgerEntryRepository.insert(
            organizationId = organizationId,
            accountCode = LedgerEntryType.CHARGEBACK.name,
            entryType = LedgerEntryType.CHARGEBACK,
            direction = LedgerDirection.CREDIT,
            amountMinor = grossAmountMinor,
            currency = currency,
            sourceType = sourceType,
            sourceId = sourceId,
            externalReference = stripeDisputeId,
            description = "Stripe dispute won, reinstating the original gross amount",
        )
        val orgPortionMinor = grossAmountMinor - platformFeeProperties.feeMinorOf(grossAmountMinor)
        ledgerEntryRepository.insert(
            organizationId = organizationId,
            accountCode = LedgerEntryType.ORGANIZATION_EARNING.name,
            entryType = LedgerEntryType.ORGANIZATION_EARNING,
            direction = LedgerDirection.CREDIT,
            amountMinor = orgPortionMinor,
            currency = currency,
            sourceType = sourceType,
            sourceId = sourceId,
            externalReference = stripeDisputeId,
            description = "Reinstatement of organization earning after winning a Stripe dispute",
        )
    }

    private fun holdingPeriodCutoff(): Instant = Instant.now().minus(Duration.ofDays(payoutProperties.holdingPeriodDays))
}
