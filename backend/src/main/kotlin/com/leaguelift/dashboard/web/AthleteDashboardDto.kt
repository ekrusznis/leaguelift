package com.leaguelift.dashboard.web

data class AthleteOverviewResponse(val displayName: String, val isDemoData: Boolean, val nextEvent: NextEventSummary?)

data class NextEventSummary(val title: String, val subtitle: String, val dateLabel: String, val location: String)

data class AthleteTeamSummary(val name: String, val detail: String, val coachName: String)

data class HistoryItem(val id: String, val opponent: String, val dateLabel: String, val location: String, val result: String, val won: Boolean?)

data class GuardianSummary(val name: String, val role: String, val email: String, val phone: String)
