import type { ReactNode } from "react";
import type { ContextType } from "../../authorization/types";
import { Capabilities } from "../../authorization/capabilityConstants";
import {
	BuildingIcon,
	CalendarIcon,
	ChartIcon,
	DollarIcon,
	FileTextIcon,
	GiftIcon,
	HeartHandshakeIcon,
	HistoryIcon,
	HomeIcon,
	LayoutIcon,
	MegaphoneIcon,
	PackageIcon,
	SettingsIcon,
	ShieldIcon,
	ShirtIcon,
	TrophyIcon,
	UserIcon,
	UserPlusIcon,
	UsersIcon,
} from "../icons";

export interface NavRegistryItem {
	id: string;
	label: string;
	icon: ReactNode;
	/** Which context type(s) this item applies to. */
	contextTypes: ContextType[];
	/** Any one of these capabilities (in the active context) is enough to show the item. Omit for "always shown in this context type". */
	requiredCapabilities?: string[];
}

/**
 * Data-driven nav items, filtered by real context/capability data rather than
 * hardcoded per-role JSX (DESIGN-DOC.md section 4.2/10.3). Covers every persona
 * dashboard's nav, including Owner/Parent/Athlete (migrated off their static
 * NAV_ITEMS arrays post-Phase-7, closing the ADR-020 consequence that only
 * Coach/Tournament/Platform Admin used this registry). Owner/Parent/Athlete items
 * are unconditional (no requiredCapabilities) to preserve their exact pre-migration
 * behavior — only Coach's Fees/Members/Settings were ever capability-gated.
 */
export const NAV_REGISTRY: NavRegistryItem[] = [
	// Owner/Director (ORGANIZATION context)
	{ id: "owner.overview", label: "Overview", icon: <HomeIcon className="size-5" />, contextTypes: ["ORGANIZATION"] },
	{ id: "owner.organization", label: "Organization", icon: <BuildingIcon className="size-5" />, contextTypes: ["ORGANIZATION"] },
	{ id: "owner.teams", label: "Teams", icon: <UsersIcon className="size-5" />, contextTypes: ["ORGANIZATION"] },
	{ id: "owner.tournaments", label: "Tournaments", icon: <TrophyIcon className="size-5" />, contextTypes: ["ORGANIZATION"] },
	{ id: "owner.households", label: "Households", icon: <HomeIcon className="size-5" />, contextTypes: ["ORGANIZATION"] },
	{ id: "owner.participants", label: "Participants", icon: <UserIcon className="size-5" />, contextTypes: ["ORGANIZATION"] },
	{ id: "owner.fees", label: "Fees & Payments", icon: <DollarIcon className="size-5" />, contextTypes: ["ORGANIZATION"] },
	{ id: "owner.fundraising", label: "Fundraising", icon: <HeartHandshakeIcon className="size-5" />, contextTypes: ["ORGANIZATION"] },
	{ id: "owner.apparel", label: "Apparel", icon: <ShirtIcon className="size-5" />, contextTypes: ["ORGANIZATION"] },
	{ id: "owner.sponsorships", label: "Sponsorships", icon: <MegaphoneIcon className="size-5" />, contextTypes: ["ORGANIZATION"] },
	{ id: "owner.credits", label: "Credits", icon: <GiftIcon className="size-5" />, contextTypes: ["ORGANIZATION"] },
	{ id: "owner.reports", label: "Reports", icon: <ChartIcon className="size-5" />, contextTypes: ["ORGANIZATION"] },
	{ id: "owner.members", label: "Members", icon: <UserPlusIcon className="size-5" />, contextTypes: ["ORGANIZATION"] },
	{ id: "owner.settings", label: "Settings", icon: <SettingsIcon className="size-5" />, contextTypes: ["ORGANIZATION"] },

	// Parent/Guardian (HOUSEHOLD context)
	{ id: "parent.overview", label: "Overview", icon: <HomeIcon className="size-5" />, contextTypes: ["HOUSEHOLD"] },
	{ id: "parent.athletes", label: "My Athletes", icon: <UsersIcon className="size-5" />, contextTypes: ["HOUSEHOLD"] },
	{ id: "parent.schedule", label: "Schedule", icon: <CalendarIcon className="size-5" />, contextTypes: ["HOUSEHOLD"] },
	{ id: "parent.fees", label: "Fees & Payments", icon: <DollarIcon className="size-5" />, contextTypes: ["HOUSEHOLD"] },
	{ id: "parent.credits", label: "Credits", icon: <GiftIcon className="size-5" />, contextTypes: ["HOUSEHOLD"] },
	{ id: "parent.fundraising", label: "Fundraising", icon: <HeartHandshakeIcon className="size-5" />, contextTypes: ["HOUSEHOLD"] },
	{ id: "parent.orders", label: "Orders", icon: <PackageIcon className="size-5" />, contextTypes: ["HOUSEHOLD"] },
	{ id: "parent.documents", label: "Documents", icon: <FileTextIcon className="size-5" />, contextTypes: ["HOUSEHOLD"] },
	{ id: "parent.profile", label: "Profile", icon: <UserIcon className="size-5" />, contextTypes: ["HOUSEHOLD"] },

	// Athlete (ATHLETE context)
	{ id: "athlete.overview", label: "Overview", icon: <HomeIcon className="size-5" />, contextTypes: ["ATHLETE"] },
	{ id: "athlete.profile", label: "My Profile", icon: <UserIcon className="size-5" />, contextTypes: ["ATHLETE"] },
	{ id: "athlete.teams", label: "My Teams", icon: <UsersIcon className="size-5" />, contextTypes: ["ATHLETE"] },
	{ id: "athlete.schedule", label: "Schedule", icon: <CalendarIcon className="size-5" />, contextTypes: ["ATHLETE"] },
	{ id: "athlete.history", label: "History", icon: <HistoryIcon className="size-5" />, contextTypes: ["ATHLETE"] },
	{ id: "athlete.guardians", label: "Guardians", icon: <ShieldIcon className="size-5" />, contextTypes: ["ATHLETE"] },
	{ id: "athlete.orders", label: "Orders", icon: <PackageIcon className="size-5" />, contextTypes: ["ATHLETE"] },

	// Coach (TEAM context)
	{ id: "coach.overview", label: "Overview", icon: <HomeIcon className="size-5" />, contextTypes: ["TEAM"] },
	{ id: "coach.my-teams", label: "My Teams", icon: <UsersIcon className="size-5" />, contextTypes: ["TEAM"] },
	{ id: "coach.schedule", label: "Schedule", icon: <CalendarIcon className="size-5" />, contextTypes: ["TEAM"] },
	{ id: "coach.roster", label: "Roster", icon: <UserIcon className="size-5" />, contextTypes: ["TEAM"] },
	{ id: "coach.team-page", label: "Team Page", icon: <LayoutIcon className="size-5" />, contextTypes: ["TEAM"] },
	{ id: "coach.fundraising", label: "Fundraising", icon: <HeartHandshakeIcon className="size-5" />, contextTypes: ["TEAM"] },
	{ id: "coach.apparel", label: "Apparel", icon: <ShirtIcon className="size-5" />, contextTypes: ["TEAM"] },
	{ id: "coach.announcements", label: "Announcements", icon: <MegaphoneIcon className="size-5" />, contextTypes: ["TEAM"] },
	{ id: "coach.fees", label: "Fees", icon: <DollarIcon className="size-5" />, contextTypes: ["TEAM"], requiredCapabilities: [Capabilities.TEAM_FEE_VIEW] },
	{ id: "coach.members", label: "Members", icon: <UserPlusIcon className="size-5" />, contextTypes: ["TEAM"], requiredCapabilities: [Capabilities.TEAM_STAFF_MANAGE] },
	{ id: "coach.settings", label: "Settings", icon: <SettingsIcon className="size-5" />, contextTypes: ["TEAM"], requiredCapabilities: [Capabilities.TEAM_ROSTER_MANAGE] },

	// Tournament Admin (TOURNAMENT context)
	{ id: "tournament.overview", label: "Overview", icon: <HomeIcon className="size-5" />, contextTypes: ["TOURNAMENT"] },
	{ id: "tournament.page", label: "Tournament Page", icon: <LayoutIcon className="size-5" />, contextTypes: ["TOURNAMENT"] },
	{ id: "tournament.teams", label: "Participating Teams", icon: <TrophyIcon className="size-5" />, contextTypes: ["TOURNAMENT"] },
	{
		id: "tournament.settings",
		label: "Settings",
		icon: <SettingsIcon className="size-5" />,
		contextTypes: ["TOURNAMENT"],
		requiredCapabilities: [Capabilities.TOURNAMENT_MANAGE],
	},

	// Platform Admin (PLATFORM_ADMIN context)
	{ id: "platform.overview", label: "Overview", icon: <HomeIcon className="size-5" />, contextTypes: ["PLATFORM_ADMIN"] },
	{
		id: "platform.organizations",
		label: "Organizations",
		icon: <BuildingIcon className="size-5" />,
		contextTypes: ["PLATFORM_ADMIN"],
		requiredCapabilities: [Capabilities.PLATFORM_ORG_VIEW],
	},
	{
		id: "platform.users",
		label: "Users",
		icon: <UserIcon className="size-5" />,
		contextTypes: ["PLATFORM_ADMIN"],
		requiredCapabilities: [Capabilities.PLATFORM_USER_VIEW],
	},
	{
		id: "platform.integrations",
		label: "Integrations",
		icon: <ShieldIcon className="size-5" />,
		contextTypes: ["PLATFORM_ADMIN"],
		requiredCapabilities: [Capabilities.PLATFORM_INTEGRATION_VIEW],
	},
	{
		id: "platform.reports",
		label: "Reports",
		icon: <ChartIcon className="size-5" />,
		contextTypes: ["PLATFORM_ADMIN"],
		requiredCapabilities: [Capabilities.PLATFORM_AUDIT_VIEW],
	},
];

export function navItemsFor(contextType: ContextType, capabilities: Set<string>): NavRegistryItem[] {
	return NAV_REGISTRY.filter(
		(item) =>
			item.contextTypes.includes(contextType) &&
			(!item.requiredCapabilities || item.requiredCapabilities.some((capability) => capabilities.has(capability))),
	);
}
