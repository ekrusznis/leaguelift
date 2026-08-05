package com.rally26.authorization.domain

import com.rally26.membership.domain.MembershipRole

/**
 * `resource.action` dot-notation capability strings (DESIGN-DOC.md section 4.2). Every
 * authorization check in the new model — frontend and backend — compares against one of
 * these constants, never a role-name string comparison. Not an exhaustive catalog of
 * every action Rally26 will ever gate; it covers what [CapabilityRegistry] actually
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
    const val ORG_COMMUNICATION_MANAGE = "organization.communication.manage"

    // Team (resource-scoped, see the Coach capability tiers in DESIGN-DOC.md section 4.4)
    const val TEAM_VIEW = "team.view"
    const val TEAM_PAGE_EDIT = "team.page.edit"
    const val TEAM_FUNDRAISING_MANAGE = "team.fundraising.manage"
    const val TEAM_STORE_MANAGE = "team.store.manage"
    /** Swag Shop (DESIGN-DOC.md section 13): a coach placing a personalized order on behalf of a roster athlete — deliberately separate from TEAM_STORE_MANAGE (catalog setup) since ordering is a lower, more routine bar. */
    const val TEAM_ORDER_CREATE = "team.order.create"
    const val TEAM_ROSTER_MANAGE = "team.roster.manage"
    const val TEAM_STAFF_MANAGE = "team.staff.manage"
    const val TEAM_FEE_VIEW = "team.fee.view"
    const val TEAM_COMMUNICATION_MANAGE = "team.communication.manage"

    // Tournament (resource-scoped)
    const val TOURNAMENT_VIEW = "tournament.view"
    const val TOURNAMENT_MANAGE = "tournament.manage"
    const val TOURNAMENT_PAGE_EDIT = "tournament.page.edit"
    const val TOURNAMENT_TEAM_MANAGE = "tournament.team.manage"
    const val TOURNAMENT_COMMUNICATION_MANAGE = "tournament.communication.manage"

    // Household (guardian context)
    const val HOUSEHOLD_VIEW = "household.view"
    const val HOUSEHOLD_FEE_VIEW = "household.fee.view"
    const val HOUSEHOLD_FEE_PAY = "household.fee.pay"
    const val HOUSEHOLD_CREDIT_VIEW = "household.credit.view"
    const val HOUSEHOLD_ORDER_VIEW = "household.order.view"
    /** Swag Shop (DESIGN-DOC.md section 13): a guardian placing a personalized order for their own household's participant. Checked alongside AuthorizationService.hasGuardianRelationship, not the broader hasHouseholdCapability "any active org member" branch — see EventRsvpService.resolveSource for the same pattern. */
    const val HOUSEHOLD_ORDER_CREATE = "household.order.create"
    const val HOUSEHOLD_PROFILE_MANAGE = "household.profile.manage"

    // Athlete (self context — deny-by-default, deliberately excludes anything
    // financial per DESIGN-DOC.md section 10.2's Athlete Dashboard "Never" list)
    const val ATHLETE_PROFILE_VIEW = "athlete.profile.view"
    const val ATHLETE_PROFILE_UPDATE = "athlete.profile.update"
    const val ATHLETE_SCHEDULE_VIEW = "athlete.schedule.view"
    const val ATHLETE_ORDER_VIEW = "athlete.order.view"
    const val ATHLETE_TEAM_VIEW = "athlete.team.view"
    const val ATHLETE_GUARDIAN_VIEW = "athlete.guardian.view"

    // Events/RSVP (Phase 10, ADR-026 — DESIGN-DOC.md section 14.1A's exact capability
    // names). EVENT_READ/EVENT_RSVP_GUARDIAN are granted here for context-listing/
    // frontend-nav purposes only — real per-event guardian/athlete access is resolved
    // through the guardian-relationship/participant-team chain in EventService, the
    // same way ParentDashboardService bypasses the generic household-capability check
    // for guardian-scoped actions (see AuthorizationService.hasHouseholdCapability's
    // "any active org member" branch, which is deliberately too broad for a
    // guardian-only action like submitting an RSVP).
    const val EVENT_READ = "event.read"
    const val EVENT_CREATE = "event.create"
    const val EVENT_UPDATE = "event.update"
    const val EVENT_CANCEL = "event.cancel"
    const val EVENT_PUBLISH = "event.publish"
    const val EVENT_RSVP_SELF = "event.rsvp.self"
    const val EVENT_RSVP_GUARDIAN = "event.rsvp.guardian"
    const val EVENT_RSVP_READ_TEAM = "event.rsvp.read_team"
    const val TEAM_EVENT_MANAGE = "team.event.manage"
    const val TOURNAMENT_EVENT_MANAGE = "tournament.event.manage"
    const val ORG_EVENT_MANAGE = "organization.event.manage"

    // Platform admin
    const val PLATFORM_ORG_VIEW = "platform.organization.view"
    const val PLATFORM_ORG_MANAGE = "platform.organization.manage"
    const val PLATFORM_USER_VIEW = "platform.user.view"
    const val PLATFORM_USER_MANAGE = "platform.user.manage"
    const val PLATFORM_INTEGRATION_VIEW = "platform.integration.view"
    const val PLATFORM_INTEGRATION_MANAGE = "platform.integration.manage"
    const val PLATFORM_AUDIT_VIEW = "platform.audit.view"
    const val PLATFORM_FEATURE_FLAG_MANAGE = "platform.feature_flag.manage"
    const val PLATFORM_SUPPORT_ACCESS = "platform.support.access"
    const val PLATFORM_HELP_MANAGE = "platform.help.manage"
    const val PLATFORM_SUPPORT_CASE_MANAGE = "platform.support_case.manage"
    const val PLATFORM_SUPPORT_IMPERSONATE = "platform.support.impersonate"
}

/** The tiered team/tournament/platform roles [com.rally26.authorization.domain.RoleAssignment] rows carry. */
enum class ResourceRole {
    COACH_READ,
    TEAM_EDITOR,
    TEAM_MANAGER,
    TOURNAMENT_VIEWER,
    TOURNAMENT_ADMINISTRATOR,
    PLATFORM_ADMIN,
    ATHLETE_SELF,
}

/**
 * Deny-by-default role -> capability-set mapping. This is the one place capability
 * grants are decided; [com.rally26.authorization.application.AuthorizationService]
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
    fun organizationCapabilities(role: MembershipRole): Set<String> =
        when (role) {
            MembershipRole.OWNER ->
                setOf(
                    Capabilities.ORG_MANAGE,
                    Capabilities.ORG_MEMBERS_MANAGE,
                    Capabilities.ORG_BILLING_MANAGE,
                    Capabilities.ORG_PAYOUT_MANAGE,
                    Capabilities.ORG_REPORT_VIEW,
                    Capabilities.ORG_TEAM_MANAGE,
                    Capabilities.ORG_TOURNAMENT_MANAGE,
                    Capabilities.ORG_COMMUNICATION_MANAGE,
                    Capabilities.ORG_EVENT_MANAGE,
                    Capabilities.EVENT_READ,
                )
            MembershipRole.ADMINISTRATOR ->
                setOf(
                    Capabilities.ORG_MANAGE,
                    Capabilities.ORG_MEMBERS_MANAGE,
                    Capabilities.ORG_REPORT_VIEW,
                    Capabilities.ORG_TEAM_MANAGE,
                    Capabilities.ORG_TOURNAMENT_MANAGE,
                    Capabilities.ORG_COMMUNICATION_MANAGE,
                    Capabilities.ORG_EVENT_MANAGE,
                    Capabilities.EVENT_READ,
                )
            MembershipRole.VIEWER -> setOf(Capabilities.ORG_REPORT_VIEW)
            MembershipRole.TEAM_ADMINISTRATOR, MembershipRole.TOURNAMENT_ADMINISTRATOR -> emptySet()
        }

    fun teamCapabilities(role: ResourceRole): Set<String> =
        when (role) {
            ResourceRole.COACH_READ -> setOf(Capabilities.TEAM_VIEW, Capabilities.EVENT_READ)
            ResourceRole.TEAM_EDITOR ->
                setOf(
                    Capabilities.TEAM_VIEW,
                    Capabilities.TEAM_PAGE_EDIT,
                    Capabilities.TEAM_FUNDRAISING_MANAGE,
                    Capabilities.TEAM_STORE_MANAGE,
                    Capabilities.TEAM_ORDER_CREATE,
                    Capabilities.EVENT_READ,
                    Capabilities.EVENT_CREATE,
                    Capabilities.EVENT_UPDATE,
                    Capabilities.EVENT_PUBLISH,
                    Capabilities.EVENT_RSVP_READ_TEAM,
                    Capabilities.TEAM_EVENT_MANAGE,
                    Capabilities.TEAM_COMMUNICATION_MANAGE,
                )
            ResourceRole.TEAM_MANAGER ->
                setOf(
                    Capabilities.TEAM_VIEW,
                    Capabilities.TEAM_PAGE_EDIT,
                    Capabilities.TEAM_FUNDRAISING_MANAGE,
                    Capabilities.TEAM_STORE_MANAGE,
                    Capabilities.TEAM_ORDER_CREATE,
                    Capabilities.TEAM_ROSTER_MANAGE,
                    Capabilities.TEAM_STAFF_MANAGE,
                    Capabilities.TEAM_FEE_VIEW,
                    Capabilities.EVENT_READ,
                    Capabilities.EVENT_CREATE,
                    Capabilities.EVENT_UPDATE,
                    Capabilities.EVENT_CANCEL,
                    Capabilities.EVENT_PUBLISH,
                    Capabilities.EVENT_RSVP_READ_TEAM,
                    Capabilities.TEAM_EVENT_MANAGE,
                    Capabilities.TEAM_COMMUNICATION_MANAGE,
                )
            else -> emptySet()
        }

    fun tournamentCapabilities(role: ResourceRole): Set<String> =
        when (role) {
            ResourceRole.TOURNAMENT_VIEWER -> setOf(Capabilities.TOURNAMENT_VIEW, Capabilities.EVENT_READ)
            ResourceRole.TOURNAMENT_ADMINISTRATOR ->
                setOf(
                    Capabilities.TOURNAMENT_VIEW,
                    Capabilities.TOURNAMENT_MANAGE,
                    Capabilities.TOURNAMENT_PAGE_EDIT,
                    Capabilities.TOURNAMENT_TEAM_MANAGE,
                    Capabilities.EVENT_READ,
                    Capabilities.EVENT_CREATE,
                    Capabilities.EVENT_UPDATE,
                    Capabilities.EVENT_CANCEL,
                    Capabilities.EVENT_PUBLISH,
                    Capabilities.EVENT_RSVP_READ_TEAM,
                    Capabilities.TOURNAMENT_EVENT_MANAGE,
                    Capabilities.TOURNAMENT_COMMUNICATION_MANAGE,
                )
            else -> emptySet()
        }

    fun platformCapabilities(role: ResourceRole): Set<String> =
        when (role) {
            ResourceRole.PLATFORM_ADMIN ->
                setOf(
                    Capabilities.PLATFORM_ORG_VIEW,
                    Capabilities.PLATFORM_ORG_MANAGE,
                    Capabilities.PLATFORM_USER_VIEW,
                    Capabilities.PLATFORM_USER_MANAGE,
                    Capabilities.PLATFORM_INTEGRATION_VIEW,
                    Capabilities.PLATFORM_INTEGRATION_MANAGE,
                    Capabilities.PLATFORM_AUDIT_VIEW,
                    Capabilities.PLATFORM_FEATURE_FLAG_MANAGE,
                    Capabilities.PLATFORM_SUPPORT_ACCESS,
                    Capabilities.PLATFORM_HELP_MANAGE,
                    Capabilities.PLATFORM_SUPPORT_CASE_MANAGE,
                )
            else -> emptySet()
        }

    /** The full household capability set — what a real guardian holds for their own household, and what OWNER/ADMINISTRATOR hold org-wide. */
    fun householdCapabilities(): Set<String> =
        setOf(
            Capabilities.HOUSEHOLD_VIEW,
            Capabilities.HOUSEHOLD_FEE_VIEW,
            Capabilities.HOUSEHOLD_FEE_PAY,
            Capabilities.HOUSEHOLD_CREDIT_VIEW,
            Capabilities.HOUSEHOLD_ORDER_VIEW,
            Capabilities.HOUSEHOLD_ORDER_CREATE,
            Capabilities.HOUSEHOLD_PROFILE_MANAGE,
            Capabilities.EVENT_READ,
            Capabilities.EVENT_RSVP_GUARDIAN,
        )

    /**
     * What an org staff member (no guardian relationship required) holds for ANY
     * household in the organization — narrower than [householdCapabilities] for every
     * role except OWNER/ADMINISTRATOR. Security-review finding (2026-08): this used to
     * be "any active org member gets every household capability", which let a VIEWER
     * trigger fee payments and edit a family's profile, and let TEAM_ADMINISTRATOR/
     * TOURNAMENT_ADMINISTRATOR — roles that are supposed to be resource-scoped per
     * ADR-020, see [organizationCapabilities]'s doc comment — reach every household in
     * the org. EVENT_READ stays broad for every role (schedule visibility is low-risk
     * and [com.rally26.event.application.EventService] already documents relying on
     * that breadth); HOUSEHOLD_PROFILE_MANAGE is never granted blanket — editing a
     * family's profile requires either OWNER/ADMINISTRATOR or an actual guardian
     * relationship (the AuthorizationService.hasHouseholdCapability fallback).
     */
    fun householdCapabilitiesForOrgRole(role: MembershipRole): Set<String> =
        when (role) {
            MembershipRole.OWNER, MembershipRole.ADMINISTRATOR -> householdCapabilities()
            MembershipRole.VIEWER ->
                setOf(
                    Capabilities.HOUSEHOLD_VIEW,
                    Capabilities.HOUSEHOLD_FEE_VIEW,
                    Capabilities.HOUSEHOLD_FEE_PAY,
                    Capabilities.EVENT_READ,
                )
            MembershipRole.TEAM_ADMINISTRATOR, MembershipRole.TOURNAMENT_ADMINISTRATOR -> setOf(Capabilities.EVENT_READ)
        }

    /** Deliberately excludes anything fee/payment/report-related — see the class doc. */
    fun athleteSelfCapabilities(): Set<String> =
        setOf(
            Capabilities.ATHLETE_PROFILE_VIEW,
            Capabilities.ATHLETE_PROFILE_UPDATE,
            Capabilities.ATHLETE_SCHEDULE_VIEW,
            Capabilities.ATHLETE_ORDER_VIEW,
            Capabilities.ATHLETE_TEAM_VIEW,
            Capabilities.ATHLETE_GUARDIAN_VIEW,
            Capabilities.EVENT_RSVP_SELF,
        )
}
