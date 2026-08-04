package com.rally26.dashboard.web

import com.rally26.dashboard.domain.DashboardContext
import com.rally26.dashboard.domain.DashboardRole
import java.util.UUID

data class DashboardContextResponse(
	val role: DashboardRole,
	val organizationId: UUID?,
	val householdId: UUID?,
	val tournamentId: UUID?,
)

fun DashboardContext.toResponse() = DashboardContextResponse(role, organizationId, householdId, tournamentId)
