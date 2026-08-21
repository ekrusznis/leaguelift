package com.rally26.invitation.domain

import java.time.Instant
import java.util.UUID

enum class OwnershipTransferInvitationStatus { PENDING, ACCEPTED, REVOKED, EXPIRED }

/** See V101's migration comment for why this is a separate table from [Invitation]. */
data class OwnershipTransferInvitation(
    val id: UUID,
    val organizationId: UUID,
    val email: String,
    val status: OwnershipTransferInvitationStatus,
    val invitedByUserId: UUID,
    val expiresAt: Instant,
    val acceptedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
)
