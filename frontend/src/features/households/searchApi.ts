import { useQuery } from "@tanstack/react-query";
import { apiFetch } from "../../lib/apiClient";
import type { HouseholdPage } from "./types";

export interface HouseholdSearchParams {
	page?: number;
	size?: number;
	q?: string;
	status?: string;
	teamId?: string;
	sort?: "NAME_ASC" | "NAME_DESC" | "NEWEST" | "OLDEST";
}

export function useHouseholdSearch(organizationId: string, params: HouseholdSearchParams) {
	const search = new URLSearchParams({
		page: String(params.page ?? 0),
		size: String(params.size ?? 25),
		sort: params.sort ?? "NAME_ASC",
	});
	if (params.q?.trim()) search.set("q", params.q.trim());
	if (params.status) search.set("status", params.status);
	if (params.teamId) search.set("teamId", params.teamId);

	return useQuery({
		queryKey: ["organizations", organizationId, "households", "search", Object.fromEntries(search.entries())],
		queryFn: () => apiFetch<HouseholdPage>(`/organizations/${organizationId}/households/search?${search.toString()}`),
		enabled: !!organizationId,
	});
}
