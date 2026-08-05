import type { ReactNode } from "react";
import type { ContextType } from "../../authorization/types";
import { Capabilities } from "../../authorization/capabilityConstants";
import { appPaths, type HouseholdSection, type OrganizationSection } from "../../routes/appPaths";
import {
	BuildingIcon,
	CalendarIcon,
	ChartIcon,
	ClipboardCheckIcon,
	DollarIcon,
	FileTextIcon,
	HeartHandshakeIcon,
	HelpIcon,
	HomeIcon,
	LayoutIcon,
	MegaphoneIcon,
	MessageSquareIcon,
	PackageIcon,
	SettingsIcon,
	ShieldIcon,
	ShirtIcon,
	TrophyIcon,
	UserIcon,
	UserPlusIcon,
	UsersIcon,
} from "../icons";

export type NavDestination =
	| { type: "dashboard"; hash?: string }
	| { type: "action-center" }
	| { type: "announcements" }
	| { type: "personal-integrations" }
	| { type: "organizations" }
	| { type: "platform-organizations" }
	| { type: "platform-users" }
	| { type: "platform-operations" }
	| { type: "platform-audit" }
	| { type: "platform-support-sessions" }
	| { type: "platform-help-articles" }
	| { type: "platform-support-cases" }
	| { type: "organization"; section: OrganizationSection }
	| { type: "household"; section: HouseholdSection }
	| { type: "team-events" }
	| { type: "tournament-events" }
	| { type: "participant-events" }
	| { type: "swag-shop-order" }
	| { type: "platform-reports" };

export interface NavRegistryItem {
	id: string;
	label: string;
	icon: ReactNode;
	/** Which context type(s) this item applies to. */
	contextTypes: ContextType[];
	/** Any one of these capabilities is enough to show the item. */
	requiredCapabilities?: string[];
	/** A concrete route or dashboard anchor. Visible navigation is never decorative. */
	destination: NavDestination;
}

export interface NavRouteContext {
	organizationId?: string | null;
	householdId?: string | null;
	teamId?: string | null;
	tournamentId?: string | null;
	participantId?: string | null;
}

export interface ResolvedNavRegistryItem extends NavRegistryItem {
	to: string;
}

/**
 * Deliberately small role-specific navigation. Features without a usable route are
 * omitted instead of appearing as inert labels. Detailed settings remain grouped on
 * the existing organization/household pages rather than filling the sidebar with
 * one item per database table.
 */
export const NAV_REGISTRY: NavRegistryItem[] = [
	// Owner/Director (ORGANIZATION context)
	{ id: "owner.overview", label: "Overview", icon: <HomeIcon className="size-5" />, contextTypes: ["ORGANIZATION"], destination: { type: "dashboard" } },
	{ id: "owner.action-center", label: "Action Center", icon: <ClipboardCheckIcon className="size-5" />, contextTypes: ["ORGANIZATION"], destination: { type: "action-center" } },
	{ id: "owner.announcements", label: "Announcements", icon: <MegaphoneIcon className="size-5" />, contextTypes: ["ORGANIZATION"], destination: { type: "announcements" } },
	{ id: "owner.integrations", label: "My Integrations", icon: <CalendarIcon className="size-5" />, contextTypes: ["ORGANIZATION"], destination: { type: "personal-integrations" } },
	{ id: "owner.organization", label: "Organization", icon: <BuildingIcon className="size-5" />, contextTypes: ["ORGANIZATION"], requiredCapabilities: [Capabilities.ORG_MANAGE], destination: { type: "organization", section: "overview" } },
	{ id: "owner.teams", label: "Teams", icon: <UsersIcon className="size-5" />, contextTypes: ["ORGANIZATION"], requiredCapabilities: [Capabilities.ORG_TEAM_MANAGE, Capabilities.ORG_MANAGE], destination: { type: "organization", section: "teams" } },
	{ id: "owner.tournaments", label: "Tournaments", icon: <TrophyIcon className="size-5" />, contextTypes: ["ORGANIZATION"], requiredCapabilities: [Capabilities.ORG_TOURNAMENT_MANAGE, Capabilities.ORG_MANAGE], destination: { type: "organization", section: "tournaments" } },
	{ id: "owner.households", label: "Households & Athletes", icon: <UsersIcon className="size-5" />, contextTypes: ["ORGANIZATION"], requiredCapabilities: [Capabilities.ORG_MANAGE], destination: { type: "organization", section: "households" } },
	{ id: "owner.events", label: "Events", icon: <CalendarIcon className="size-5" />, contextTypes: ["ORGANIZATION"], requiredCapabilities: [Capabilities.EVENT_READ, Capabilities.ORG_EVENT_MANAGE], destination: { type: "organization", section: "events" } },
	{ id: "owner.fees", label: "Fees & Payments", icon: <DollarIcon className="size-5" />, contextTypes: ["ORGANIZATION"], requiredCapabilities: [Capabilities.ORG_REPORT_VIEW, Capabilities.ORG_MANAGE], destination: { type: "organization", section: "fees" } },
	{ id: "owner.fundraising", label: "Fundraising", icon: <HeartHandshakeIcon className="size-5" />, contextTypes: ["ORGANIZATION"], requiredCapabilities: [Capabilities.ORG_MANAGE], destination: { type: "organization", section: "fundraising" } },
	{ id: "owner.swag-shop", label: "Swag Shop", icon: <PackageIcon className="size-5" />, contextTypes: ["ORGANIZATION"], requiredCapabilities: [Capabilities.ORG_MANAGE], destination: { type: "organization", section: "swag-shop" } },
	{ id: "owner.sponsorships", label: "Sponsorships", icon: <MegaphoneIcon className="size-5" />, contextTypes: ["ORGANIZATION"], requiredCapabilities: [Capabilities.ORG_MANAGE], destination: { type: "organization", section: "sponsorships" } },
	{ id: "owner.reports", label: "Reports", icon: <ChartIcon className="size-5" />, contextTypes: ["ORGANIZATION"], requiredCapabilities: [Capabilities.ORG_REPORT_VIEW], destination: { type: "organization", section: "reports" } },
	{ id: "owner.documents", label: "Documents", icon: <FileTextIcon className="size-5" />, contextTypes: ["ORGANIZATION"], requiredCapabilities: [Capabilities.ORG_MANAGE], destination: { type: "organization", section: "documents" } },
	{ id: "owner.members", label: "Members", icon: <UserPlusIcon className="size-5" />, contextTypes: ["ORGANIZATION"], requiredCapabilities: [Capabilities.ORG_MEMBERS_MANAGE], destination: { type: "organization", section: "members" } },
	{ id: "owner.organization-integrations", label: "Organization Integrations", icon: <SettingsIcon className="size-5" />, contextTypes: ["ORGANIZATION"], requiredCapabilities: [Capabilities.ORG_MANAGE], destination: { type: "organization", section: "integrations" } },
	{ id: "owner.settings", label: "Settings", icon: <SettingsIcon className="size-5" />, contextTypes: ["ORGANIZATION"], requiredCapabilities: [Capabilities.ORG_MANAGE, Capabilities.ORG_BILLING_MANAGE, Capabilities.ORG_PAYOUT_MANAGE], destination: { type: "organization", section: "settings" } },

	// Parent/Guardian (HOUSEHOLD context)
	{ id: "parent.overview", label: "Overview", icon: <HomeIcon className="size-5" />, contextTypes: ["HOUSEHOLD"], destination: { type: "dashboard" } },
	{ id: "parent.action-center", label: "Action Center", icon: <ClipboardCheckIcon className="size-5" />, contextTypes: ["HOUSEHOLD"], destination: { type: "action-center" } },
	{ id: "parent.announcements", label: "Announcements", icon: <MegaphoneIcon className="size-5" />, contextTypes: ["HOUSEHOLD"], destination: { type: "announcements" } },
	{ id: "parent.integrations", label: "My Integrations", icon: <CalendarIcon className="size-5" />, contextTypes: ["HOUSEHOLD"], destination: { type: "personal-integrations" } },
	{ id: "parent.athletes", label: "My Athletes", icon: <UsersIcon className="size-5" />, contextTypes: ["HOUSEHOLD"], destination: { type: "household", section: "participants" } },
	{ id: "parent.schedule", label: "Family Schedule", icon: <CalendarIcon className="size-5" />, contextTypes: ["HOUSEHOLD"], requiredCapabilities: [Capabilities.EVENT_READ], destination: { type: "household", section: "events" } },
	{ id: "parent.fees", label: "Fees & Payments", icon: <DollarIcon className="size-5" />, contextTypes: ["HOUSEHOLD"], requiredCapabilities: [Capabilities.HOUSEHOLD_FEE_VIEW], destination: { type: "household", section: "fees" } },
	{ id: "parent.fundraising", label: "Fundraising", icon: <HeartHandshakeIcon className="size-5" />, contextTypes: ["HOUSEHOLD"], destination: { type: "dashboard", hash: "parent-fundraising" } },
	{ id: "parent.swag-shop", label: "Swag Shop", icon: <ShirtIcon className="size-5" />, contextTypes: ["HOUSEHOLD"], requiredCapabilities: [Capabilities.HOUSEHOLD_ORDER_CREATE], destination: { type: "swag-shop-order" } },
	{ id: "parent.documents", label: "Documents", icon: <FileTextIcon className="size-5" />, contextTypes: ["HOUSEHOLD"], destination: { type: "household", section: "documents" } },
	{ id: "parent.profile", label: "Household Profile", icon: <UserIcon className="size-5" />, contextTypes: ["HOUSEHOLD"], requiredCapabilities: [Capabilities.HOUSEHOLD_VIEW], destination: { type: "household", section: "profile" } },

	// Athlete (ATHLETE context)
	{ id: "athlete.overview", label: "Overview", icon: <HomeIcon className="size-5" />, contextTypes: ["ATHLETE"], destination: { type: "dashboard" } },
	{ id: "athlete.action-center", label: "Action Center", icon: <ClipboardCheckIcon className="size-5" />, contextTypes: ["ATHLETE"], destination: { type: "action-center" } },
	{ id: "athlete.announcements", label: "Announcements", icon: <MegaphoneIcon className="size-5" />, contextTypes: ["ATHLETE"], destination: { type: "announcements" } },
	{ id: "athlete.integrations", label: "My Integrations", icon: <CalendarIcon className="size-5" />, contextTypes: ["ATHLETE"], destination: { type: "personal-integrations" } },
	{ id: "athlete.schedule", label: "Schedule", icon: <CalendarIcon className="size-5" />, contextTypes: ["ATHLETE"], requiredCapabilities: [Capabilities.ATHLETE_SCHEDULE_VIEW, Capabilities.EVENT_RSVP_SELF], destination: { type: "participant-events" } },
	{ id: "athlete.teams", label: "My Teams", icon: <UsersIcon className="size-5" />, contextTypes: ["ATHLETE"], requiredCapabilities: [Capabilities.ATHLETE_TEAM_VIEW], destination: { type: "dashboard", hash: "athlete-teams" } },
	{ id: "athlete.profile", label: "Profile & Guardians", icon: <ShieldIcon className="size-5" />, contextTypes: ["ATHLETE"], requiredCapabilities: [Capabilities.ATHLETE_PROFILE_VIEW, Capabilities.ATHLETE_GUARDIAN_VIEW], destination: { type: "dashboard", hash: "athlete-profile" } },

	// Coach (TEAM context)
	{ id: "coach.overview", label: "Overview", icon: <HomeIcon className="size-5" />, contextTypes: ["TEAM"], destination: { type: "dashboard" } },
	{ id: "coach.action-center", label: "Action Center", icon: <ClipboardCheckIcon className="size-5" />, contextTypes: ["TEAM"], destination: { type: "action-center" } },
	{ id: "coach.announcements", label: "Announcements", icon: <MegaphoneIcon className="size-5" />, contextTypes: ["TEAM"], destination: { type: "announcements" } },
	{ id: "coach.integrations", label: "My Integrations", icon: <CalendarIcon className="size-5" />, contextTypes: ["TEAM"], destination: { type: "personal-integrations" } },
	{ id: "coach.my-teams", label: "My Teams", icon: <UsersIcon className="size-5" />, contextTypes: ["TEAM"], requiredCapabilities: [Capabilities.TEAM_VIEW], destination: { type: "dashboard", hash: "coach-teams" } },
	{ id: "coach.schedule", label: "Schedule", icon: <CalendarIcon className="size-5" />, contextTypes: ["TEAM"], requiredCapabilities: [Capabilities.EVENT_READ], destination: { type: "team-events" } },
	{ id: "coach.roster", label: "Roster Summary", icon: <UserIcon className="size-5" />, contextTypes: ["TEAM"], requiredCapabilities: [Capabilities.TEAM_VIEW], destination: { type: "dashboard", hash: "coach-roster" } },
	{ id: "coach.team-page", label: "Team Page", icon: <LayoutIcon className="size-5" />, contextTypes: ["TEAM"], requiredCapabilities: [Capabilities.TEAM_VIEW, Capabilities.TEAM_PAGE_EDIT], destination: { type: "dashboard", hash: "coach-team-page" } },
	{ id: "coach.fundraising", label: "Fundraising", icon: <HeartHandshakeIcon className="size-5" />, contextTypes: ["TEAM"], requiredCapabilities: [Capabilities.TEAM_VIEW, Capabilities.TEAM_FUNDRAISING_MANAGE], destination: { type: "dashboard", hash: "coach-fundraising" } },
	{ id: "coach.swag-shop", label: "Swag Shop", icon: <ShirtIcon className="size-5" />, contextTypes: ["TEAM"], requiredCapabilities: [Capabilities.TEAM_ORDER_CREATE], destination: { type: "swag-shop-order" } },

	// Tournament Admin (TOURNAMENT context)
	{ id: "tournament.overview", label: "Overview", icon: <HomeIcon className="size-5" />, contextTypes: ["TOURNAMENT"], destination: { type: "dashboard" } },
	{ id: "tournament.action-center", label: "Action Center", icon: <ClipboardCheckIcon className="size-5" />, contextTypes: ["TOURNAMENT"], destination: { type: "action-center" } },
	{ id: "tournament.announcements", label: "Announcements", icon: <MegaphoneIcon className="size-5" />, contextTypes: ["TOURNAMENT"], destination: { type: "announcements" } },
	{ id: "tournament.integrations", label: "My Integrations", icon: <CalendarIcon className="size-5" />, contextTypes: ["TOURNAMENT"], destination: { type: "personal-integrations" } },
	{ id: "tournament.events", label: "Schedule & Events", icon: <CalendarIcon className="size-5" />, contextTypes: ["TOURNAMENT"], requiredCapabilities: [Capabilities.EVENT_READ], destination: { type: "tournament-events" } },
	{ id: "tournament.page", label: "Tournament Page", icon: <LayoutIcon className="size-5" />, contextTypes: ["TOURNAMENT"], requiredCapabilities: [Capabilities.TOURNAMENT_VIEW, Capabilities.TOURNAMENT_PAGE_EDIT], destination: { type: "dashboard", hash: "tournament-page" } },
	{ id: "tournament.settings", label: "Settings", icon: <SettingsIcon className="size-5" />, contextTypes: ["TOURNAMENT"], requiredCapabilities: [Capabilities.TOURNAMENT_MANAGE], destination: { type: "dashboard", hash: "tournament-summary" } },

	// Platform Admin (PLATFORM_ADMIN context)
	{ id: "platform.overview", label: "Overview", icon: <HomeIcon className="size-5" />, contextTypes: ["PLATFORM_ADMIN"], destination: { type: "dashboard" } },
	{ id: "platform.action-center", label: "Action Center", icon: <ClipboardCheckIcon className="size-5" />, contextTypes: ["PLATFORM_ADMIN"], destination: { type: "action-center" } },
	{ id: "platform.announcements", label: "Announcements", icon: <MegaphoneIcon className="size-5" />, contextTypes: ["PLATFORM_ADMIN"], destination: { type: "announcements" } },
	{ id: "platform.integrations", label: "My Integrations", icon: <CalendarIcon className="size-5" />, contextTypes: ["PLATFORM_ADMIN"], destination: { type: "personal-integrations" } },
	{ id: "platform.organizations", label: "Organizations", icon: <BuildingIcon className="size-5" />, contextTypes: ["PLATFORM_ADMIN"], requiredCapabilities: [Capabilities.PLATFORM_ORG_VIEW], destination: { type: "platform-organizations" } },
	{ id: "platform.users", label: "Users", icon: <UserIcon className="size-5" />, contextTypes: ["PLATFORM_ADMIN"], requiredCapabilities: [Capabilities.PLATFORM_USER_VIEW], destination: { type: "platform-users" } },
	{ id: "platform.operations", label: "Integration Operations", icon: <ShieldIcon className="size-5" />, contextTypes: ["PLATFORM_ADMIN"], requiredCapabilities: [Capabilities.PLATFORM_INTEGRATION_VIEW], destination: { type: "platform-operations" } },
	{ id: "platform.reports", label: "Reports", icon: <ChartIcon className="size-5" />, contextTypes: ["PLATFORM_ADMIN"], requiredCapabilities: [Capabilities.PLATFORM_AUDIT_VIEW], destination: { type: "platform-reports" } },
	{ id: "platform.audit", label: "Audit", icon: <FileTextIcon className="size-5" />, contextTypes: ["PLATFORM_ADMIN"], requiredCapabilities: [Capabilities.PLATFORM_AUDIT_VIEW], destination: { type: "platform-audit" } },
	{ id: "platform.support-sessions", label: "Support Sessions", icon: <ShieldIcon className="size-5" />, contextTypes: ["PLATFORM_ADMIN"], requiredCapabilities: [Capabilities.PLATFORM_SUPPORT_ACCESS], destination: { type: "platform-support-sessions" } },
	{ id: "platform.help-articles", label: "Help Articles", icon: <HelpIcon className="size-5" />, contextTypes: ["PLATFORM_ADMIN"], requiredCapabilities: [Capabilities.PLATFORM_HELP_MANAGE], destination: { type: "platform-help-articles" } },
	{ id: "platform.support-cases", label: "Support Cases", icon: <MessageSquareIcon className="size-5" />, contextTypes: ["PLATFORM_ADMIN"], requiredCapabilities: [Capabilities.PLATFORM_SUPPORT_CASE_MANAGE], destination: { type: "platform-support-cases" } },
];

function resolveDestination(destination: NavDestination, context: NavRouteContext): string | null {
	switch (destination.type) {
		case "dashboard":
			return appPaths.dashboard(destination.hash);
		case "action-center":
			return appPaths.actionCenter();
		case "announcements":
			return appPaths.announcements();
		case "personal-integrations":
			return appPaths.personalIntegrations();
		case "organizations":
			return appPaths.organizations();
		case "platform-organizations":
			return appPaths.platformOrganizations();
		case "platform-users":
			return appPaths.platformUsers();
		case "platform-operations":
			return appPaths.platformOperations();
		case "platform-audit":
			return appPaths.platformAudit();
		case "platform-support-sessions":
			return appPaths.platformSupportSessions();
		case "platform-help-articles":
			return appPaths.platformHelpArticles();
		case "platform-support-cases":
			return appPaths.platformSupportCases();
		case "organization":
			return context.organizationId ? appPaths.organization(context.organizationId, destination.section) : null;
		case "household":
			return context.organizationId && context.householdId
				? appPaths.household(context.organizationId, context.householdId, destination.section)
				: null;
		case "team-events":
			return context.organizationId && context.teamId ? appPaths.teamEvents(context.organizationId, context.teamId) : appPaths.dashboard("coach-schedule");
		case "tournament-events":
			return context.organizationId && context.tournamentId
				? appPaths.tournamentEvents(context.organizationId, context.tournamentId)
				: null;
		case "participant-events":
			return context.organizationId && context.participantId
				? appPaths.participantEvents(context.organizationId, context.participantId)
				: appPaths.dashboard("athlete-schedule");
		case "swag-shop-order":
			return context.organizationId ? appPaths.swagShopOrder(context.organizationId) : null;
		case "platform-reports":
			return appPaths.platformReports();
	}
}

export function navItemsFor(
	contextType: ContextType,
	capabilities: Set<string>,
	context: NavRouteContext = {},
): ResolvedNavRegistryItem[] {
	return NAV_REGISTRY.filter(
		(item) =>
			item.contextTypes.includes(contextType) &&
			(!item.requiredCapabilities || item.requiredCapabilities.some((capability) => capabilities.has(capability))),
	)
		.map((item) => ({ ...item, to: resolveDestination(item.destination, context) }))
		.filter((item): item is ResolvedNavRegistryItem => Boolean(item.to));
}
