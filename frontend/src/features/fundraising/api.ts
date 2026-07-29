import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiFetch } from "../../lib/apiClient";
import type { CreateCampaignFormValues, CreateContributionFormValues } from "./schema";
import type {
	Campaign,
	CampaignPage,
	CampaignStatus,
	ContributionCheckout,
	ContributionPage,
	ContributionStatusResult,
	PublicCampaign,
} from "./types";

const campaignsKey = (organizationId: string) => ["organizations", organizationId, "campaigns"] as const;

export function useCampaigns(organizationId: string) {
	return useQuery({
		queryKey: campaignsKey(organizationId),
		queryFn: () => apiFetch<CampaignPage>(`/organizations/${organizationId}/campaigns`),
		enabled: !!organizationId,
	});
}

export function useCreateCampaign(organizationId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (values: CreateCampaignFormValues) =>
			apiFetch<Campaign>(`/organizations/${organizationId}/campaigns`, {
				method: "POST",
				body: {
					...values,
					teamId: values.teamId || null,
					description: values.description || null,
					startDate: values.startDate || null,
					endDate: values.endDate || null,
				},
			}),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: campaignsKey(organizationId) });
		},
	});
}

export function usePublishCampaign(organizationId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (campaignId: string) =>
			apiFetch<Campaign>(`/organizations/${organizationId}/campaigns/${campaignId}/publish`, { method: "POST" }),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: campaignsKey(organizationId) });
		},
	});
}

export function useUpdateCampaignStatus(organizationId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: ({ campaignId, status }: { campaignId: string; status: CampaignStatus }) =>
			apiFetch<Campaign>(`/organizations/${organizationId}/campaigns/${campaignId}/status`, {
				method: "PATCH",
				body: { status },
			}),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: campaignsKey(organizationId) });
		},
	});
}

export function usePublicCampaign(slug: string) {
	return useQuery({
		queryKey: ["public", "campaigns", slug] as const,
		queryFn: () => apiFetch<PublicCampaign>(`/public/campaigns/${slug}`),
		enabled: !!slug,
		retry: false,
	});
}

/**
 * Creates a Stripe Checkout Session for a one-time contribution. The caller is
 * responsible for redirecting the browser to the returned `checkoutUrl` — this
 * hook only talks to our own API, never Stripe directly.
 */
export function useCreateContributionCheckout(slug: string) {
	return useMutation({
		mutationFn: (values: CreateContributionFormValues & { successUrl: string; cancelUrl: string }) =>
			apiFetch<ContributionCheckout>(`/public/campaigns/${slug}/contributions`, {
				method: "POST",
				body: {
					amountMinor: values.amountMinor,
					supporterName: values.isAnonymous ? null : values.supporterName || null,
					isAnonymous: values.isAnonymous,
					supporterEmail: values.supporterEmail || null,
					successUrl: values.successUrl,
					cancelUrl: values.cancelUrl,
				},
			}),
	});
}

/**
 * Polls contribution status after the browser returns from Stripe Checkout.
 * Confirmation itself happens via the Stripe webhook, not this poll — this only
 * reflects what's already true in the database, so a short refetch interval
 * while still PENDING is enough (see `pages/marketing/PublicCampaignView.tsx`).
 */
export function useContributionStatus(slug: string, contributionId: string | null) {
	return useQuery({
		queryKey: ["public", "campaigns", slug, "contributions", contributionId] as const,
		queryFn: () => apiFetch<ContributionStatusResult>(`/public/campaigns/${slug}/contributions/${contributionId}`),
		enabled: !!slug && !!contributionId,
		refetchInterval: (query) => (query.state.data?.status === "PENDING" ? 2000 : false),
	});
}

export function useOrgContributions(organizationId: string, campaignId: string) {
	return useQuery({
		queryKey: ["organizations", organizationId, "campaigns", campaignId, "contributions"] as const,
		queryFn: () => apiFetch<ContributionPage>(`/organizations/${organizationId}/campaigns/${campaignId}/contributions`),
		enabled: !!organizationId && !!campaignId,
	});
}
