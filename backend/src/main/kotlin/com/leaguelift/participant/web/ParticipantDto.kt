package com.leaguelift.participant.web

import com.leaguelift.common.web.PageResponse
import com.leaguelift.participant.domain.Participant
import com.leaguelift.participant.domain.ParticipantTeamAssignment
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class CreateParticipantRequest(
    @field:NotBlank @field:Size(min = 1, max = 60) val firstName: String,
    @field:NotBlank @field:Size(min = 1, max = 60) val lastName: String,
    val dateOfBirth: LocalDate? = null,
    @field:Size(max = 1000) val notes: String? = null,
)

data class UpdateParticipantRequest(
    @field:Size(min = 1, max = 60) val firstName: String? = null,
    @field:Size(min = 1, max = 60) val lastName: String? = null,
    val dateOfBirth: LocalDate? = null,
    @field:Size(max = 1000) val notes: String? = null,
)

data class AssignTeamRequest(
    val teamId: UUID,
    val joinedAt: LocalDate? = null,
)

data class ParticipantResponse(
    val id: UUID,
    val householdId: UUID,
    val organizationId: UUID,
    val firstName: String,
    val lastName: String,
    val dateOfBirth: LocalDate?,
    val notes: String?,
    val status: String,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class ParticipantTeamResponse(
    val id: UUID,
    val participantId: UUID,
    val teamId: UUID,
    val status: String,
    val joinedAt: LocalDate?,
    val createdAt: Instant,
)

fun Participant.toResponse() = ParticipantResponse(
    id, householdId, organizationId, firstName, lastName, dateOfBirth, notes, status.name, createdAt, updatedAt,
)

fun ParticipantTeamAssignment.toResponse() = ParticipantTeamResponse(
    id, participantId, teamId, status, joinedAt, createdAt,
)


typealias ParticipantPageResponse = PageResponse<ParticipantResponse>
