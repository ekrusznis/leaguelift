package com.leaguelift.dashboard.web

import com.leaguelift.dashboard.domain.DashboardContext
import com.leaguelift.dashboard.domain.DashboardRole
import java.util.UUID

data class DashboardContextResponse(
	val role: DashboardRole,
	val organizationId: UUID?,
	val householdId: UUID?,
	val tournamentId: UUID?,
)

fun DashboardContext.toResponse() = DashboardContextResponse(role, organizationId, householdId, tournamentId)
