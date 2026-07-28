export type CampaignType =
	| "ORGANIZATION_GENERAL"
	| "TEAM_GENERAL"
	| "TRAVEL"
	| "TOURNAMENT_FEES"
	| "UNIFORMS"
	| "EQUIPMENT"
	| "FACILITY_IMPROVEMENTS"
	| "SCHOLARSHIPS"
	| "SPECIAL_EVENTS"
	| "APPAREL_BASED"
	| "SPONSOR_SUPPORTED";

export type CampaignStatus = "DRAFT" | "ACTIVE" | "COMPLETED" | "ARCHIVED";

export interface Campaign {
	id: string;
	organizationId: string;
	teamId: string | null;
	name: string;
	slug: string;
	description: string | null;
	campaignType: CampaignType;
	goalAmountMinor: number;
	currency: string;
	startDate: string | null;
	endDate: string | null;
	status: CampaignStatus;
	publishedAt: string | null;
	createdAt: string;
	updatedAt: string;
}

export interface CampaignPage {
	items: Campaign[];
	page: number;
	size: number;
	totalElements: number;
}

/** Public-facing shape from GET /public/campaigns/{slug} — no createdAt/updatedAt. */
export interface PublicCampaign {
	id: string;
	organizationId: string;
	teamId: string | null;
	name: string;
	slug: string;
	description: string | null;
	campaignType: CampaignType;
	goalAmountMinor: number;
	currency: string;
	startDate: string | null;
	endDate: string | null;
	status: CampaignStatus;
	publishedAt: string | null;
}
