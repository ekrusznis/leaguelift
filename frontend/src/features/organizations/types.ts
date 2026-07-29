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
	sports: string[];
	contactEmail: string | null;
	contactPhone: string | null;
	createdAt: string;
	updatedAt: string;
}

export interface OrganizationPage {
	items: Organization[];
	page: number;
	size: number;
	totalElements: number;
}

export interface OnboardingProgress {
	profileComplete: boolean;
	hasAdditionalAdministrator: boolean;
	payoutsConnected: boolean;
}

export const INVITABLE_ROLES = [
	"ADMINISTRATOR",
	"FINANCE_MANAGER",
	"TEAM_ADMINISTRATOR",
	"TOURNAMENT_ADMINISTRATOR",
	"VIEWER",
] as const;

export type InvitableRole = (typeof INVITABLE_ROLES)[number];

export interface Invitation {
	id: string;
	organizationId: string;
	email: string;
	role: InvitableRole;
	status: "PENDING" | "ACCEPTED" | "REVOKED" | "EXPIRED";
	expiresAt: string;
	createdAt: string;
}

export interface InvitationPage {
	items: Invitation[];
	page: number;
	size: number;
	totalElements: number;
}
