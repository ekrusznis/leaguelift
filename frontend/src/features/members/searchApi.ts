import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiFetch } from "../../lib/apiClient";
import type { MembershipPage } from "./types";

export interface MemberSearchParams {
	page?: number;
	size?: number;
	q?: string;
	role?: string;
	status?: string;
	sort?: "NAME_ASC" | "NAME_DESC" | "ROLE_ASC" | "NEWEST" | "OLDEST";
}

const baseKey = (organizationId: string) => ["organizations", organizationId, "members"] as const;

export function useMemberSearch(organizationId: string, params: MemberSearchParams) {
	const search = new URLSearchParams({
		page: String(params.page ?? 0),
		size: String(params.size ?? 25),
		sort: params.sort ?? "NAME_ASC",
	});
	if (params.q?.trim()) search.set("q", params.q.trim());
	if (params.role) search.set("role", params.role);
	if (params.status) search.set("status", params.status);

	return useQuery({
		queryKey: [...baseKey(organizationId), "search", Object.fromEntries(search.entries())],
		queryFn: () => apiFetch<MembershipPage>(`/organizations/${organizationId}/members/search?${search.toString()}`),
		enabled: !!organizationId,
	});
}

export function useUpdateOrganizationMemberRole(organizationId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: ({ memberId, role }: { memberId: string; role: string }) =>
			apiFetch(`/organizations/${organizationId}/members/${memberId}`, {
				method: "PATCH",
				body: { role },
			}),
		onSuccess: () => queryClient.invalidateQueries({ queryKey: baseKey(organizationId) }),
	});
}

export function useDisableOrganizationMember(organizationId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (memberId: string) =>
			apiFetch(`/organizations/${organizationId}/members/${memberId}`, { method: "DELETE" }),
		onSuccess: () => queryClient.invalidateQueries({ queryKey: baseKey(organizationId) }),
	});
}
