export type PageType = "ORGANIZATION" | "TEAM" | "TOURNAMENT";
export type PageStatus = "DRAFT" | "PUBLISHED" | "ARCHIVED";

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
}

export interface PublicPagePage {
	items: PublicPage[];
	page: number;
	size: number;
	totalElements: number;
}
