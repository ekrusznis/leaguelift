package com.leaguelift.ledger.application

import com.leaguelift.config.PayoutProperties
import com.leaguelift.config.PlatformFeeProperties
import com.leaguelift.fundraising.domain.Contribution
import com.leaguelift.ledger.domain.LedgerDirection
import com.leaguelift.ledger.domain.LedgerEntry
import com.leaguelift.ledger.domain.LedgerEntryType
import com.leaguelift.ledger.domain.LedgerSourceType
import com.leaguelift.ledger.persistence.LedgerEntryRepository
import com.leaguelift.order.domain.Order
import com.leaguelift.order.domain.OrderItem
import com.leaguelift.sponsorship.domain.Sponsorship
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
 * and records the CONTRIBUTION/GROSS_SALE/PRODUCTION_COST/LEAGUELIFT_PLATFORM_FEE/
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
			accountCode = LedgerEntryType.LEAGUELIFT_PLATFORM_FEE.name,
			entryType = LedgerEntryType.LEAGUELIFT_PLATFORM_FEE,
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
			accountCode = LedgerEntryType.LEAGUELIFT_PLATFORM_FEE.name,
			entryType = LedgerEntryType.LEAGUELIFT_PLATFORM_FEE,
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

	@Transactional
	fun recordConfirmedOrder(order: Order, orderItems: List<OrderItem>) {
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
			accountCode = LedgerEntryType.LEAGUELIFT_PLATFORM_FEE.name,
			entryType = LedgerEntryType.LEAGUELIFT_PLATFORM_FEE,
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
	 * Records money the organization received outside LeagueLift. The matching
	 * OFFLINE_SETTLEMENT debit documents that the funds were already settled
	 * externally. No ORGANIZATION_EARNING row is created, so this record can never
	 * be included in a LeagueLift payout transfer.
	 */
	@Transactional
	fun recordOfflineContribution(contribution: Contribution, paymentReference: String?) {
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
	fun recordOfflineSponsorship(sponsorship: Sponsorship, paymentReference: String?) {
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
	fun recordOfflineOrder(order: Order, orderItems: List<OrderItem>, paymentReference: String?) {
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
			description = "Funds received directly by the organization outside LeagueLift",
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
	fun recordTransfer(organizationId: UUID, amountMinor: Long, currency: String, stripeTransferId: String, includedEntryIds: List<UUID>): LedgerEntry {
		val transferEntry = ledgerEntryRepository.insert(
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
	fun recordRefund(organizationId: UUID, sourceType: LedgerSourceType, sourceId: UUID, grossAmountMinor: Long, currency: String, stripeRefundId: String) {
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

	private fun holdingPeriodCutoff(): Instant = Instant.now().minus(Duration.ofDays(payoutProperties.holdingPeriodDays))
}
