package com.leaguelift.dashboard.web

import java.util.UUID

/** Real: from team + participant_team. There is no per-team coach role-assignment yet
 * (DESIGN-DOC.md section 4.4 capability tiers), so this lists every team in the
 * organization rather than a coach-scoped subset. */
data class CoachTeamSummary(val teamId: UUID, val name: String, val sport: String, val participants: Long)

/** Team/coach/manager counts are real; attendance/availability are demo (no attendance model exists). */
data class RosterSummary(
	val athletes: Long,
	val isAttendanceDemoData: Boolean,
	val attendanceRatePercent: Int,
	val availabilityResponsePercent: Int,
)

/** Real, when a public page exists for the team. */
data class TeamPageStatusItem(val teamId: UUID, val teamName: String, val status: String, val slug: String?)

/** Real, when the organization has a campaign. Contribution totals are demo — contribution
 * recording is not built yet (DESIGN-DOC.md section 14.1, Phase 3). */
data class FundraisingProgress(
	val campaignId: UUID,
	val name: String,
	val status: String,
	val goalAmountMinor: Long,
	val currency: String,
	val isRaisedDemoData: Boolean,
	val raisedMinor: Long,
)

data class AnnouncementItem(val id: String, val title: String, val body: String, val postedLabel: String, val isNew: Boolean)
