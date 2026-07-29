import { useQuery } from "@tanstack/react-query";
import { apiFetch, apiFetchBlob } from "../../lib/apiClient";
import type { FeeAssignmentStatus } from "../fees/types";
import type { FeeAssignmentSummaryPage } from "./types";

export interface CollectionsFilter {
	status?: FeeAssignmentStatus;
	overdueOnly?: boolean;
	page?: number;
	size?: number;
}

const collectionsKey = (organizationId: string, filter: CollectionsFilter) =>
	["organizations", organizationId, "collections", filter] as const;

function buildQueryString(filter: CollectionsFilter): string {
	const params = new URLSearchParams();
	if (filter.status) params.set("status", filter.status);
	if (filter.overdueOnly) params.set("overdueOnly", "true");
	params.set("page", String(filter.page ?? 0));
	params.set("size", String(filter.size ?? 50));
	return params.toString();
}

export function useCollections(organizationId: string, filter: CollectionsFilter) {
	return useQuery({
		queryKey: collectionsKey(organizationId, filter),
		queryFn: () => apiFetch<FeeAssignmentSummaryPage>(`/organizations/${organizationId}/fee-assignments?${buildQueryString(filter)}`),
		enabled: !!organizationId,
	});
}

export async function downloadCollectionsCsv(organizationId: string, filter: CollectionsFilter): Promise<void> {
	const params = new URLSearchParams();
	if (filter.status) params.set("status", filter.status);
	if (filter.overdueOnly) params.set("overdueOnly", "true");

	const { blob, filename } = await apiFetchBlob(`/organizations/${organizationId}/fee-assignments/export?${params.toString()}`);
	const url = URL.createObjectURL(blob);
	const link = document.createElement("a");
	link.href = url;
	link.download = filename;
	document.body.appendChild(link);
	link.click();
	document.body.removeChild(link);
	URL.revokeObjectURL(url);
}
