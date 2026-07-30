/**
 * Mirrors `backend/.../authorization/domain/Capabilities.kt` (DESIGN-DOC.md section
 * 4.2, ADR-020). Kept as plain string constants, not derived from the backend at build
 * time — there's no shared-types pipeline yet — so a new backend capability must be
 * added here too before frontend code can gate on it. Frontend code must always
 * compare against one of these, never a role-name string (`user.role === "X"`).
 */
export const Capabilities = {
	PROFILE_VIEW: "profile.view",
	PROFILE_MANAGE: "profile.manage",

	ORG_MANAGE: "organization.manage",
	ORG_MEMBERS_MANAGE: "organization.members.manage",
	ORG_BILLING_MANAGE: "organization.billing.manage",
	ORG_PAYOUT_MANAGE: "organization.payout.manage",
	ORG_REPORT_VIEW: "organization.report.view",
	ORG_TEAM_MANAGE: "organization.team.manage",
	ORG_TOURNAMENT_MANAGE: "organization.tournament.manage",

	TEAM_VIEW: "team.view",
	TEAM_PAGE_EDIT: "team.page.edit",
	TEAM_FUNDRAISING_MANAGE: "team.fundraising.manage",
	TEAM_STORE_MANAGE: "team.store.manage",
	TEAM_ROSTER_MANAGE: "team.roster.manage",
	TEAM_STAFF_MANAGE: "team.staff.manage",
	TEAM_FEE_VIEW: "team.fee.view",

	TOURNAMENT_VIEW: "tournament.view",
	TOURNAMENT_MANAGE: "tournament.manage",
	TOURNAMENT_PAGE_EDIT: "tournament.page.edit",
	TOURNAMENT_TEAM_MANAGE: "tournament.team.manage",

	HOUSEHOLD_VIEW: "household.view",
	HOUSEHOLD_FEE_VIEW: "household.fee.view",
	HOUSEHOLD_FEE_PAY: "household.fee.pay",
	HOUSEHOLD_CREDIT_VIEW: "household.credit.view",
	HOUSEHOLD_ORDER_VIEW: "household.order.view",
	HOUSEHOLD_PROFILE_MANAGE: "household.profile.manage",

	ATHLETE_PROFILE_VIEW: "athlete.profile.view",
	ATHLETE_PROFILE_UPDATE: "athlete.profile.update",
	ATHLETE_SCHEDULE_VIEW: "athlete.schedule.view",
	ATHLETE_ORDER_VIEW: "athlete.order.view",
	ATHLETE_TEAM_VIEW: "athlete.team.view",
	ATHLETE_GUARDIAN_VIEW: "athlete.guardian.view",

	EVENT_READ: "event.read",
	EVENT_CREATE: "event.create",
	EVENT_UPDATE: "event.update",
	EVENT_CANCEL: "event.cancel",
	EVENT_PUBLISH: "event.publish",
	EVENT_RSVP_SELF: "event.rsvp.self",
	EVENT_RSVP_GUARDIAN: "event.rsvp.guardian",
	EVENT_RSVP_READ_TEAM: "event.rsvp.read_team",
	TEAM_EVENT_MANAGE: "team.event.manage",
	TOURNAMENT_EVENT_MANAGE: "tournament.event.manage",
	ORG_EVENT_MANAGE: "organization.event.manage",

	PLATFORM_ORG_VIEW: "platform.organization.view",
	PLATFORM_ORG_MANAGE: "platform.organization.manage",
	PLATFORM_USER_VIEW: "platform.user.view",
	PLATFORM_INTEGRATION_VIEW: "platform.integration.view",
	PLATFORM_INTEGRATION_MANAGE: "platform.integration.manage",
	PLATFORM_AUDIT_VIEW: "platform.audit.view",
	PLATFORM_FEATURE_FLAG_MANAGE: "platform.feature_flag.manage",
	PLATFORM_SUPPORT_IMPERSONATE: "platform.support.impersonate",
} as const;
