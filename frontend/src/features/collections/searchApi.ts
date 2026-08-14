import { useQuery } from "@tanstack/react-query";
import { apiFetch, apiFetchBlob } from "../../lib/apiClient";
import type { FeeAssignmentStatus } from "../fees/types";
import type { FeeAssignmentSummaryPage } from "./types";

export type CollectionsSort =
	| "DUE_DATE_ASC"
	| "DUE_DATE_DESC"
	| "BALANCE_DESC"
	| "BALANCE_ASC"
	| "DESCRIPTION_ASC"
	| "HOUSEHOLD_ASC"
	| "NEWEST"
	| "OLDEST";

export interface CollectionsSearchFilter {
	q?: string;
	status?: FeeAssignmentStatus;
	overdueOnly?: boolean;
	sort?: CollectionsSort;
	page?: number;
	size?: number;
}

function buildQueryString(filter: CollectionsSearchFilter, includePage = true): string {
	const params = new URLSearchParams();
	if (filter.q?.trim()) params.set("q", filter.q.trim());
	if (filter.status) params.set("status", filter.status);
	if (filter.overdueOnly) params.set("overdueOnly", "true");
	params.set("sort", filter.sort ?? "DUE_DATE_ASC");
	if (includePage) {
		params.set("page", String(filter.page ?? 0));
		params.set("size", String(filter.size ?? 25));
	}
	return params.toString();
}

export function useCollectionsSearch(organizationId: string, filter: CollectionsSearchFilter) {
	return useQuery({
		queryKey: ["organizations", organizationId, "collections", "search", filter],
		queryFn: () =>
			apiFetch<FeeAssignmentSummaryPage>(
				`/organizations/${organizationId}/fee-assignments/search?${buildQueryString(filter)}`,
			),
		enabled: !!organizationId,
	});
}

export async function downloadCollectionsSearchCsv(
	organizationId: string,
	filter: CollectionsSearchFilter,
): Promise<void> {
	const { blob, filename } = await apiFetchBlob(
		`/organizations/${organizationId}/fee-assignments/search/export?${buildQueryString(filter, false)}`,
	);
	const url = URL.createObjectURL(blob);
	const link = document.createElement("a");
	link.href = url;
	link.download = filename;
	document.body.appendChild(link);
	link.click();
	document.body.removeChild(link);
	URL.revokeObjectURL(url);
}
