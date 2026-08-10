export type PageType = "ORGANIZATION" | "TEAM" | "TOURNAMENT";
export type PageStatus = "DRAFT" | "PUBLISHED" | "ARCHIVED";

export interface PublicPageMedia {
	assetId: string;
	url: string;
	altText: string | null;
	contentType: string | null;
	widthPx: number | null;
	heightPx: number | null;
}

export interface PublicPage {
	id: string;
	organizationId: string;
	pageType: PageType;
	entityId: string;
	slug: string;
	title: string;
	summary: string | null;
	status: PageStatus;
	publishedAt: string | null;
	createdAt: string;
	updatedAt: string;
	logo: PublicPageMedia | null;
	cover: PublicPageMedia | null;
	/** Phase 35 (ADR-099): resolved (always non-null) team colors for a TEAM page; Rally26 defaults for every other page type. */
	primaryColor: string;
	secondaryColor: string;
}

export interface PublicPagePage {
	items: PublicPage[];
	page: number;
	size: number;
	totalElements: number;
}
