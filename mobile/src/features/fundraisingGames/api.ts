import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { apiFetch } from '@/lib/apiClient';

import type { FundraisingGame, FundraisingGameEntry, FundraisingGameType } from './types';

export const gameKey = (organizationId: string | null, campaignId: string | null) =>
  ['organizations', organizationId, 'campaigns', campaignId, 'free-game'] as const;

export interface FundraisingGameInput {
  gameType: FundraisingGameType;
  title: string;
  instructions: string | null;
  prizeDescription: string | null;
  maxEntries: number | null;
  entriesPerPerson: number;
  rows: number | null;
  cols: number | null;
}

export function useFundraisingGame(organizationId: string | null, campaignId: string | null) {
  return useQuery({
    queryKey: gameKey(organizationId, campaignId),
    queryFn: ({ signal }) =>
      apiFetch<FundraisingGame | null>(
        `/organizations/${organizationId}/campaigns/${campaignId}/game`,
        { signal },
      ),
    enabled: !!organizationId && !!campaignId,
    retry: false,
  });
}

export function useCreateFundraisingGame(organizationId: string | null, campaignId: string | null) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: FundraisingGameInput) =>
      apiFetch<FundraisingGame>(`/organizations/${organizationId}/campaigns/${campaignId}/game`, {
        method: 'POST',
        body: data,
      }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: gameKey(organizationId, campaignId) }),
  });
}

export function useUpdateFundraisingGame(organizationId: string | null, campaignId: string | null) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: Omit<FundraisingGameInput, 'gameType'>) =>
      apiFetch<FundraisingGame>(`/organizations/${organizationId}/campaigns/${campaignId}/game`, {
        method: 'PATCH',
        body: data,
      }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: gameKey(organizationId, campaignId) }),
  });
}

function useFundraisingGameAction(
  organizationId: string | null,
  campaignId: string | null,
  action: 'open' | 'close',
) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () =>
      apiFetch<FundraisingGame>(
        `/organizations/${organizationId}/campaigns/${campaignId}/game/${action}`,
        { method: 'POST' },
      ),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: gameKey(organizationId, campaignId) }),
  });
}

export function useOpenFundraisingGame(organizationId: string | null, campaignId: string | null) {
  return useFundraisingGameAction(organizationId, campaignId, 'open');
}

export function useCloseFundraisingGame(organizationId: string | null, campaignId: string | null) {
  return useFundraisingGameAction(organizationId, campaignId, 'close');
}

export function useDrawFundraisingGameWinner(organizationId: string | null, campaignId: string | null) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () =>
      apiFetch<FundraisingGameEntry>(
        `/organizations/${organizationId}/campaigns/${campaignId}/game/draw-winner`,
        { method: 'POST' },
      ),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: gameKey(organizationId, campaignId) }),
  });
}

/** Compatibility helper for screens that still want the full entry array. Prefer searchApi for list UI. */
export function useFundraisingGameEntries(
  organizationId: string | null,
  campaignId: string | null,
  enabled = true,
) {
  return useQuery({
    queryKey: [...gameKey(organizationId, campaignId), 'entries'],
    queryFn: ({ signal }) =>
      apiFetch<FundraisingGameEntry[]>(
        `/organizations/${organizationId}/campaigns/${campaignId}/game/entries`,
        { signal },
      ),
    enabled: enabled && !!organizationId && !!campaignId,
  });
}
