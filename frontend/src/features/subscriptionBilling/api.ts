import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiFetch } from "../../lib/apiClient";
import type {
	BillingPortalResponse,
	OrganizationSubscription,
	PageResponse,
	PlanChangePreview,
	PlanChangeResult,
	PlatformOrganizationSubscription,
	SubscriptionPlanOption,
} from "./types";

export function useOrganizationSubscription(organizationId: string | undefined) {
	return useQuery({
		queryKey: ["organization-subscription", organizationId],
		queryFn: ({ signal }) => apiFetch<OrganizationSubscription | null>(`/organizations/${organizationId}/subscription`, { signal }),
		enabled: !!organizationId,
	});
}

export function useCreateBillingPortal() {
	return useMutation({
		mutationFn: (organizationId: string) =>
			apiFetch<BillingPortalResponse>(`/organizations/${organizationId}/subscription/portal`, { method: "POST" }),
	});
}

export function useSubscriptionPlans(organizationId: string | undefined) {
	return useQuery({
		queryKey: ["organization-subscription-plans", organizationId],
		queryFn: ({ signal }) => apiFetch<SubscriptionPlanOption[]>(`/organizations/${organizationId}/subscription/plans`, { signal }),
		enabled: !!organizationId,
	});
}

export function usePreviewPlanChange(organizationId: string | undefined) {
	return useMutation({
		mutationFn: (targetPlanCode: string) =>
			apiFetch<PlanChangePreview>(`/organizations/${organizationId}/subscription/plan-change/preview`, {
				method: "POST",
				body: { targetPlanCode },
			}),
	});
}

export function useApplyPlanChange(organizationId: string | undefined) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (targetPlanCode: string) =>
			apiFetch<PlanChangeResult>(`/organizations/${organizationId}/subscription/plan-change/apply`, {
				method: "POST",
				body: { targetPlanCode },
			}),
		onSuccess: () => {
			void queryClient.invalidateQueries({ queryKey: ["organization-subscription", organizationId] });
		},
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
