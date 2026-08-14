import { useQuery } from "@tanstack/react-query";
import { apiFetch } from "../../lib/apiClient";
import type { TeamPage } from "./types";

export interface TeamSearchParams {
	page?: number;
	size?: number;
	q?: string;
	sport?: string;
	season?: string;
	genderCategory?: string;
	status?: string;
	sort?: "NAME_ASC" | "NAME_DESC" | "SPORT_ASC" | "NEWEST" | "OLDEST";
}

export interface TeamStaffMember {
	userId: string;
	displayName: string;
	role: string;
	roleLabel: string;
}

export function useTeamSearch(organizationId: string, params: TeamSearchParams) {
	const search = new URLSearchParams({
		page: String(params.page ?? 0),
		size: String(params.size ?? 25),
		sort: params.sort ?? "NAME_ASC",
	});
	if (params.q?.trim()) search.set("q", params.q.trim());
	if (params.sport?.trim()) search.set("sport", params.sport.trim());
	if (params.season?.trim()) search.set("season", params.season.trim());
	if (params.genderCategory) search.set("genderCategory", params.genderCategory);
	if (params.status) search.set("status", params.status);

	return useQuery({
		queryKey: ["organizations", organizationId, "teams", "search", Object.fromEntries(search.entries())],
		queryFn: () => apiFetch<TeamPage>(`/organizations/${organizationId}/teams/search?${search.toString()}`),
		enabled: !!organizationId,
	});
}

export function useTeamStaff(organizationId: string, teamId: string) {
	return useQuery({
		queryKey: ["organizations", organizationId, "teams", teamId, "staff"],
		queryFn: () => apiFetch<TeamStaffMember[]>(`/organizations/${organizationId}/teams/${teamId}/staff`),
		enabled: !!organizationId && !!teamId,
	});
}
