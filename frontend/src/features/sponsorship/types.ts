export type SponsorshipPackageStatus = "DRAFT" | "PUBLISHED" | "ARCHIVED";

export interface SponsorshipPackage {
	id: string;
	organizationId: string;
	name: string;
	description: string | null;
	priceMinor: number;
	currency: string;
	maxQuantity: number | null;
	exclusive: boolean;
	placementStartDate: string | null;
	placementEndDate: string | null;
	status: SponsorshipPackageStatus;
	createdAt: string;
	updatedAt: string;
	/** Sum of CONFIRMED (+ REFUNDED, still "was confirmed") sponsorships — real, not demo data. */
	confirmedCount: number;
	soldOut: boolean;
}

export interface SponsorshipPackagePage {
	items: SponsorshipPackage[];
	page: number;
	size: number;
	totalElements: number;
}

export interface PublicSponsorshipPackage {
	id: string;
	organizationId: string;
	name: string;
	description: string | null;
	priceMinor: number;
	currency: string;
	maxQuantity: number | null;
	exclusive: boolean;
	placementStartDate: string | null;
	placementEndDate: string | null;
	confirmedCount: number;
	soldOut: boolean;
}

export type SponsorshipStatus = "PENDING" | "CONFIRMED" | "REFUNDED";

export interface SponsorshipCheckout {
	sponsorshipId: string;
	sponsorId: string;
	checkoutUrl: string;
}

export interface SponsorshipStatusResult {
	id: string;
	status: SponsorshipStatus;
	amountMinor: number;
	currency: string;
	confirmedAt: string | null;
}

/** Org-admin list shape — a confirmed sponsorship joined with its sponsor's name/contact. */
export interface Sponsorship {
	id: string;
	status: SponsorshipStatus;
	amountMinor: number;
	currency: string;
	sponsorId: string;
	sponsorName: string;
	sponsorContactEmail: string | null;
	confirmedAt: string | null;
	createdAt: string;
}

export interface SponsorshipPage {
	items: Sponsorship[];
	page: number;
	size: number;
	totalElements: number;
}

/** One row of the public sponsor directory. `logoUrl` is null until an org admin assigns one (ADR-018). */
export interface SponsorDirectoryEntry {
	sponsorId: string;
	sponsorName: string;
	packageId: string;
	packageName: string;
	logoUrl: string | null;
}
