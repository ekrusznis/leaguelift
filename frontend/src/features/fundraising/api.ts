import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiFetch } from "../../lib/apiClient";
import type { CreateCampaignFormValues } from "./schema";
import type { Campaign, CampaignPage, CampaignStatus, PublicCampaign } from "./types";

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
