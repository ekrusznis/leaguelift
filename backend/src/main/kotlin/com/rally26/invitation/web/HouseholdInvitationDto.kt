package com.rally26.invitation.web

import com.rally26.invitation.domain.HouseholdInvitation
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class InviteGuardianRequest(
    @field:NotBlank @field:Size(max = 60) val firstName: String,
    @field:NotBlank @field:Size(max = 60) val lastName: String,
    @field:NotBlank @field:Email val email: String,
    @field:Size(max = 60) val relationship: String? = null,
)

data class InviteAthleteRequest(
    @field:NotBlank @field:Email val email: String,
)

data class HouseholdInvitationResponse(
    val id: UUID,
    val organizationId: UUID,
    val householdId: UUID,
    val kind: String,
    val participantId: UUID,
    val email: String,
    val status: String,
    val expiresAt: Instant,
    val acceptedAt: Instant?,
    val createdAt: Instant,
)

data class CreateHouseholdInvitationResponse(
    val invitation: HouseholdInvitationResponse,
    val rawToken: String,
)

fun HouseholdInvitation.toResponse() =
    HouseholdInvitationResponse(
        id = id,
        organizationId = organizationId,
        householdId = householdId,
        kind = kind.name,
        participantId = participantId,
        email = email,
        status = status.name,
        expiresAt = expiresAt,
        acceptedAt = acceptedAt,
        createdAt = createdAt,
    )
