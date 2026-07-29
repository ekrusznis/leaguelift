package com.leaguelift.dashboard.web

import com.fasterxml.jackson.annotation.JsonProperty
import java.util.UUID

// See AthleteDashboardDto.kt for why every isXxx boolean field in this file needs
// @get:JsonProperty — otherwise Jackson's standard JavaBean introspection strips the
// "is" prefix and silently serializes e.g. isDemoData as "demoData".

/** Real: counted from team/participant/household/tournament tables. */
data class OwnerSummaryResponse(
	val organizationName: String,
	val activeTeams: Long,
	val participants: Long,
	val households: Long,
	val upcomingTournaments: Long,
)

/**
 * Fee fields (feesAssignedMinor/feesCollectedMinor/outstandingMinor) are real, from
 * fee_assignment/fee_payment/fee_adjustment (DESIGN-DOC.md section 14.3, Phase 2
 * remainder) — [isFeesDemoData] is false. Fundraising/apparel/payout fields stay demo
 * ([isFundraisingDemoData] true) — no contribution-recording or commerce tables exist
 * yet (DESIGN-DOC.md section 8.6), mirrors the [TeamPerformanceRow.isFundraisingDemoData]
 * per-concern-flag convention used elsewhere in this file.
 */
data class FinancialOverviewResponse(
	@get:JsonProperty("isFeesDemoData") val isFeesDemoData: Boolean,
	@get:JsonProperty("isFundraisingDemoData") val isFundraisingDemoData: Boolean,
	val currency: String,
	val feesAssignedMinor: Long,
	val feesCollectedMinor: Long,
	val outstandingMinor: Long,
	val fundraisingMinor: Long,
	val apparelSalesMinor: Long,
	val pendingPayoutMinor: Long,
)

/** Demo: no dedicated exception-queue model exists yet. */
data class AttentionItem(
	val id: String,
	val tone: String,
	val title: String,
	val subtitle: String,
	val count: Int,
)

/** Team identity/participant count are real; fundraising figures are demo until campaign contributions are recorded. */
data class TeamPerformanceRow(
	val teamId: UUID,
	val name: String,
	val sport: String,
	val participants: Long,
	val status: String,
	@get:JsonProperty("isFundraisingDemoData") val isFundraisingDemoData: Boolean,
	val fundraisingRaisedMinor: Long?,
	val fundraisingGoalMinor: Long?,
)

/** Demo. */
data class OwnerOnboardingProgress(
	@get:JsonProperty("isDemoData") val isDemoData: Boolean,
	val completedSteps: Int,
	val totalSteps: Int,
)

/** Demo. */
data class ReportMetric(
	@get:JsonProperty("isDemoData") val isDemoData: Boolean,
	val label: String,
	val valueMinor: Long,
	val trendPercent: Double,
)
