package com.leaguelift.dashboard.domain

import java.util.UUID

enum class DashboardRole { OWNER, COACH, PARENT, ATHLETE }

/**
 * Which dashboard a signed-in user should land on, plus the scope needed to fetch
 * its data. Resolved by [com.leaguelift.dashboard.application.DashboardContextService]
 * from real tables (organization_membership, household_adult) — see that class for the
 * resolution order and its known interim limitations (no guardian_relationship table
 * yet, DESIGN-DOC.md section 4.2/8.3).
 */
data class DashboardContext(
	val role: DashboardRole,
	val organizationId: UUID?,
	val householdId: UUID?,
)
