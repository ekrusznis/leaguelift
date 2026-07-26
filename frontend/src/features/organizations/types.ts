export const ORGANIZATION_TYPES = [
	"RECREATIONAL_LEAGUE",
	"TRAVEL_CLUB",
	"INDIVIDUAL_TEAM",
	"TOURNAMENT_OPERATOR",
	"BOOSTER_ORGANIZATION",
	"MULTISPORT_FACILITY",
	"COMMUNITY_PROGRAM",
	"OTHER",
] as const;

export type OrganizationType = (typeof ORGANIZATION_TYPES)[number];

export interface Organization {
	id: string;
	name: string;
	slug: string;
	organizationType: OrganizationType;
	status: "ACTIVE" | "SUSPENDED" | "ARCHIVED";
	createdAt: string;
	updatedAt: string;
}

export interface OrganizationPage {
	items: Organization[];
	page: number;
	size: number;
	totalElements: number;
}
