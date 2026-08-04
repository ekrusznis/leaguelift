package com.rally26.dashboard.application

import com.rally26.authorization.domain.RoleAssignmentContextType
import com.rally26.authorization.persistence.GuardianRelationshipRepository
import com.rally26.authorization.persistence.RoleAssignmentRepository
import com.rally26.common.web.CurrentUser
import com.rally26.dashboard.domain.DashboardContext
import com.rally26.dashboard.domain.DashboardRole
import com.rally26.household.persistence.HouseholdRepository
import com.rally26.membership.domain.MembershipRole
import com.rally26.membership.persistence.MembershipRepository
import org.springframework.stereotype.Service

/**
 * Resolves which dashboard a signed-in user should land on. This is the real,
 * production role check the frontend routes on (DESIGN-DOC.md section 10.1) — not a
 * dev-only switcher. As of Phase 7/ADR-020 it consults both the original interim
 * lookups (organization_membership, household_adult-by-email — kept for backward
 * compatibility, see ADR-020 consequences) and the new capability model
 * (role_assignment, guardian_relationship), so a user whose *only* access is a
 * team/tournament/platform/athlete-self grant (no organization_membership row at all)
 * still routes correctly.
 *
 * Resolution order (first match wins):
 * 1. Real `role_assignment(PLATFORM)` -> Platform Admin dashboard.
 * 2. Real organization_membership -> Owner, Coach, or Tournament Admin dashboard
 *    depending on role (routing only — the dashboard's own endpoints still enforce
 *    real per-resource capability via AuthorizationService, this is just "which
 *    dashboard do you land on").
 * 3. Real `role_assignment(TEAM)` with no organization_membership -> Coach dashboard
 *    (a team-only coach).
 * 4. Real `role_assignment(TOURNAMENT)` with no organization_membership -> Tournament
 *    Admin dashboard (a tournament-only admin).
 * 5. Real `guardian_relationship`, then the interim household_adult email match ->
 *    Parent dashboard.
 * 6. Otherwise -> Athlete dashboard (explicit `role_assignment(PARTICIPANT)` self-link
 *    if one exists, else the same fallback as before — DESIGN-DOC.md section 4.6: no
 *    general participant-login concept exists, so this remains a deliberate fallback).
 */
@Service
class DashboardContextService(
	private val membershipRepository: MembershipRepository,
	private val householdRepository: HouseholdRepository,
	private val roleAssignmentRepository: RoleAssignmentRepository,
	private val guardianRelationshipRepository: GuardianRelationshipRepository,
) {

	fun resolve(currentUser: CurrentUser): DashboardContext {
		if (currentUser.platformAdministrator) {
			return DashboardContext(DashboardRole.PLATFORM_ADMIN, organizationId = null, householdId = null)
		}

		val membership = membershipRepository.findAnyActiveMembershipForUser(currentUser.userId)
		if (membership != null) {
			val role = when (membership.role) {
				MembershipRole.OWNER, MembershipRole.ADMINISTRATOR, MembershipRole.VIEWER ->
					DashboardRole.OWNER
				MembershipRole.TEAM_ADMINISTRATOR -> DashboardRole.COACH
				MembershipRole.TOURNAMENT_ADMINISTRATOR -> DashboardRole.TOURNAMENT_ADMIN
			}
			val tournamentId = if (role == DashboardRole.TOURNAMENT_ADMIN) {
				roleAssignmentRepository.findActiveForUserAndContext(currentUser.userId, RoleAssignmentContextType.TOURNAMENT)
					.firstOrNull { it.organizationId == membership.organizationId }
					?.resourceId
			} else {
				null
			}
			return DashboardContext(role, organizationId = membership.organizationId, householdId = null, tournamentId = tournamentId)
		}

		val teamAssignment = roleAssignmentRepository.findActiveForUserAndContext(currentUser.userId, RoleAssignmentContextType.TEAM).firstOrNull()
		if (teamAssignment != null) {
			return DashboardContext(DashboardRole.COACH, organizationId = teamAssignment.organizationId, householdId = null)
		}

		val tournamentAssignment = roleAssignmentRepository.findActiveForUserAndContext(currentUser.userId, RoleAssignmentContextType.TOURNAMENT).firstOrNull()
		if (tournamentAssignment != null) {
			return DashboardContext(
				DashboardRole.TOURNAMENT_ADMIN,
				organizationId = tournamentAssignment.organizationId,
				householdId = null,
				tournamentId = tournamentAssignment.resourceId,
			)
		}

		val guardianRelationship = guardianRelationshipRepository.findActiveForUser(currentUser.userId).firstOrNull()
		if (guardianRelationship != null) {
			return DashboardContext(DashboardRole.PARENT, organizationId = guardianRelationship.organizationId, householdId = guardianRelationship.householdId)
		}

		val adult = householdRepository.findActiveAdultByEmail(currentUser.email)
		if (adult != null) {
			return DashboardContext(DashboardRole.PARENT, organizationId = adult.organizationId, householdId = adult.householdId)
		}

		return DashboardContext(DashboardRole.ATHLETE, organizationId = null, householdId = null)
	}
}
