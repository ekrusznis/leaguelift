package com.rally26.dashboard.web

import java.time.LocalDate
import java.util.UUID

/** Real: from `tournament` directly. */
data class TournamentSummaryResponse(
    val tournamentId: UUID,
    val name: String,
    val sport: String?,
    val status: String,
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val location: String?,
)

/** Real, when a public page exists for the tournament. Same pattern as the Coach dashboard's team-page-status card. */
data class TournamentPageStatusItem(
    val tournamentId: UUID,
    val tournamentName: String,
    val status: String,
    val slug: String?,
)
