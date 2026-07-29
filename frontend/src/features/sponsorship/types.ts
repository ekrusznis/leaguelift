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

/** The org-admin approval workflow's review state (Phase 6 remainder, ADR-019) — kept conceptually separate from payment `SponsorshipStatus`: a sponsorship can be CONFIRMED and still PENDING_REVIEW. */
export type SponsorshipReviewStatus = "PENDING_REVIEW" | "APPROVED" | "REJECTED";

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
	refundedAt: string | null;
	reviewStatus: SponsorshipReviewStatus;
	reviewedAt: string | null;
	createdAt: string;
}

/** Sponsor-contact CRM shape (Phase 6 remainder, ADR-019) — a small, bounded field set, not a full multi-contact CRM. */
export interface Sponsor {
	id: string;
	name: string;
	contactEmail: string | null;
	phone: string | null;
	companyName: string | null;
	notes: string | null;
	createdAt: string;
	updatedAt: string;
}

/** A downloadable/viewable receipt-style summary of one confirmed sponsorship (Phase 6 remainder, ADR-019). */
export interface SponsorshipInvoice {
	sponsorshipId: string;
	status: SponsorshipStatus;
	amountMinor: number;
	currency: string;
	confirmedAt: string | null;
	sponsorName: string;
	sponsorContactEmail: string | null;
	sponsorCompanyName: string | null;
	packageId: string;
	packageName: string;
	organizationId: string;
	organizationName: string;
}

/** A shareable QR code image (as a ready-to-render data URI) plus the plain URL it encodes (Phase 6 remainder, ADR-019 — no click-through tracking). */
export interface ShareLink {
	url: string;
	qrCodeDataUri: string;
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
