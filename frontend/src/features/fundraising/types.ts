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

export type CampaignStatus = "DRAFT" | "PENDING_APPROVAL" | "ACTIVE" | "COMPLETED" | "ARCHIVED";

/** Starter/configuration style used to create the fundraiser. Legacy BOX_POOL remains readable but is no longer offered for new campaigns. */
export type FundraiserTemplateKey =
	| "GENERAL"
	| "IN_PERSON_EVENT"
	| "SPONSOR_MATCH"
	| "MILESTONE_CHALLENGE"
	| "FUNDRAISING_CHALLENGE"
	| "BOX_POOL"
	| "BAKE_SALE"
	| "CAR_WASH";

export interface CampaignPermissions {
	canEdit: boolean;
	canRequestActivation: boolean;
	canApprove: boolean;
	canReturnToDraft: boolean;
	canClose: boolean;
	canArchive: boolean;
	canManageBoxPool: boolean;
}

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
	eventLocationName: string | null;
	eventAddress: string | null;
	status: CampaignStatus;
	publishedAt: string | null;
	createdByUserId: string | null;
	templateKey: FundraiserTemplateKey | null;
	submittedAt?: string | null;
	approvedAt?: string | null;
	approvedByUserId?: string | null;
	permissions?: CampaignPermissions;
	createdAt: string;
	updatedAt: string;
	raisedMinor: number;
}

export interface CampaignPage { items: Campaign[]; page: number; size: number; totalElements: number; }

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
	eventLocationName: string | null;
	eventAddress: string | null;
	status: CampaignStatus;
	publishedAt: string | null;
	raisedMinor: number;
	logoUrl: string | null;
	coverUrl: string | null;
	primaryColor: string;
	secondaryColor: string;
}

export interface FundraisingSettings {
	organizationId: string;
	requireOwnerApproval: boolean;
	updatedByUserId: string | null;
	updatedAt: string | null;
}

export interface CampaignShareLink { url: string; qrCodeDataUri: string; }
export type ContributionStatus = "PENDING" | "CONFIRMED" | "CANCELED" | "REFUNDED";
export interface ContributionCheckout { contributionId: string; checkoutUrl: string; }
export interface ContributionStatusResult { id: string; status: ContributionStatus; amountMinor: number; currency: string; confirmedAt: string | null; }
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
export interface ContributionPage { items: Contribution[]; page: number; size: number; totalElements: number; }
