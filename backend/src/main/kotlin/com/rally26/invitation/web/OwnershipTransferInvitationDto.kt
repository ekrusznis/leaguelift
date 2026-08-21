package com.rally26.invitation.web

import com.rally26.invitation.domain.OwnershipTransferInvitation
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import java.time.Instant
import java.util.UUID

data class InviteOwnershipTransferRequest(
    @field:NotBlank @field:Email val email: String,
)

data class OwnershipTransferInvitationResponse(
    val id: UUID,
    val organizationId: UUID,
    val email: String,
    val status: String,
    val expiresAt: Instant,
    val acceptedAt: Instant?,
    val createdAt: Instant,
)

fun OwnershipTransferInvitation.toResponse() =
    OwnershipTransferInvitationResponse(
        id = id,
        organizationId = organizationId,
        email = email,
        status = status.name,
        expiresAt = expiresAt,
        acceptedAt = acceptedAt,
        createdAt = createdAt,
    )
