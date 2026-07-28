package com.leaguelift.dashboard.application

import com.leaguelift.common.web.CurrentUser
import com.leaguelift.dashboard.domain.DashboardContext
import com.leaguelift.dashboard.domain.DashboardRole
import com.leaguelift.household.persistence.HouseholdRepository
import com.leaguelift.membership.domain.MembershipRole
import com.leaguelift.membership.persistence.MembershipRepository
import org.springframework.stereotype.Service

/**
 * Resolves which dashboard a signed-in user should land on. This is the real,
 * production role check the frontend routes on (DESIGN-DOC.md section 10.1) — not a
 * dev-only switcher — but it is deliberately a simple three-step lookup against
 * today's schema, not the full capability/context model in DESIGN-DOC.md section 4.2
 * (no `role_assignment` or `guardian_relationship` tables exist yet).
 *
 * Resolution order:
 * 1. Real organization_membership -> OWNER or COACH dashboard.
 * 2. Real household_adult matched by email (an interim stand-in for the not-yet-built
 *    guardian_relationship FK — see [HouseholdRepository.findActiveAdultByEmail]) ->
 *    PARENT dashboard.
 * 3. Otherwise -> ATHLETE dashboard. There is no product-sanctioned way to resolve a
 *    real participant link for a login (DESIGN-DOC.md section 4.6: no participant
 *    login concept exists), so this is a deliberate fallback rather than an attempt to
 *    fuzzy-match participant records by name.
 */
@Service
class DashboardContextService(
	private val membershipRepository: MembershipRepository,
	private val householdRepository: HouseholdRepository,
) {

	fun resolve(currentUser: CurrentUser): DashboardContext {
		val membership = membershipRepository.findAnyActiveMembershipForUser(currentUser.userId)
		if (membership != null) {
			val role = when (membership.role) {
				MembershipRole.OWNER, MembershipRole.ADMINISTRATOR, MembershipRole.FINANCE_MANAGER, MembershipRole.VIEWER ->
					DashboardRole.OWNER
				MembershipRole.TEAM_ADMINISTRATOR, MembershipRole.TOURNAMENT_ADMINISTRATOR ->
					DashboardRole.COACH
			}
			return DashboardContext(role, organizationId = membership.organizationId, householdId = null)
		}

		val adult = householdRepository.findActiveAdultByEmail(currentUser.email)
		if (adult != null) {
			return DashboardContext(DashboardRole.PARENT, organizationId = adult.organizationId, householdId = adult.householdId)
		}

		return DashboardContext(DashboardRole.ATHLETE, organizationId = null, householdId = null)
	}
}
