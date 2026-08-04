package com.rally26.membership.domain

import java.time.Instant
import java.util.UUID

enum class MembershipRole {
	OWNER,
	ADMINISTRATOR,
	TEAM_ADMINISTRATOR,
	TOURNAMENT_ADMINISTRATOR,
	VIEWER,
}

enum class MembershipStatus { ACTIVE, INVITED, REVOKED }

data class OrganizationMembership(
	val id: UUID,
	val organizationId: UUID,
	val userId: UUID,
	val role: MembershipRole,
	val status: MembershipStatus,
	val createdAt: Instant,
	val updatedAt: Instant,
)
