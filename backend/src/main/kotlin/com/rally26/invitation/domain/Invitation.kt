package com.rally26.invitation.domain

import com.rally26.membership.domain.MembershipRole
import java.time.Instant
import java.util.UUID

enum class InvitationStatus { PENDING, ACCEPTED, REVOKED, EXPIRED }

/** Roles that can be granted via a generic invitation. OWNER is deliberately excluded
 *  — ownership transfer is its own controlled workflow (DESIGN-DOC.md section 7.2). */
val INVITABLE_ROLES: Set<MembershipRole> =
    setOf(
        MembershipRole.ADMINISTRATOR,
        MembershipRole.TEAM_ADMINISTRATOR,
        MembershipRole.TOURNAMENT_ADMINISTRATOR,
        MembershipRole.VIEWER,
    )

data class Invitation(
    val id: UUID,
    val organizationId: UUID,
    val email: String,
    val role: MembershipRole,
    val status: InvitationStatus,
    val invitedByUserId: UUID,
    val token: String,
    val expiresAt: Instant,
    val acceptedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
)
