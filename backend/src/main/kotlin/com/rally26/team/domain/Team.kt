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
    /** Phase 24 slice 24.5 (ADR-071): null means "inherit organization default" — a real value overrides it. */
    val timezoneOverride: String? = null,
)
