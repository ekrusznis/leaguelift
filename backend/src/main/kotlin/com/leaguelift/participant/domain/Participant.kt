package com.leaguelift.participant.domain

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

enum class ParticipantStatus { ACTIVE, INACTIVE, ARCHIVED }

data class Participant(
    val id: UUID,
    val householdId: UUID,
    val organizationId: UUID,
    val firstName: String,
    val lastName: String,
    val dateOfBirth: LocalDate?,
    val notes: String?,
    val status: ParticipantStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class ParticipantTeamAssignment(
    val id: UUID,
    val participantId: UUID,
    val teamId: UUID,
    val organizationId: UUID,
    val status: String,
    val joinedAt: LocalDate?,
    val createdAt: Instant,
    val updatedAt: Instant,
)
