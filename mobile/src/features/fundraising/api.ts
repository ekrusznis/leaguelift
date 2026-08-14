import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { apiFetch } from '@/lib/apiClient';

import type { Campaign, CampaignPage, FundraisingSettings } from './types';

const campaignsKey = (organizationId: string | null) => ['organizations', organizationId, 'campaigns'] as const;
const settingsKey = (organizationId: string | null) => ['organizations', organizationId, 'fundraising', 'settings'] as const;

export function useCampaigns(organizationId: string | null) {
	return useQuery({
		queryKey: campaignsKey(organizationId),
		queryFn: ({ signal }) =>
			apiFetch<CampaignPage>(`/organizations/${organizationId}/campaigns?size=50`, { signal }),
		enabled: !!organizationId,
	});
}

export function useCampaign(organizationId: string | null, campaignId: string | null) {
	return useQuery({
		queryKey: [...campaignsKey(organizationId), campaignId],
		queryFn: ({ signal }) =>
			apiFetch<Campaign>(`/organizations/${organizationId}/campaigns/${campaignId}`, { signal }),
		enabled: !!organizationId && !!campaignId,
	});
}

export function useFundraisingSettings(organizationId: string | null) {
	return useQuery({
		queryKey: settingsKey(organizationId),
		queryFn: ({ signal }) =>
			apiFetch<FundraisingSettings>(`/organizations/${organizationId}/fundraising/settings`, { signal }),
		enabled: !!organizationId,
	});
}

export function useCreateCampaign(organizationId: string | null) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (data: any) =>
			apiFetch<Campaign>(`/organizations/${organizationId}/campaigns`, { method: 'POST', body: data }),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: campaignsKey(organizationId) });
		},
	});
}

export function useUpdateCampaign(organizationId: string | null, campaignId: string | null) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (data: any) =>
			apiFetch<Campaign>(`/organizations/${organizationId}/campaigns/${campaignId}`, {
				method: 'PUT',
				body: data,
			}),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: campaignsKey(organizationId) });
		},
	});
}

export function useRequestCampaignActivation(organizationId: string | null) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (campaignId: string) =>
			apiFetch<Campaign>(`/organizations/${organizationId}/campaigns/${campaignId}/request-activation`, {
				method: 'POST',
			}),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: campaignsKey(organizationId) });
		},
	});
}

export function useApproveCampaign(organizationId: string | null) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (campaignId: string) =>
			apiFetch<Campaign>(`/organizations/${organizationId}/campaigns/${campaignId}/approve`, {
				method: 'POST',
			}),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: campaignsKey(organizationId) });
		},
	});
}

export function useReturnCampaignToDraft(organizationId: string | null) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (campaignId: string) =>
			apiFetch<Campaign>(`/organizations/${organizationId}/campaigns/${campaignId}/draft`, {
				method: 'POST',
			}),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: campaignsKey(organizationId) });
		},
	});
}

export function useUpdateCampaignStatus(organizationId: string | null) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: ({ campaignId, status }: { campaignId: string; status: string }) =>
			apiFetch<Campaign>(`/organizations/${organizationId}/campaigns/${campaignId}/status`, {
				method: 'PUT',
				body: { status },
			}),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: campaignsKey(organizationId) });
		},
	});
}

export function useUpdateFundraisingSettings(organizationId: string | null) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (data: any) =>
			apiFetch<FundraisingSettings>(`/organizations/${organizationId}/fundraising/settings`, {
				method: 'PUT',
				body: data,
			}),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: settingsKey(organizationId) });
		},
	});
}

export function useContributions(organizationId: string | null, campaignId: string | null) {
	return useQuery({
		queryKey: ['organizations', organizationId, 'campaigns', campaignId, 'contributions'],
		queryFn: ({ signal }) =>
			apiFetch<any>(
				`/organizations/${organizationId}/campaigns/${campaignId}/contributions?size=50`,
				{ signal },
			),
		enabled: !!organizationId && !!campaignId,
	});
}

export function useCampaignShareLink(organizationId: string | null) {
	return useMutation({
		mutationFn: (url: string) =>
			apiFetch<any>(`/organizations/${organizationId}/campaigns/share-link`, {
				method: 'POST',
				body: { url },
			}),
	});
}

