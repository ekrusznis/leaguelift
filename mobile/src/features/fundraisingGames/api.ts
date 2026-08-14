import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { apiFetch } from '@/lib/apiClient';

import type { FundraisingGame, FundraisingGamePage } from './types';

const gamesKey = (organizationId: string | null, campaignId: string | null) =>
	['organizations', organizationId, 'campaigns', campaignId, 'games'] as const;

export function useGames(organizationId: string | null, campaignId: string | null) {
	return useQuery({
		queryKey: gamesKey(organizationId, campaignId),
		queryFn: ({ signal }) =>
			apiFetch<FundraisingGamePage>(
				`/organizations/${organizationId}/campaigns/${campaignId}/games?size=50`,
				{ signal },
			),
		enabled: !!organizationId && !!campaignId,
	});
}

export function useFundraisingGame(organizationId: string | null, campaignId: string | null) {
	return useQuery({
		queryKey: [...gamesKey(organizationId, campaignId), 'current'],
		queryFn: ({ signal }) =>
			apiFetch<FundraisingGame | null>(
				`/organizations/${organizationId}/campaigns/${campaignId}/games/current`,
				{ signal },
			),
		enabled: !!organizationId && !!campaignId,
	});
}

export function useFundraisingGameEntries(organizationId: string | null, campaignId: string | null, gameId: string | null) {
	return useQuery({
		queryKey: [...gamesKey(organizationId, campaignId), gameId, 'entries'],
		queryFn: ({ signal }) =>
			apiFetch<any[]>(
				`/organizations/${organizationId}/campaigns/${campaignId}/games/${gameId}/entries?size=50`,
				{ signal },
			),
		enabled: !!organizationId && !!campaignId && !!gameId,
	});
}

export function useCreateFundraisingGame(organizationId: string | null, campaignId: string | null) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (data: any) =>
			apiFetch<FundraisingGame>(
				`/organizations/${organizationId}/campaigns/${campaignId}/games`,
				{ method: 'POST', body: data },
			),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: gamesKey(organizationId, campaignId) });
		},
	});
}

export function useUpdateFundraisingGame(organizationId: string | null, campaignId: string | null) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: ({ gameId, data }: { gameId: string; data: any }) =>
			apiFetch<FundraisingGame>(`/organizations/${organizationId}/campaigns/${campaignId}/games/${gameId}`, {
				method: 'PUT',
				body: data,
			}),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: gamesKey(organizationId, campaignId) });
		},
	});
}

export function useOpenFundraisingGame(organizationId: string | null, campaignId: string | null) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (gameId: string) =>
			apiFetch<FundraisingGame>(`/organizations/${organizationId}/campaigns/${campaignId}/games/${gameId}/open`, {
				method: 'POST',
			}),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: gamesKey(organizationId, campaignId) });
		},
	});
}

export function useCloseFundraisingGame(organizationId: string | null, campaignId: string | null) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (gameId: string) =>
			apiFetch<FundraisingGame>(`/organizations/${organizationId}/campaigns/${campaignId}/games/${gameId}/close`, {
				method: 'POST',
			}),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: gamesKey(organizationId, campaignId) });
		},
	});
}

export function useDrawFundraisingGameWinner(organizationId: string | null, campaignId: string | null) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: ({ gameId, drawId }: { gameId: string; drawId: string }) =>
			apiFetch<any>(`/organizations/${organizationId}/campaigns/${campaignId}/games/${gameId}/draws/${drawId}`, {
				method: 'POST',
			}),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: gamesKey(organizationId, campaignId) });
		},
	});
}

