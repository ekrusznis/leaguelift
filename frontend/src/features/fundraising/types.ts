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
	/** Sum of CONFIRMED contributions — real data, not a demo placeholder. */
	raisedMinor: number;
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
	raisedMinor: number;
}

export type ContributionStatus = "PENDING" | "CONFIRMED" | "CANCELED" | "REFUNDED";

/** Response from POST /public/campaigns/{slug}/contributions. */
export interface ContributionCheckout {
	contributionId: string;
	checkoutUrl: string;
}

/** Response from GET /public/campaigns/{slug}/contributions/{contributionId} — used by the browser's return-page poll. */
export interface ContributionStatusResult {
	id: string;
	status: ContributionStatus;
	amountMinor: number;
	currency: string;
	confirmedAt: string | null;
}

/** Org-admin list shape from GET /organizations/{id}/campaigns/{campaignId}/contributions. */
export interface Contribution {
	id: string;
	status: ContributionStatus;
	paymentSource: "STRIPE" | "OFFLINE";
	amountMinor: number;
	currency: string;
	supporterName: string | null;
	isAnonymous: boolean;
	supporterEmail: string | null;
	confirmedAt: string | null;
	refundedAt: string | null;
	createdAt: string;
}

export interface ContributionPage {
	items: Contribution[];
	page: number;
	size: number;
	totalElements: number;
}
