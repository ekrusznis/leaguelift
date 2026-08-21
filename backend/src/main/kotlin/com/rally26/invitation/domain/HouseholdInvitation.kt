package com.rally26.invitation.domain

import java.time.Instant
import java.util.UUID

enum class HouseholdInvitationKind { GUARDIAN, ATHLETE }

enum class HouseholdInvitationStatus { PENDING, ACCEPTED, REVOKED, EXPIRED }

/** See V99's migration comment for why this is a separate table from [Invitation]. */
data class HouseholdInvitation(
    val id: UUID,
    val organizationId: UUID,
    val householdId: UUID,
    val kind: HouseholdInvitationKind,
    val householdAdultId: UUID?,
    val participantId: UUID,
    val email: String,
    val status: HouseholdInvitationStatus,
    val invitedByUserId: UUID,
    val expiresAt: Instant,
    val acceptedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
)
