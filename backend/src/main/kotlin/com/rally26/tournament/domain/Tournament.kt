package com.rally26.tournament.domain

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

enum class TournamentStatus { ACTIVE, ARCHIVED }

data class Tournament(
    val id: UUID,
    val organizationId: UUID,
    val name: String,
    val sport: String?,
    val status: TournamentStatus,
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val location: String?,
    val contactEmail: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)
