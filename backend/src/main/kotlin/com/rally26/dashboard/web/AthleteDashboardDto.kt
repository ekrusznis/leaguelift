package com.rally26.dashboard.web

import com.fasterxml.jackson.annotation.JsonProperty

// Kotlin's `val isFoo: Boolean` generates a getter literally named `isFoo()`; standard
// JavaBean introspection (which Jackson follows) then strips the "is" prefix, silently
// serializing it as "foo" instead of "isFoo" — @get:JsonProperty pins the wire name so
// isDemoData-style flags actually reach the frontend as isDemoData, not demoData.
data class AthleteOverviewResponse(
    val displayName: String,
    @get:JsonProperty("isDemoData") val isDemoData: Boolean,
    val nextEvent: NextEventSummary?,
)

data class NextEventSummary(
    val title: String,
    val subtitle: String,
    val dateLabel: String,
    val location: String,
)

data class AthleteTeamSummary(
    val name: String,
    val detail: String,
    val coachName: String,
)

data class HistoryItem(
    val id: String,
    val opponent: String,
    val dateLabel: String,
    val location: String,
    val result: String,
    val won: Boolean?,
)

data class GuardianSummary(
    val name: String,
    val role: String,
    val email: String,
    val phone: String,
)
