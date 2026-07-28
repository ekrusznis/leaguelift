package com.leaguelift.dashboard.application

import com.leaguelift.common.web.CurrentUser
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AthleteDashboardServiceTest {

	private val service = AthleteDashboardService()
	private val currentUser = CurrentUser(UUID.randomUUID(), "maya.johnson@example.com", "Maya Johnson")

	@Test
	fun `getOverview returns demo data scoped to the caller's display name`() {
		val result = service.getOverview(currentUser)

		assertEquals("Maya Johnson", result.displayName)
		assertTrue(result.isDemoData)
	}

	@Test
	fun `getTeams returns demo data`() {
		assertTrue(service.getTeams(currentUser).isNotEmpty())
	}
}
