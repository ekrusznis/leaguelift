package com.rally26.team.domain

import java.time.Instant
import java.util.UUID

enum class TeamStatus { ACTIVE, ARCHIVED }

data class Team(
    val id: UUID,
    val organizationId: UUID,
    val name: String,
    val sport: String,
    val season: String?,
    val status: TeamStatus,
    val contactEmail: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)
