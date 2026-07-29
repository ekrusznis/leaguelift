package com.leaguelift.authorization.domain

import com.leaguelift.membership.domain.MembershipRole
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pure mapping-function tests for the deny-by-default role -> capability registry
 * (DESIGN-DOC.md section 4.2, ADR-020). These pin the exact tier boundaries so a future
 * change to who can do what is a deliberate, reviewed edit to this file's expectations,
 * not an accidental capability leak.
 */
class CapabilityRegistryTest {

	@Test
	fun `coach read tier can only view the team`() {
		val capabilities = CapabilityRegistry.teamCapabilities(ResourceRole.COACH_READ)
		assertTrue(Capabilities.TEAM_VIEW in capabilities)
		assertFalse(Capabilities.TEAM_ROSTER_MANAGE in capabilities)
		assertFalse(Capabilities.TEAM_PAGE_EDIT in capabilities)
		assertFalse(Capabilities.TEAM_STAFF_MANAGE in capabilities)
	}

	@Test
	fun `team editor tier can edit the page but not manage the roster`() {
		val capabilities = CapabilityRegistry.teamCapabilities(ResourceRole.TEAM_EDITOR)
		assertTrue(Capabilities.TEAM_PAGE_EDIT in capabilities)
		assertTrue(Capabilities.TEAM_FUNDRAISING_MANAGE in capabilities)
		assertTrue(Capabilities.TEAM_STORE_MANAGE in capabilities)
		assertFalse(Capabilities.TEAM_ROSTER_MANAGE in capabilities)
		assertFalse(Capabilities.TEAM_STAFF_MANAGE in capabilities)
		assertFalse(Capabilities.TEAM_FEE_VIEW in capabilities)
	}

	@Test
	fun `team manager tier is a strict superset of team editor`() {
		val editor = CapabilityRegistry.teamCapabilities(ResourceRole.TEAM_EDITOR)
		val manager = CapabilityRegistry.teamCapabilities(ResourceRole.TEAM_MANAGER)
		assertTrue(manager.containsAll(editor))
		assertTrue(Capabilities.TEAM_ROSTER_MANAGE in manager)
		assertTrue(Capabilities.TEAM_STAFF_MANAGE in manager)
		assertTrue(Capabilities.TEAM_FEE_VIEW in manager)
	}

	@Test
	fun `tournament viewer cannot manage the tournament`() {
		val capabilities = CapabilityRegistry.tournamentCapabilities(ResourceRole.TOURNAMENT_VIEWER)
		assertTrue(Capabilities.TOURNAMENT_VIEW in capabilities)
		assertFalse(Capabilities.TOURNAMENT_MANAGE in capabilities)
		assertFalse(Capabilities.TOURNAMENT_TEAM_MANAGE in capabilities)
	}

	@Test
	fun `a resource role granted for the wrong resource type resolves to no capabilities`() {
		// ATHLETE_SELF is a PARTICIPANT-context role; asking the TEAM registry about it
		// must deny, not accidentally fall through to some default grant.
		assertTrue(CapabilityRegistry.teamCapabilities(ResourceRole.ATHLETE_SELF).isEmpty())
		assertTrue(CapabilityRegistry.tournamentCapabilities(ResourceRole.ATHLETE_SELF).isEmpty())
		assertTrue(CapabilityRegistry.platformCapabilities(ResourceRole.ATHLETE_SELF).isEmpty())
	}

	@Test
	fun `legacy org-wide team and tournament administrator roles grant no org-level capabilities`() {
		// ADR-020: real team/tournament access now comes from an explicit role_assignment
		// grant, not the blanket organization_membership role — this is the exact gap
		// db/seed/V9000 flagged ("not actually scoped to just Varsity Soccer").
		assertTrue(CapabilityRegistry.organizationCapabilities(MembershipRole.TEAM_ADMINISTRATOR).isEmpty())
		assertTrue(CapabilityRegistry.organizationCapabilities(MembershipRole.TOURNAMENT_ADMINISTRATOR).isEmpty())
	}

	@Test
	fun `owner has strictly more organization capabilities than administrator`() {
		val owner = CapabilityRegistry.organizationCapabilities(MembershipRole.OWNER)
		val admin = CapabilityRegistry.organizationCapabilities(MembershipRole.ADMINISTRATOR)
		assertTrue(owner.containsAll(admin))
		assertTrue(Capabilities.ORG_BILLING_MANAGE in owner)
		assertFalse(Capabilities.ORG_BILLING_MANAGE in admin)
	}

	@Test
	fun `platform administrator is the only role with platform capabilities`() {
		assertTrue(CapabilityRegistry.platformCapabilities(ResourceRole.PLATFORM_ADMINISTRATOR).isNotEmpty())
		assertTrue(CapabilityRegistry.platformCapabilities(ResourceRole.COACH_READ).isEmpty())
		assertTrue(CapabilityRegistry.platformCapabilities(ResourceRole.TEAM_MANAGER).isEmpty())
	}

	@Test
	fun `athlete self capabilities never include anything financial`() {
		val capabilities = CapabilityRegistry.athleteSelfCapabilities()
		assertFalse(capabilities.any { it.contains("fee") || it.contains("payment") || it.contains("credit") || it.contains("payout") })
	}
}
