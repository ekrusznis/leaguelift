package com.leaguelift.dashboard.web

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDate
import java.util.UUID

// See AthleteDashboardDto.kt for why every isXxx boolean field in this file needs
// @get:JsonProperty — otherwise Jackson's standard JavaBean introspection strips the
// "is" prefix and silently serializes e.g. isDemoData as "demoData".

data class HouseholdOverviewResponse(val householdName: String)

/** Real: from participant + participant_team + team. */
data class AthleteSummary(val participantId: UUID, val name: String, val teamNames: List<String>)

/**
 * Real: summed from each outstanding fee assignment's computed balance (original
 * amount minus active manual payments/adjustments — `fee_payment`/`fee_adjustment`,
 * DESIGN-DOC.md section 14.3, Phase 2 remainder). No longer approximate now that a
 * real payment/adjustment ledger exists to net against.
 */
data class OutstandingBalance(
	val totalOutstandingMinor: Long,
	val currency: String,
	val lineItems: List<FeeLineItem>,
)

data class FeeLineItem(val description: String, val balanceMinor: Long, val status: String, val dueDate: LocalDate?)

/** Demo: no credit_event/credit_application tables exist yet. */
data class FamilyCredits(
	@get:JsonProperty("isDemoData") val isDemoData: Boolean,
	val currency: String,
	val pendingMinor: Long,
	val availableMinor: Long,
	val appliedThisSeasonMinor: Long,
)

/** Real: the organization's active campaigns. Contribution totals are demo. */
data class FundraiserSummary(
	val campaignId: UUID,
	val name: String,
	@get:JsonProperty("isRaisedDemoData") val isRaisedDemoData: Boolean,
	val raisedMinor: Long,
	val goalMinor: Long,
	val currency: String,
)

data class UpdateItem(val id: String, val title: String, val body: String, val postedLabel: String)
