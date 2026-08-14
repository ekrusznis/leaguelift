import { useQuery } from "@tanstack/react-query";
import { apiFetch } from "../../lib/apiClient";
import type {
	CampaignPage,
	CampaignStatus,
	CampaignType,
	ContributionPage,
	FundraiserTemplateKey,
} from "./types";

export type CampaignSearchSort =
	| "NEWEST"
	| "NAME_ASC"
	| "START_DATE_ASC"
	| "END_DATE_ASC"
	| "RAISED_DESC"
	| "GOAL_DESC";

export interface CampaignSearchParams {
	page?: number;
	size?: number;
	q?: string;
	status?: CampaignStatus | "";
	campaignType?: CampaignType | "";
	templateKey?: FundraiserTemplateKey | "";
	teamId?: string;
	sort?: CampaignSearchSort;
}

export function useCampaignSearch(organizationId: string, params: CampaignSearchParams) {
	const search = new URLSearchParams({
		page: String(params.page ?? 0),
		size: String(params.size ?? 25),
		sort: params.sort ?? "NEWEST",
	});
	if (params.q?.trim()) search.set("q", params.q.trim());
	if (params.status) search.set("status", params.status);
	if (params.campaignType) search.set("campaignType", params.campaignType);
	if (params.templateKey) search.set("templateKey", params.templateKey);
	if (params.teamId) search.set("teamId", params.teamId);

	return useQuery({
		queryKey: ["organizations", organizationId, "campaigns", "search", Object.fromEntries(search.entries())],
		queryFn: () => apiFetch<CampaignPage>(`/organizations/${organizationId}/campaigns/search?${search.toString()}`),
		enabled: !!organizationId,
	});
}

export type ContributionSearchSort = "NEWEST" | "OLDEST" | "AMOUNT_DESC" | "AMOUNT_ASC" | "SUPPORTER_ASC";

export interface ContributionSearchParams {
	page?: number;
	size?: number;
	q?: string;
	status?: "CONFIRMED" | "REFUNDED" | "";
	paymentSource?: "STRIPE" | "OFFLINE" | "";
	sort?: ContributionSearchSort;
}

export function useContributionSearch(
	organizationId: string,
	campaignId: string,
	params: ContributionSearchParams,
) {
	const search = new URLSearchParams({
		page: String(params.page ?? 0),
		size: String(params.size ?? 25),
		sort: params.sort ?? "NEWEST",
	});
	if (params.q?.trim()) search.set("q", params.q.trim());
	if (params.status) search.set("status", params.status);
	if (params.paymentSource) search.set("paymentSource", params.paymentSource);

	return useQuery({
		queryKey: [
			"organizations",
			organizationId,
			"campaigns",
			campaignId,
			"contributions",
			"search",
			Object.fromEntries(search.entries()),
		],
		queryFn: () =>
			apiFetch<ContributionPage>(
				`/organizations/${organizationId}/campaigns/${campaignId}/contributions/search?${search.toString()}`,
			),
		enabled: !!organizationId && !!campaignId,
	});
}
