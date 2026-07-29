package com.leaguelift.authorization.domain

import com.leaguelift.membership.domain.MembershipRole

/**
 * `resource.action` dot-notation capability strings (DESIGN-DOC.md section 4.2). Every
 * authorization check in the new model — frontend and backend — compares against one of
 * these constants, never a role-name string comparison. Not an exhaustive catalog of
 * every action LeagueLift will ever gate; it covers what [CapabilityRegistry] actually
 * maps roles to today. Extend both together when a new capability is needed.
 */
object Capabilities {

	// Personal (always held by any signed-in user)
	const val PROFILE_VIEW = "profile.view"
	const val PROFILE_MANAGE = "profile.manage"

	// Organization
	const val ORG_MANAGE = "organization.manage"
	const val ORG_MEMBERS_MANAGE = "organization.members.manage"
	const val ORG_BILLING_MANAGE = "organization.billing.manage"
	const val ORG_PAYOUT_MANAGE = "organization.payout.manage"
	const val ORG_REPORT_VIEW = "organization.report.view"
	const val ORG_TEAM_MANAGE = "organization.team.manage"
	const val ORG_TOURNAMENT_MANAGE = "organization.tournament.manage"

	// Team (resource-scoped, see the Coach capability tiers in DESIGN-DOC.md section 4.4)
	const val TEAM_VIEW = "team.view"
	const val TEAM_PAGE_EDIT = "team.page.edit"
	const val TEAM_FUNDRAISING_MANAGE = "team.fundraising.manage"
	const val TEAM_STORE_MANAGE = "team.store.manage"
	const val TEAM_ROSTER_MANAGE = "team.roster.manage"
	const val TEAM_STAFF_MANAGE = "team.staff.manage"
	const val TEAM_FEE_VIEW = "team.fee.view"

	// Tournament (resource-scoped)
	const val TOURNAMENT_VIEW = "tournament.view"
	const val TOURNAMENT_MANAGE = "tournament.manage"
	const val TOURNAMENT_PAGE_EDIT = "tournament.page.edit"
	const val TOURNAMENT_TEAM_MANAGE = "tournament.team.manage"

	// Household (guardian context)
	const val HOUSEHOLD_VIEW = "household.view"
	const val HOUSEHOLD_FEE_VIEW = "household.fee.view"
	const val HOUSEHOLD_FEE_PAY = "household.fee.pay"
	const val HOUSEHOLD_CREDIT_VIEW = "household.credit.view"
	const val HOUSEHOLD_ORDER_VIEW = "household.order.view"
	const val HOUSEHOLD_PROFILE_MANAGE = "household.profile.manage"

	// Athlete (self context — deny-by-default, deliberately excludes anything
	// financial per DESIGN-DOC.md section 10.2's Athlete Dashboard "Never" list)
	const val ATHLETE_PROFILE_VIEW = "athlete.profile.view"
	const val ATHLETE_PROFILE_UPDATE = "athlete.profile.update"
	const val ATHLETE_SCHEDULE_VIEW = "athlete.schedule.view"
	const val ATHLETE_ORDER_VIEW = "athlete.order.view"
	const val ATHLETE_TEAM_VIEW = "athlete.team.view"
	const val ATHLETE_GUARDIAN_VIEW = "athlete.guardian.view"

	// Platform admin
	const val PLATFORM_ORG_VIEW = "platform.organization.view"
	const val PLATFORM_ORG_MANAGE = "platform.organization.manage"
	const val PLATFORM_USER_VIEW = "platform.user.view"
	const val PLATFORM_INTEGRATION_VIEW = "platform.integration.view"
	const val PLATFORM_AUDIT_VIEW = "platform.audit.view"
	const val PLATFORM_FEATURE_FLAG_MANAGE = "platform.feature_flag.manage"
	const val PLATFORM_SUPPORT_IMPERSONATE = "platform.support.impersonate"
}

/** The tiered team/tournament/platform roles [com.leaguelift.authorization.domain.RoleAssignment] rows carry. */
enum class ResourceRole {
	COACH_READ,
	TEAM_EDITOR,
	TEAM_MANAGER,
	TOURNAMENT_VIEWER,
	TOURNAMENT_ADMINISTRATOR,
	PLATFORM_ADMINISTRATOR,
	ATHLETE_SELF,
}

/**
 * Deny-by-default role -> capability-set mapping. This is the one place capability
 * grants are decided; [com.leaguelift.authorization.application.AuthorizationService]
 * only ever asks this object "what can this role do", never encodes rules itself.
 */
object CapabilityRegistry {

	fun personalCapabilities(): Set<String> = setOf(Capabilities.PROFILE_VIEW, Capabilities.PROFILE_MANAGE)

	/**
	 * TEAM_ADMINISTRATOR/TOURNAMENT_ADMINISTRATOR intentionally resolve to no
	 * organization-level capabilities here: under the new model, real team/tournament
	 * access comes from an explicit [ResourceRole] grant in `role_assignment`, not a
	 * blanket org-wide role (ADR-020 closes the "not actually scoped to just Varsity
	 * Soccer" gap `db/seed/V9000` called out). OWNER/ADMINISTRATOR still additionally
	 * *inherit* TEAM_MANAGER/TOURNAMENT_ADMINISTRATOR-tier capabilities for every
	 * team/tournament in their organization — see AuthorizationService, not this map.
	 */
	fun organizationCapabilities(role: MembershipRole): Set<String> = when (role) {
		MembershipRole.OWNER -> setOf(
			Capabilities.ORG_MANAGE, Capabilities.ORG_MEMBERS_MANAGE, Capabilities.ORG_BILLING_MANAGE,
			Capabilities.ORG_PAYOUT_MANAGE, Capabilities.ORG_REPORT_VIEW, Capabilities.ORG_TEAM_MANAGE,
			Capabilities.ORG_TOURNAMENT_MANAGE,
		)
		MembershipRole.ADMINISTRATOR -> setOf(
			Capabilities.ORG_MANAGE, Capabilities.ORG_MEMBERS_MANAGE, Capabilities.ORG_REPORT_VIEW,
			Capabilities.ORG_TEAM_MANAGE, Capabilities.ORG_TOURNAMENT_MANAGE,
		)
		MembershipRole.FINANCE_MANAGER, MembershipRole.VIEWER -> setOf(Capabilities.ORG_REPORT_VIEW)
		MembershipRole.TEAM_ADMINISTRATOR, MembershipRole.TOURNAMENT_ADMINISTRATOR -> emptySet()
	}

	fun teamCapabilities(role: ResourceRole): Set<String> = when (role) {
		ResourceRole.COACH_READ -> setOf(Capabilities.TEAM_VIEW)
		ResourceRole.TEAM_EDITOR -> setOf(
			Capabilities.TEAM_VIEW, Capabilities.TEAM_PAGE_EDIT, Capabilities.TEAM_FUNDRAISING_MANAGE, Capabilities.TEAM_STORE_MANAGE,
		)
		ResourceRole.TEAM_MANAGER -> setOf(
			Capabilities.TEAM_VIEW, Capabilities.TEAM_PAGE_EDIT, Capabilities.TEAM_FUNDRAISING_MANAGE, Capabilities.TEAM_STORE_MANAGE,
			Capabilities.TEAM_ROSTER_MANAGE, Capabilities.TEAM_STAFF_MANAGE, Capabilities.TEAM_FEE_VIEW,
		)
		else -> emptySet()
	}

	fun tournamentCapabilities(role: ResourceRole): Set<String> = when (role) {
		ResourceRole.TOURNAMENT_VIEWER -> setOf(Capabilities.TOURNAMENT_VIEW)
		ResourceRole.TOURNAMENT_ADMINISTRATOR -> setOf(
			Capabilities.TOURNAMENT_VIEW, Capabilities.TOURNAMENT_MANAGE, Capabilities.TOURNAMENT_PAGE_EDIT, Capabilities.TOURNAMENT_TEAM_MANAGE,
		)
		else -> emptySet()
	}

	fun platformCapabilities(role: ResourceRole): Set<String> = when (role) {
		ResourceRole.PLATFORM_ADMINISTRATOR -> setOf(
			Capabilities.PLATFORM_ORG_VIEW, Capabilities.PLATFORM_ORG_MANAGE, Capabilities.PLATFORM_USER_VIEW,
			Capabilities.PLATFORM_INTEGRATION_VIEW, Capabilities.PLATFORM_AUDIT_VIEW,
			Capabilities.PLATFORM_FEATURE_FLAG_MANAGE, Capabilities.PLATFORM_SUPPORT_IMPERSONATE,
		)
		else -> emptySet()
	}

	fun householdCapabilities(): Set<String> = setOf(
		Capabilities.HOUSEHOLD_VIEW, Capabilities.HOUSEHOLD_FEE_VIEW, Capabilities.HOUSEHOLD_FEE_PAY,
		Capabilities.HOUSEHOLD_CREDIT_VIEW, Capabilities.HOUSEHOLD_ORDER_VIEW, Capabilities.HOUSEHOLD_PROFILE_MANAGE,
	)

	/** Deliberately excludes anything fee/payment/report-related — see the class doc. */
	fun athleteSelfCapabilities(): Set<String> = setOf(
		Capabilities.ATHLETE_PROFILE_VIEW, Capabilities.ATHLETE_PROFILE_UPDATE, Capabilities.ATHLETE_SCHEDULE_VIEW,
		Capabilities.ATHLETE_ORDER_VIEW, Capabilities.ATHLETE_TEAM_VIEW, Capabilities.ATHLETE_GUARDIAN_VIEW,
	)
}
