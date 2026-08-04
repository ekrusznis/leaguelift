package com.rally26.authorization.application

import com.rally26.authorization.domain.Capabilities
import com.rally26.authorization.domain.GuardianRelationship
import com.rally26.authorization.domain.GuardianRelationshipStatus
import com.rally26.authorization.domain.ResourceRole
import com.rally26.authorization.domain.RoleAssignment
import com.rally26.authorization.domain.RoleAssignmentContextType
import com.rally26.authorization.domain.RoleAssignmentStatus
import com.rally26.authorization.persistence.GuardianRelationshipRepository
import com.rally26.authorization.persistence.RoleAssignmentRepository
import com.rally26.common.error.ForbiddenException
import com.rally26.common.web.CurrentUser
import com.rally26.household.persistence.HouseholdRepository
import com.rally26.membership.application.MembershipService
import com.rally26.membership.domain.MembershipRole
import com.rally26.membership.domain.MembershipStatus
import com.rally26.membership.domain.OrganizationMembership
import com.rally26.membership.persistence.MembershipRepository
import com.rally26.team.domain.Team
import com.rally26.team.domain.TeamStatus
import com.rally26.team.persistence.TeamRepository
import com.rally26.tournament.persistence.TournamentRepository
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit-level coverage of the capability model's core resolution rules (DESIGN-DOC.md
 * section 4.2, ADR-020): deny-by-default, org owner/admin team/tournament inheritance,
 * explicit resource-scoped grants, and platform-admin bypass. See
 * `AuthorizationIsolationIntegrationTest` for the same scenarios exercised against a
 * real database end-to-end.
 */
class AuthorizationServiceTest {

	private val roleAssignmentRepository = mockk<RoleAssignmentRepository>()
	private val guardianRelationshipRepository = mockk<GuardianRelationshipRepository>()
	private val membershipRepository = mockk<MembershipRepository>()
	private val membershipService = mockk<MembershipService>()
	private val teamRepository = mockk<TeamRepository>()
	private val tournamentRepository = mockk<TournamentRepository>()
	private val householdRepository = mockk<HouseholdRepository>()

	private val service = AuthorizationService(
		roleAssignmentRepository, guardianRelationshipRepository, membershipRepository,
		membershipService, teamRepository, tournamentRepository, householdRepository,
	)

	private fun user(platformAdministrator: Boolean = false) =
		CurrentUser(UUID.randomUUID(), "user-${UUID.randomUUID()}@example.com", "User", platformAdministrator)

	private fun team(organizationId: UUID) = Team(
		id = UUID.randomUUID(), organizationId = organizationId, name = "Varsity Soccer", sport = "Soccer",
		season = "2024", status = TeamStatus.ACTIVE, contactEmail = null, createdAt = Instant.now(), updatedAt = Instant.now(),
	)

	private fun membership(organizationId: UUID, userId: UUID, role: MembershipRole) = OrganizationMembership(
		UUID.randomUUID(), organizationId, userId, role, MembershipStatus.ACTIVE, Instant.now(), Instant.now(),
	)

	private fun grant(organizationId: UUID, userId: UUID, resourceId: UUID, role: ResourceRole) = RoleAssignment(
		UUID.randomUUID(), organizationId, userId, RoleAssignmentContextType.TEAM, resourceId, role,
		RoleAssignmentStatus.ACTIVE, null, Instant.now(), Instant.now(),
	)

	@Test
	fun `a coach with no team grant is denied team view`() {
		val organizationId = UUID.randomUUID()
		val teamId = UUID.randomUUID()
		val caller = user()
		every { membershipRepository.findActiveMembership(organizationId, caller.userId) } returns null
		every { roleAssignmentRepository.findActiveForResource(caller.userId, RoleAssignmentContextType.TEAM, teamId) } returns null

		assertFalse(service.hasTeamCapability(organizationId, teamId, caller, Capabilities.TEAM_VIEW))
		assertFailsWith<ForbiddenException> {
			service.requireTeamCapability(organizationId, teamId, caller, Capabilities.TEAM_VIEW)
		}
	}

	@Test
	fun `a coach with an explicit COACH_READ grant can view but not manage the roster`() {
		val organizationId = UUID.randomUUID()
		val teamId = UUID.randomUUID()
		val caller = user()
		every { membershipRepository.findActiveMembership(organizationId, caller.userId) } returns null
		every { roleAssignmentRepository.findActiveForResource(caller.userId, RoleAssignmentContextType.TEAM, teamId) } returns
			grant(organizationId, caller.userId, teamId, ResourceRole.COACH_READ)

		assertTrue(service.hasTeamCapability(organizationId, teamId, caller, Capabilities.TEAM_VIEW))
		assertFalse(service.hasTeamCapability(organizationId, teamId, caller, Capabilities.TEAM_ROSTER_MANAGE))
	}

	@Test
	fun `a coach assigned to team A cannot access team B in the same organization`() {
		val organizationId = UUID.randomUUID()
		val teamA = UUID.randomUUID()
		val teamB = UUID.randomUUID()
		val caller = user()
		every { membershipRepository.findActiveMembership(organizationId, caller.userId) } returns null
		every { roleAssignmentRepository.findActiveForResource(caller.userId, RoleAssignmentContextType.TEAM, teamA) } returns
			grant(organizationId, caller.userId, teamA, ResourceRole.TEAM_MANAGER)
		every { roleAssignmentRepository.findActiveForResource(caller.userId, RoleAssignmentContextType.TEAM, teamB) } returns null

		assertTrue(service.hasTeamCapability(organizationId, teamA, caller, Capabilities.TEAM_VIEW))
		assertFalse(service.hasTeamCapability(organizationId, teamB, caller, Capabilities.TEAM_VIEW))
	}

	@Test
	fun `an organization owner inherits TEAM_MANAGER-tier capability for every team without an explicit grant`() {
		val organizationId = UUID.randomUUID()
		val teamId = UUID.randomUUID()
		val owner = user()
		every { membershipRepository.findActiveMembership(organizationId, owner.userId) } returns
			membership(organizationId, owner.userId, MembershipRole.OWNER)

		assertTrue(service.hasTeamCapability(organizationId, teamId, owner, Capabilities.TEAM_ROSTER_MANAGE))
	}

	@Test
	fun `a finance manager does not inherit team capabilities`() {
		val organizationId = UUID.randomUUID()
		val teamId = UUID.randomUUID()
		val financeManager = user()
		every { membershipRepository.findActiveMembership(organizationId, financeManager.userId) } returns
			membership(organizationId, financeManager.userId, MembershipRole.FINANCE_MANAGER)
		every { roleAssignmentRepository.findActiveForResource(financeManager.userId, RoleAssignmentContextType.TEAM, teamId) } returns null

		assertFalse(service.hasTeamCapability(organizationId, teamId, financeManager, Capabilities.TEAM_VIEW))
	}

	@Test
	fun `listAccessibleTeamIds returns every team for an inheriting owner and only assigned teams for a coach`() {
		val organizationId = UUID.randomUUID()
		val teamA = team(organizationId)
		val teamB = team(organizationId)
		every { teamRepository.findAll(organizationId, 0, 500) } returns listOf(teamA, teamB)

		val owner = user()
		every { membershipRepository.findActiveMembership(organizationId, owner.userId) } returns
			membership(organizationId, owner.userId, MembershipRole.OWNER)
		assertTrue(service.listAccessibleTeamIds(organizationId, owner, Capabilities.TEAM_VIEW) == setOf(teamA.id, teamB.id))

		val coach = user()
		every { membershipRepository.findActiveMembership(organizationId, coach.userId) } returns null
		every { roleAssignmentRepository.findActiveForUserAndContext(coach.userId, RoleAssignmentContextType.TEAM) } returns
			listOf(grant(organizationId, coach.userId, teamA.id, ResourceRole.COACH_READ))
		assertTrue(service.listAccessibleTeamIds(organizationId, coach, Capabilities.TEAM_VIEW) == setOf(teamA.id))
	}

	@Test
	fun `platform administrator bypasses every organization-scoped check`() {
		val organizationId = UUID.randomUUID()
		val teamId = UUID.randomUUID()
		val admin = user(platformAdministrator = true)

		assertTrue(service.hasTeamCapability(organizationId, teamId, admin, Capabilities.TEAM_ROSTER_MANAGE))
		assertTrue(service.hasPlatformCapability(admin, Capabilities.PLATFORM_ORG_MANAGE))
	}

	@Test
	fun `a non-platform-administrator is denied every platform capability regardless of organization role`() {
		val organizationId = UUID.randomUUID()
		val owner = user()
		every { membershipRepository.findActiveMembership(organizationId, owner.userId) } returns
			membership(organizationId, owner.userId, MembershipRole.OWNER)

		assertFalse(service.hasPlatformCapability(owner, Capabilities.PLATFORM_ORG_VIEW))
		assertFailsWith<ForbiddenException> {
			service.requirePlatformCapability(owner, Capabilities.PLATFORM_ORG_VIEW)
		}
	}

	@Test
	fun `an organization staff member has household view access without a guardian relationship`() {
		val organizationId = UUID.randomUUID()
		val householdId = UUID.randomUUID()
		val staff = user()
		every { membershipRepository.findActiveMembership(organizationId, staff.userId) } returns
			membership(organizationId, staff.userId, MembershipRole.ADMINISTRATOR)

		assertTrue(service.hasHouseholdCapability(organizationId, householdId, staff, Capabilities.HOUSEHOLD_VIEW))
	}

	@Test
	fun `a guardian of an unrelated household is denied`() {
		val organizationId = UUID.randomUUID()
		val householdId = UUID.randomUUID()
		val outsider = user()
		every { membershipRepository.findActiveMembership(organizationId, outsider.userId) } returns null
		every { guardianRelationshipRepository.findActiveForHousehold(outsider.userId, householdId) } returns null

		assertFalse(service.hasHouseholdCapability(organizationId, householdId, outsider, Capabilities.HOUSEHOLD_VIEW))
	}

	@Test
	fun `a finance manager can view and pay fees org-wide but cannot manage a household's profile`() {
		val organizationId = UUID.randomUUID()
		val householdId = UUID.randomUUID()
		val financeManager = user()
		every { membershipRepository.findActiveMembership(organizationId, financeManager.userId) } returns
			membership(organizationId, financeManager.userId, MembershipRole.FINANCE_MANAGER)
		every { guardianRelationshipRepository.findActiveForHousehold(financeManager.userId, householdId) } returns null

		assertTrue(service.hasHouseholdCapability(organizationId, householdId, financeManager, Capabilities.HOUSEHOLD_FEE_VIEW))
		assertTrue(service.hasHouseholdCapability(organizationId, householdId, financeManager, Capabilities.HOUSEHOLD_FEE_PAY))
		assertFalse(service.hasHouseholdCapability(organizationId, householdId, financeManager, Capabilities.HOUSEHOLD_PROFILE_MANAGE))
	}

	@Test
	fun `a viewer can view and pay fees org-wide but cannot see credits, orders, or manage a profile`() {
		val organizationId = UUID.randomUUID()
		val householdId = UUID.randomUUID()
		val viewer = user()
		every { membershipRepository.findActiveMembership(organizationId, viewer.userId) } returns
			membership(organizationId, viewer.userId, MembershipRole.VIEWER)
		every { guardianRelationshipRepository.findActiveForHousehold(viewer.userId, householdId) } returns null

		assertTrue(service.hasHouseholdCapability(organizationId, householdId, viewer, Capabilities.HOUSEHOLD_FEE_VIEW))
		assertTrue(service.hasHouseholdCapability(organizationId, householdId, viewer, Capabilities.HOUSEHOLD_FEE_PAY))
		assertFalse(service.hasHouseholdCapability(organizationId, householdId, viewer, Capabilities.HOUSEHOLD_CREDIT_VIEW))
		assertFalse(service.hasHouseholdCapability(organizationId, householdId, viewer, Capabilities.HOUSEHOLD_ORDER_VIEW))
		assertFalse(service.hasHouseholdCapability(organizationId, householdId, viewer, Capabilities.HOUSEHOLD_PROFILE_MANAGE))
	}

	@Test
	fun `a team administrator has no blanket household access beyond schedule visibility`() {
		val organizationId = UUID.randomUUID()
		val householdId = UUID.randomUUID()
		val teamAdmin = user()
		every { membershipRepository.findActiveMembership(organizationId, teamAdmin.userId) } returns
			membership(organizationId, teamAdmin.userId, MembershipRole.TEAM_ADMINISTRATOR)
		every { guardianRelationshipRepository.findActiveForHousehold(teamAdmin.userId, householdId) } returns null

		assertTrue(service.hasHouseholdCapability(organizationId, householdId, teamAdmin, Capabilities.EVENT_READ))
		assertFalse(service.hasHouseholdCapability(organizationId, householdId, teamAdmin, Capabilities.HOUSEHOLD_FEE_VIEW))
		assertFalse(service.hasHouseholdCapability(organizationId, householdId, teamAdmin, Capabilities.HOUSEHOLD_FEE_PAY))
	}

	@Test
	fun `a real guardian can manage their own household's profile regardless of org role`() {
		val organizationId = UUID.randomUUID()
		val householdId = UUID.randomUUID()
		val guardianViewer = user()
		every { membershipRepository.findActiveMembership(organizationId, guardianViewer.userId) } returns
			membership(organizationId, guardianViewer.userId, MembershipRole.VIEWER)
		every { guardianRelationshipRepository.findActiveForHousehold(guardianViewer.userId, householdId) } returns
			GuardianRelationship(
				UUID.randomUUID(), organizationId, householdId, UUID.randomUUID(), guardianViewer.userId,
				GuardianRelationshipStatus.ACTIVE, Instant.now(), Instant.now(),
			)

		assertTrue(service.hasHouseholdCapability(organizationId, householdId, guardianViewer, Capabilities.HOUSEHOLD_PROFILE_MANAGE))
	}

	@Test
	fun `listTeamStaffUserIds combines explicit team grants with inherited org owner-admin, deduplicated`() {
		val organizationId = UUID.randomUUID()
		val teamId = UUID.randomUUID()
		val editorUserId = UUID.randomUUID()
		val ownerUserId = UUID.randomUUID()
		every { roleAssignmentRepository.listActiveForResource(RoleAssignmentContextType.TEAM, teamId) } returns
			listOf(grant(organizationId, editorUserId, teamId, ResourceRole.TEAM_EDITOR))
		every { membershipRepository.listActiveManagers(organizationId) } returns listOf(membership(organizationId, ownerUserId, MembershipRole.OWNER))

		val result = service.listTeamStaffUserIds(organizationId, teamId, Capabilities.EVENT_RSVP_READ_TEAM)

		assertEquals(setOf(editorUserId, ownerUserId), result)
	}

	@Test
	fun `listTeamStaffUserIds excludes a COACH_READ grant that lacks the requested capability`() {
		val organizationId = UUID.randomUUID()
		val teamId = UUID.randomUUID()
		val coachUserId = UUID.randomUUID()
		every { roleAssignmentRepository.listActiveForResource(RoleAssignmentContextType.TEAM, teamId) } returns
			listOf(grant(organizationId, coachUserId, teamId, ResourceRole.COACH_READ))
		every { membershipRepository.listActiveManagers(organizationId) } returns emptyList()

		val result = service.listTeamStaffUserIds(organizationId, teamId, Capabilities.EVENT_RSVP_READ_TEAM)

		assertTrue(result.isEmpty())
	}
}
