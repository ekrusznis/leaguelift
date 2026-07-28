package com.leaguelift.dashboard.web

import java.util.UUID

/** Real: counted from team/participant/household/tournament tables. */
data class OwnerSummaryResponse(
	val organizationName: String,
	val activeTeams: Long,
	val participants: Long,
	val households: Long,
	val upcomingTournaments: Long,
)

/** Demo: no ledger/payment tables exist yet (DESIGN-DOC.md section 8.6). */
data class FinancialOverviewResponse(
	val isDemoData: Boolean,
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
	val isFundraisingDemoData: Boolean,
	val fundraisingRaisedMinor: Long?,
	val fundraisingGoalMinor: Long?,
)

/** Demo. */
data class OwnerOnboardingProgress(val isDemoData: Boolean, val completedSteps: Int, val totalSteps: Int)

/** Demo. */
data class ReportMetric(val isDemoData: Boolean, val label: String, val valueMinor: Long, val trendPercent: Double)
