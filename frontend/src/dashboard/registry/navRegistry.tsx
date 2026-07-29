import type { ReactNode } from "react";
import type { ContextType } from "../../authorization/types";
import { Capabilities } from "../../authorization/capabilityConstants";
import {
	BuildingIcon,
	CalendarIcon,
	ChartIcon,
	DollarIcon,
	HeartHandshakeIcon,
	HomeIcon,
	LayoutIcon,
	MegaphoneIcon,
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
 * hardcoded per-role JSX (DESIGN-DOC.md section 4.2/10.3). Covers the Coach
 * dashboard's capability-tiered nav (§10.2: "conditional: Fees, Reports, Members,
 * Settings only when capabilities permit") plus the two brand-new dashboards this
 * phase adds (Tournament, Platform Admin). Owner/Parent/Athlete keep their existing
 * static nav arrays for now — out of this phase's scope (ADR-020 consequences).
 */
export const NAV_REGISTRY: NavRegistryItem[] = [
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
