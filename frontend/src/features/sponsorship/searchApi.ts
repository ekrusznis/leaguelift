import { useQuery } from "@tanstack/react-query";
import { apiFetch } from "../../lib/apiClient";
import type {
	SponsorshipPackagePage,
	SponsorshipPackageStatus,
	SponsorshipReviewStatus,
	SponsorshipStatus,
} from "./types";

export type SponsorshipPackageSearchSort =
	| "NEWEST"
	| "OLDEST"
	| "NAME_ASC"
	| "NAME_DESC"
	| "PRICE_ASC"
	| "PRICE_DESC"
	| "SPONSORS_DESC";

export function useSponsorshipPackageSearch(
	organizationId: string,
	params: {
		page?: number;
		size?: number;
		q?: string;
		status?: SponsorshipPackageStatus | "";
		exclusive?: boolean;
		sort?: SponsorshipPackageSearchSort;
	},
) {
	const search = new URLSearchParams({
		page: String(params.page ?? 0),
		size: String(params.size ?? 25),
		sort: params.sort ?? "NEWEST",
	});
	if (params.q?.trim()) search.set("q", params.q.trim());
	if (params.status) search.set("status", params.status);
	if (params.exclusive !== undefined) search.set("exclusive", String(params.exclusive));

	return useQuery({
		queryKey: [
			"organizations",
			organizationId,
			"sponsorship-packages",
			"search",
			Object.fromEntries(search.entries()),
		],
		queryFn: () =>
			apiFetch<SponsorshipPackagePage>(
				`/organizations/${organizationId}/sponsorship-packages/search?${search.toString()}`,
			),
		enabled: !!organizationId,
	});
}

export type SponsorshipSearchSort =
	| "NEWEST"
	| "OLDEST"
	| "SPONSOR_ASC"
	| "AMOUNT_ASC"
	| "AMOUNT_DESC"
	| "PACKAGE_ASC"
	| "REVIEW_STATUS_ASC";

export interface SponsorshipSearchItem {
	id: string;
	packageId: string;
	packageName: string;
	status: SponsorshipStatus;
	paymentSource: "STRIPE" | "OFFLINE";
	amountMinor: number;
	currency: string;
	sponsorId: string;
	sponsorName: string;
	sponsorContactEmail: string | null;
	sponsorCompanyName: string | null;
	confirmedAt: string | null;
	refundedAt: string | null;
	reviewStatus: SponsorshipReviewStatus;
	reviewedAt: string | null;
	createdAt: string;
}

export interface SponsorshipSearchPage {
	items: SponsorshipSearchItem[];
	page: number;
	size: number;
	totalElements: number;
}

export function useSponsorshipSearch(
	organizationId: string,
	params: {
		page?: number;
		size?: number;
		q?: string;
		packageId?: string;
		status?: Extract<SponsorshipStatus, "CONFIRMED" | "REFUNDED"> | "";
		reviewStatus?: SponsorshipReviewStatus | "";
		paymentSource?: "STRIPE" | "OFFLINE" | "";
		sort?: SponsorshipSearchSort;
	},
	enabled = true,
) {
	const search = new URLSearchParams({
		page: String(params.page ?? 0),
		size: String(params.size ?? 25),
		sort: params.sort ?? "NEWEST",
	});
	if (params.q?.trim()) search.set("q", params.q.trim());
	if (params.packageId) search.set("packageId", params.packageId);
	if (params.status) search.set("status", params.status);
	if (params.reviewStatus) search.set("reviewStatus", params.reviewStatus);
	if (params.paymentSource) search.set("paymentSource", params.paymentSource);

	return useQuery({
		queryKey: [
			"organizations",
			organizationId,
			"sponsorships",
			"search",
			Object.fromEntries(search.entries()),
		],
		queryFn: () =>
			apiFetch<SponsorshipSearchPage>(
				`/organizations/${organizationId}/sponsorships/search?${search.toString()}`,
			),
		enabled: enabled && !!organizationId,
	});
}
