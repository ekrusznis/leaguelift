import { useMutation, useQuery } from "@tanstack/react-query";
import { apiFetch } from "../../lib/apiClient";
import type { BillingPortalResponse, OrganizationSubscription, PageResponse, PlatformOrganizationSubscription } from "./types";

export function useOrganizationSubscription(organizationId: string | undefined) {
	return useQuery({
		queryKey: ["organization-subscription", organizationId],
		queryFn: () => apiFetch<OrganizationSubscription | null>(`/organizations/${organizationId}/subscription`),
		enabled: !!organizationId,
	});
}

export function useCreateBillingPortal() {
	return useMutation({
		mutationFn: (organizationId: string) =>
			apiFetch<BillingPortalResponse>(`/organizations/${organizationId}/subscription/portal`, { method: "POST" }),
	});
}

export function usePlatformSubscriptions(params: { query: string; status: string; page: number; size: number }) {
	const search = new URLSearchParams({
		page: String(params.page),
		size: String(params.size),
	});
	if (params.query) search.set("query", params.query);
	if (params.status) search.set("status", params.status);
	return useQuery({
		queryKey: ["platform-subscriptions", params],
		queryFn: () => apiFetch<PageResponse<PlatformOrganizationSubscription>>(`/platform/admin/subscriptions?${search.toString()}`),
	});
}
