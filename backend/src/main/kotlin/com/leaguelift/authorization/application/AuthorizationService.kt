package com.leaguelift.authorization.application

import com.leaguelift.authorization.domain.AuthorizationContext
import com.leaguelift.authorization.domain.CapabilityRegistry
import com.leaguelift.authorization.domain.ContextType
import com.leaguelift.authorization.domain.ResourceRole
import com.leaguelift.authorization.domain.RoleAssignment
import com.leaguelift.authorization.domain.RoleAssignmentContextType
import com.leaguelift.authorization.persistence.GuardianRelationshipRepository
import com.leaguelift.authorization.persistence.RoleAssignmentRepository
import com.leaguelift.common.error.ForbiddenException
import com.leaguelift.common.web.CurrentUser
import com.leaguelift.household.persistence.HouseholdRepository
import com.leaguelift.membership.application.MembershipService
import com.leaguelift.membership.domain.MembershipRole
import com.leaguelift.membership.persistence.MembershipRepository
import com.leaguelift.team.persistence.TeamRepository
import com.leaguelift.tournament.persistence.TournamentRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

private const val LISTING_LIMIT = 500

/**
 * The capability-based authorization model's single entry point (DESIGN-DOC.md section
 * 4.2, ADR-020). Deliberately additive alongside [MembershipService], not a replacement
 * of it: the ORGANIZATION context is still resolved from `organization_membership`
 * (consulted here as one input, per ADR-020's "additive, not a big-bang replacement"
 * decision) — this service adds the TEAM, TOURNAMENT, HOUSEHOLD, ATHLETE, and
 * PLATFORM_ADMIN contexts on top, backed by the new `role_assignment` and
 * `guardian_relationship` tables.
 *
 * Every `require*`/`has*` method is deny-by-default: a missing grant is always "no",
 * never inferred from role name, email, or any other signal outside these tables (plus
 * the one explicit, documented exception every method call below states — a platform
 * administrator, and an organization OWNER/ADMINISTRATOR's team/tournament
 * inheritance).
 */
@Service
class AuthorizationService(
	private val roleAssignmentRepository: RoleAssignmentRepository,
	private val guardianRelationshipRepository: GuardianRelationshipRepository,
	private val membershipRepository: MembershipRepository,
	private val membershipService: MembershipService,
	private val teamRepository: TeamRepository,
	private val tournamentRepository: TournamentRepository,
	private val householdRepository: HouseholdRepository,
) {

	// --- /me/contexts ---------------------------------------------------------------

	fun listContexts(currentUser: CurrentUser): List<AuthorizationContext> {
		val contexts = mutableListOf<AuthorizationContext>()

		contexts += AuthorizationContext(
			contextType = ContextType.PERSONAL,
			resourceId = null,
			organizationId = null,
			label = currentUser.displayName,
			role = "SELF",
			capabilities = CapabilityRegistry.personalCapabilities(),
		)

		if (currentUser.platformAdministrator) {
			contexts += AuthorizationContext(
				contextType = ContextType.PLATFORM_ADMIN,
				resourceId = null,
				organizationId = null,
				label = "LeagueLift Platform",
				role = ResourceRole.PLATFORM_ADMINISTRATOR.name,
				capabilities = CapabilityRegistry.platformCapabilities(ResourceRole.PLATFORM_ADMINISTRATOR),
			)
		}

		val memberships = membershipRepository.listActiveForUser(currentUser.userId)
		for (membership in memberships) {
			contexts += AuthorizationContext(
				contextType = ContextType.ORGANIZATION,
				resourceId = membership.organizationId,
				organizationId = membership.organizationId,
				label = membership.role.name,
				role = membership.role.name,
				capabilities = CapabilityRegistry.organizationCapabilities(membership.role),
			)
		}

		val teamAssignments = roleAssignmentRepository.findActiveForUserAndContext(currentUser.userId, RoleAssignmentContextType.TEAM)
		for (assignment in teamAssignments) {
			val team = assignment.resourceId?.let { teamRepository.findById(it, assignment.organizationId!!) } ?: continue
			contexts += AuthorizationContext(
				contextType = ContextType.TEAM,
				resourceId = team.id,
				organizationId = team.organizationId,
				label = team.name,
				role = assignment.role.name,
				capabilities = CapabilityRegistry.teamCapabilities(assignment.role),
			)
		}
		// Inherited team contexts: an OWNER/ADMINISTRATOR sees every team in their
		// organization at TEAM_MANAGER tier, without a row in role_assignment for each
		// one (DESIGN-DOC.md section 4.2's inheritance example).
		val inheritedTeamOrgIds = memberships.filter { it.role == MembershipRole.OWNER || it.role == MembershipRole.ADMINISTRATOR }.map { it.organizationId }
		val explicitTeamIds = teamAssignments.mapNotNull { it.resourceId }.toSet()
		for (organizationId in inheritedTeamOrgIds) {
			for (team in teamRepository.findAll(organizationId, 0, LISTING_LIMIT)) {
				if (team.id in explicitTeamIds) continue
				contexts += AuthorizationContext(
					contextType = ContextType.TEAM,
					resourceId = team.id,
					organizationId = team.organizationId,
					label = team.name,
					role = ResourceRole.TEAM_MANAGER.name,
					capabilities = CapabilityRegistry.teamCapabilities(ResourceRole.TEAM_MANAGER),
				)
			}
		}

		val tournamentAssignments = roleAssignmentRepository.findActiveForUserAndContext(currentUser.userId, RoleAssignmentContextType.TOURNAMENT)
		for (assignment in tournamentAssignments) {
			val tournament = assignment.resourceId?.let { tournamentRepository.findById(it, assignment.organizationId!!) } ?: continue
			contexts += AuthorizationContext(
				contextType = ContextType.TOURNAMENT,
				resourceId = tournament.id,
				organizationId = tournament.organizationId,
				label = tournament.name,
				role = assignment.role.name,
				capabilities = CapabilityRegistry.tournamentCapabilities(assignment.role),
			)
		}
		val explicitTournamentIds = tournamentAssignments.mapNotNull { it.resourceId }.toSet()
		for (organizationId in inheritedTeamOrgIds) {
			for (tournament in tournamentRepository.findAll(organizationId, 0, LISTING_LIMIT)) {
				if (tournament.id in explicitTournamentIds) continue
				contexts += AuthorizationContext(
					contextType = ContextType.TOURNAMENT,
					resourceId = tournament.id,
					organizationId = tournament.organizationId,
					label = tournament.name,
					role = ResourceRole.TOURNAMENT_ADMINISTRATOR.name,
					capabilities = CapabilityRegistry.tournamentCapabilities(ResourceRole.TOURNAMENT_ADMINISTRATOR),
				)
			}
		}

		for (relationship in guardianRelationshipRepository.findActiveForUser(currentUser.userId)) {
			val household = householdRepository.findById(relationship.householdId, relationship.organizationId) ?: continue
			contexts += AuthorizationContext(
				contextType = ContextType.HOUSEHOLD,
				resourceId = household.id,
				organizationId = household.organizationId,
				label = household.displayName,
				role = "GUARDIAN",
				capabilities = CapabilityRegistry.householdCapabilities(),
			)
		}

		for (assignment in roleAssignmentRepository.findActiveForUserAndContext(currentUser.userId, RoleAssignmentContextType.PARTICIPANT)) {
			contexts += AuthorizationContext(
				contextType = ContextType.ATHLETE,
				resourceId = assignment.resourceId,
				organizationId = assignment.organizationId,
				label = "My Athlete Profile",
				role = ResourceRole.ATHLETE_SELF.name,
				capabilities = CapabilityRegistry.athleteSelfCapabilities(),
			)
		}

		return contexts
	}

	// --- Team context -----------------------------------------------------------------

	fun hasTeamCapability(organizationId: UUID, teamId: UUID, currentUser: CurrentUser, capability: String): Boolean {
		if (currentUser.platformAdministrator) return true
		if (organizationOwnerOrAdmin(organizationId, currentUser)) {
			return capability in CapabilityRegistry.teamCapabilities(ResourceRole.TEAM_MANAGER)
		}
		val assignment = roleAssignmentRepository.findActiveForResource(currentUser.userId, RoleAssignmentContextType.TEAM, teamId) ?: return false
		return capability in CapabilityRegistry.teamCapabilities(assignment.role)
	}

	fun requireTeamCapability(organizationId: UUID, teamId: UUID, currentUser: CurrentUser, capability: String) {
		if (!hasTeamCapability(organizationId, teamId, currentUser, capability)) {
			throw ForbiddenException("CAPABILITY_DENIED", "You do not have the '$capability' capability for this team.")
		}
	}

	/**
	 * Every team id this user holds [capability] for in this organization — explicit
	 * `role_assignment` grants plus inherited OWNER/ADMINISTRATOR access. Used to scope
	 * "my teams" queries (e.g. CoachDashboardService) instead of returning every team
	 * in the organization regardless of assignment.
	 */
	fun listAccessibleTeamIds(organizationId: UUID, currentUser: CurrentUser, capability: String): Set<UUID> {
		if (currentUser.platformAdministrator || organizationOwnerOrAdmin(organizationId, currentUser)) {
			if (currentUser.platformAdministrator || capability in CapabilityRegistry.teamCapabilities(ResourceRole.TEAM_MANAGER)) {
				return teamRepository.findAll(organizationId, 0, LISTING_LIMIT).map { it.id }.toSet()
			}
		}
		return roleAssignmentRepository.findActiveForUserAndContext(currentUser.userId, RoleAssignmentContextType.TEAM)
			.filter { it.organizationId == organizationId && capability in CapabilityRegistry.teamCapabilities(it.role) }
			.mapNotNull { it.resourceId }
			.toSet()
	}

	/**
	 * Every user currently holding [capability] as staff on [teamId] — explicit
	 * `role_assignment` grants plus inherited org OWNER/ADMINISTRATOR — used to resolve
	 * notification *recipients* (Phase 10 slice 4, ADR-029), not just to answer a
	 * yes/no permission check the way [hasTeamCapability] does.
	 */
	fun listTeamStaffUserIds(organizationId: UUID, teamId: UUID, capability: String): Set<UUID> {
		val explicit = roleAssignmentRepository.listActiveForResource(RoleAssignmentContextType.TEAM, teamId)
			.filter { capability in CapabilityRegistry.teamCapabilities(it.role) }
			.map { it.userId }
			.toSet()
		val inherited = if (capability in CapabilityRegistry.teamCapabilities(ResourceRole.TEAM_MANAGER)) {
			membershipRepository.listActiveManagers(organizationId).map { it.userId }.toSet()
		} else {
			emptySet()
		}
		return explicit + inherited
	}

	// --- Tournament context ------------------------------------------------------------

	fun hasTournamentCapability(organizationId: UUID, tournamentId: UUID, currentUser: CurrentUser, capability: String): Boolean {
		if (currentUser.platformAdministrator) return true
		if (organizationOwnerOrAdmin(organizationId, currentUser)) {
			return capability in CapabilityRegistry.tournamentCapabilities(ResourceRole.TOURNAMENT_ADMINISTRATOR)
		}
		val assignment = roleAssignmentRepository.findActiveForResource(currentUser.userId, RoleAssignmentContextType.TOURNAMENT, tournamentId) ?: return false
		return capability in CapabilityRegistry.tournamentCapabilities(assignment.role)
	}

	fun requireTournamentCapability(organizationId: UUID, tournamentId: UUID, currentUser: CurrentUser, capability: String) {
		if (!hasTournamentCapability(organizationId, tournamentId, currentUser, capability)) {
			throw ForbiddenException("CAPABILITY_DENIED", "You do not have the '$capability' capability for this tournament.")
		}
	}

	fun listAccessibleTournamentIds(organizationId: UUID, currentUser: CurrentUser, capability: String): Set<UUID> {
		if (currentUser.platformAdministrator || organizationOwnerOrAdmin(organizationId, currentUser)) {
			if (currentUser.platformAdministrator || capability in CapabilityRegistry.tournamentCapabilities(ResourceRole.TOURNAMENT_ADMINISTRATOR)) {
				return tournamentRepository.findAll(organizationId, 0, LISTING_LIMIT).map { it.id }.toSet()
			}
		}
		return roleAssignmentRepository.findActiveForUserAndContext(currentUser.userId, RoleAssignmentContextType.TOURNAMENT)
			.filter { it.organizationId == organizationId && capability in CapabilityRegistry.tournamentCapabilities(it.role) }
			.mapNotNull { it.resourceId }
			.toSet()
	}

	// --- Platform context ---------------------------------------------------------------

	fun hasPlatformCapability(currentUser: CurrentUser, capability: String): Boolean {
		if (!currentUser.platformAdministrator) return false
		return capability in CapabilityRegistry.platformCapabilities(ResourceRole.PLATFORM_ADMINISTRATOR)
	}

	fun requirePlatformCapability(currentUser: CurrentUser, capability: String) {
		if (!hasPlatformCapability(currentUser, capability)) {
			throw ForbiddenException("CAPABILITY_DENIED", "You do not have the '$capability' platform capability.")
		}
	}

	// --- Household context (guardian) ---------------------------------------------------

	fun hasHouseholdCapability(organizationId: UUID, householdId: UUID, currentUser: CurrentUser, capability: String): Boolean {
		if (currentUser.platformAdministrator) return true
		if (membershipRepository.findActiveMembership(organizationId, currentUser.userId) != null) return true
		val relationship = guardianRelationshipRepository.findActiveForHousehold(currentUser.userId, householdId) ?: return false
		return capability in CapabilityRegistry.householdCapabilities() && relationship.organizationId == organizationId
	}

	// --- Athlete (participant self) context ----------------------------------------------

	/** The participant this signed-in user is the controlled self-login for, if any. */
	fun findAthleteSelfLink(currentUser: CurrentUser): RoleAssignment? =
		roleAssignmentRepository.findActiveForUserAndContext(currentUser.userId, RoleAssignmentContextType.PARTICIPANT).firstOrNull()

	fun hasParticipantCapability(currentUser: CurrentUser, participantId: UUID, capability: String): Boolean {
		if (currentUser.platformAdministrator) return true
		val assignment = roleAssignmentRepository.findActiveForResource(currentUser.userId, RoleAssignmentContextType.PARTICIPANT, participantId) ?: return false
		return capability in CapabilityRegistry.athleteSelfCapabilities()
	}

	// --- Grants (org-manager-only; PLATFORM/PARTICIPANT grants are code-only, see class doc) ---

	/** Every active role grant on this team — the admin UI's "who currently has access" list (ADR-020 consequence: grant/revoke had no UI to show this). */
	fun listTeamRoleAssignments(organizationId: UUID, teamId: UUID, currentUser: CurrentUser): List<RoleAssignment> {
		membershipService.requireManagerRole(organizationId, currentUser)
		requireNotNull(teamRepository.findById(teamId, organizationId)) { "Team not found in this organization." }
		return roleAssignmentRepository.listActiveForResource(RoleAssignmentContextType.TEAM, teamId)
	}

	/** Every active role grant on this tournament — same purpose as [listTeamRoleAssignments]. */
	fun listTournamentRoleAssignments(organizationId: UUID, tournamentId: UUID, currentUser: CurrentUser): List<RoleAssignment> {
		membershipService.requireManagerRole(organizationId, currentUser)
		requireNotNull(tournamentRepository.findById(tournamentId, organizationId)) { "Tournament not found in this organization." }
		return roleAssignmentRepository.listActiveForResource(RoleAssignmentContextType.TOURNAMENT, tournamentId)
	}

	@Transactional
	fun grantTeamRole(organizationId: UUID, teamId: UUID, targetUserId: UUID, role: ResourceRole, currentUser: CurrentUser): RoleAssignment {
		membershipService.requireManagerRole(organizationId, currentUser)
		require(role == ResourceRole.COACH_READ || role == ResourceRole.TEAM_EDITOR || role == ResourceRole.TEAM_MANAGER) {
			"'$role' is not a valid team role."
		}
		requireNotNull(teamRepository.findById(teamId, organizationId)) { "Team not found in this organization." }
		return roleAssignmentRepository.grant(organizationId, targetUserId, RoleAssignmentContextType.TEAM, teamId, role, currentUser.userId)
	}

	@Transactional
	fun revokeTeamRole(organizationId: UUID, teamId: UUID, assignmentId: UUID, currentUser: CurrentUser) {
		membershipService.requireManagerRole(organizationId, currentUser)
		roleAssignmentRepository.revoke(assignmentId)
	}

	@Transactional
	fun grantTournamentRole(organizationId: UUID, tournamentId: UUID, targetUserId: UUID, role: ResourceRole, currentUser: CurrentUser): RoleAssignment {
		membershipService.requireManagerRole(organizationId, currentUser)
		require(role == ResourceRole.TOURNAMENT_VIEWER || role == ResourceRole.TOURNAMENT_ADMINISTRATOR) {
			"'$role' is not a valid tournament role."
		}
		requireNotNull(tournamentRepository.findById(tournamentId, organizationId)) { "Tournament not found in this organization." }
		return roleAssignmentRepository.grant(organizationId, targetUserId, RoleAssignmentContextType.TOURNAMENT, tournamentId, role, currentUser.userId)
	}

	@Transactional
	fun revokeTournamentRole(organizationId: UUID, tournamentId: UUID, assignmentId: UUID, currentUser: CurrentUser) {
		membershipService.requireManagerRole(organizationId, currentUser)
		roleAssignmentRepository.revoke(assignmentId)
	}

	/**
	 * Links an athlete's own controlled login to their participant record (DESIGN-DOC.md
	 * section 4.6 / ADR-020) — formalizes the existing `db/seed` "controlled test
	 * account" pattern, not a self-service signup flow. [grantedByUserId] must be an
	 * active guardian of the participant's household; there is no REST endpoint for
	 * this in this slice (no consent-workflow UI exists yet — see ADR-020).
	 */
	@Transactional
	fun linkAthleteSelf(organizationId: UUID, householdId: UUID, participantId: UUID, athleteUserId: UUID, grantedByUserId: UUID): RoleAssignment {
		val isGuardian = guardianRelationshipRepository.findActiveForHousehold(grantedByUserId, householdId) != null
		val isOrgManager = membershipRepository.findActiveMembership(organizationId, grantedByUserId)?.role
			?.let { it == MembershipRole.OWNER || it == MembershipRole.ADMINISTRATOR } == true
		require(isGuardian || isOrgManager) { "Only a guardian of this household or an organization manager can link an athlete self-login." }
		return roleAssignmentRepository.grant(organizationId, athleteUserId, RoleAssignmentContextType.PARTICIPANT, participantId, ResourceRole.ATHLETE_SELF, grantedByUserId)
	}

	private fun organizationOwnerOrAdmin(organizationId: UUID, currentUser: CurrentUser): Boolean {
		val membership = membershipRepository.findActiveMembership(organizationId, currentUser.userId) ?: return false
		return membership.role == MembershipRole.OWNER || membership.role == MembershipRole.ADMINISTRATOR
	}
}
