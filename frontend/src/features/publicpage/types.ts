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
}

export interface PublicPagePage {
	items: PublicPage[];
	page: number;
	size: number;
	totalElements: number;
}
