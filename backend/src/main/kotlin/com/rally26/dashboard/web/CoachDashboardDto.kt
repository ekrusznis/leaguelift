package com.rally26.dashboard.web

import com.fasterxml.jackson.annotation.JsonProperty
import java.util.UUID

// See AthleteDashboardDto.kt for why every isXxx boolean field here needs
// @get:JsonProperty — otherwise Jackson's standard JavaBean introspection strips the
// "is" prefix and silently serializes e.g. isDemoData as "demoData".

/** Real: from team + participant_team, scoped to the caller's actual assigned teams
 * via `role_assignment`/org-owner-admin inheritance (Phase 7/ADR-020,
 * AuthorizationService.listAccessibleTeamIds) — not every team in the organization. */
data class CoachTeamSummary(val teamId: UUID, val name: String, val sport: String, val participants: Long)

/** Team/coach/manager counts are real; attendance/availability are demo (no attendance model exists). */
data class RosterSummary(
	val athletes: Long,
	@get:JsonProperty("isAttendanceDemoData") val isAttendanceDemoData: Boolean,
	val attendanceRatePercent: Int,
	val availabilityResponsePercent: Int,
)

/** Real, when a public page exists for the team. */
data class TeamPageStatusItem(val teamId: UUID, val teamName: String, val status: String, val slug: String?)

/** Real, when the caller's team has an active campaign — raised amount is real (Phase 3/ADR-015). */
data class FundraisingProgress(
	val campaignId: UUID,
	val name: String,
	val status: String,
	val goalAmountMinor: Long,
	val currency: String,
	@get:JsonProperty("isRaisedDemoData") val isRaisedDemoData: Boolean,
	val raisedMinor: Long,
)

data class AnnouncementItem(
	val id: String,
	val title: String,
	val body: String,
	val postedLabel: String,
	@get:JsonProperty("isNew") val isNew: Boolean,
)
