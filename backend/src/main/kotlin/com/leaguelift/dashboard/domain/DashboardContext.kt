package com.leaguelift.dashboard.domain

import java.util.UUID

enum class DashboardRole { OWNER, COACH, PARENT, ATHLETE, TOURNAMENT_ADMIN, PLATFORM_ADMIN }

/**
 * Which dashboard a signed-in user should land on, plus the scope needed to fetch its
 * data. Resolved by [com.leaguelift.dashboard.application.DashboardContextService] —
 * as of Phase 7/ADR-020, from real tables across both the original interim lookups
 * (organization_membership, household_adult-by-email) and the new capability model
 * (role_assignment, guardian_relationship). This is deliberately *routing* to one
 * primary dashboard, not the full interactive context-switching UI DESIGN-DOC.md
 * section 4.2 describes as the long-term target — see ADR-020's consequences for what
 * that means a user who holds multiple contexts sees today.
 */
data class DashboardContext(
	val role: DashboardRole,
	val organizationId: UUID?,
	val householdId: UUID?,
	val tournamentId: UUID? = null,
)
