package com.leaguelift.dashboard.web

import java.time.LocalDate
import java.util.UUID

data class HouseholdOverviewResponse(val householdName: String)

/** Real: from participant + participant_team + team. */
data class AthleteSummary(val participantId: UUID, val name: String, val teamNames: List<String>)

/**
 * Real, but approximate: summed from `fee_assignment.original_amount_minor` for
 * assignments not yet PAID/WAIVED/CANCELLED. There is no payment/credit ledger yet
 * (DESIGN-DOC.md section 14.1), so this is the assigned amount still outstanding, not a
 * balance net of partial payments — [isApproximate] tells the frontend to caveat it.
 */
data class OutstandingBalance(
	val totalOutstandingMinor: Long,
	val currency: String,
	val isApproximate: Boolean,
	val lineItems: List<FeeLineItem>,
)

data class FeeLineItem(val description: String, val amountMinor: Long, val status: String, val dueDate: LocalDate?)

/** Demo: no credit_event/credit_application tables exist yet. */
data class FamilyCredits(val isDemoData: Boolean, val currency: String, val pendingMinor: Long, val availableMinor: Long, val appliedThisSeasonMinor: Long)

/** Real: the organization's active campaigns. Contribution totals are demo. */
data class FundraiserSummary(val campaignId: UUID, val name: String, val isRaisedDemoData: Boolean, val raisedMinor: Long, val goalMinor: Long, val currency: String)

data class UpdateItem(val id: String, val title: String, val body: String, val postedLabel: String)
