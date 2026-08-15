import { useQuery } from "@tanstack/react-query";
import { apiFetch } from "../../lib/apiClient";
import type { FeeAssignmentPage, FeeAssignmentStatus, FeeTemplatePage } from "./types";

export type FeeTemplateSearchSort =
	| "NAME_ASC"
	| "NAME_DESC"
	| "AMOUNT_ASC"
	| "AMOUNT_DESC"
	| "NEWEST"
	| "OLDEST";

export interface FeeTemplateSearchParams {
	page?: number;
	size?: number;
	q?: string;
	status?: "ACTIVE" | "ARCHIVED" | "";
	sort?: FeeTemplateSearchSort;
}

export function useFeeTemplateSearch(organizationId: string, params: FeeTemplateSearchParams) {
	const search = new URLSearchParams({
		page: String(params.page ?? 0),
		size: String(params.size ?? 25),
		sort: params.sort ?? "NAME_ASC",
	});
	if (params.q?.trim()) search.set("q", params.q.trim());
	if (params.status) search.set("status", params.status);

	return useQuery({
		queryKey: [
			"organizations",
			organizationId,
			"fee-templates",
			"search",
			Object.fromEntries(search.entries()),
		],
		queryFn: () =>
			apiFetch<FeeTemplatePage>(
				`/organizations/${organizationId}/fee-templates/search?${search.toString()}`,
			),
		enabled: !!organizationId,
	});
}

export type FeeAssignmentSearchSort =
	| "DUE_DATE_ASC"
	| "DUE_DATE_DESC"
	| "BALANCE_DESC"
	| "BALANCE_ASC"
	| "DESCRIPTION_ASC"
	| "HOUSEHOLD_ASC"
	| "NEWEST"
	| "OLDEST";

export interface HouseholdFeeSearchParams {
	page?: number;
	size?: number;
	q?: string;
	status?: FeeAssignmentStatus | "";
	overdueOnly?: boolean;
	sort?: FeeAssignmentSearchSort;
}

export function useHouseholdFeeSearch(
	organizationId: string,
	householdId: string,
	params: HouseholdFeeSearchParams,
) {
	const search = new URLSearchParams({
		page: String(params.page ?? 0),
		size: String(params.size ?? 25),
		sort: params.sort ?? "DUE_DATE_ASC",
		overdueOnly: String(params.overdueOnly ?? false),
	});
	if (params.q?.trim()) search.set("q", params.q.trim());
	if (params.status) search.set("status", params.status);

	return useQuery({
		queryKey: [
			"organizations",
			organizationId,
			"households",
			householdId,
			"fee-assignments",
			"search",
			Object.fromEntries(search.entries()),
		],
		queryFn: () =>
			apiFetch<FeeAssignmentPage>(
				`/organizations/${organizationId}/households/${householdId}/fee-assignments/search?${search.toString()}`,
			),
		enabled: !!organizationId && !!householdId,
	});
}
