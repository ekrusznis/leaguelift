import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { apiFetch } from '@/lib/apiClient';

import type { PayoutAccountResponse, PayoutSummaryResponse } from './types';

const payoutKey = (organizationId: string | null) =>
  ['organizations', organizationId, 'payout-account'] as const;

export function usePayoutAccount(organizationId: string | null) {
  return useQuery({
    queryKey: payoutKey(organizationId),
    queryFn: ({ signal }) =>
      apiFetch<PayoutAccountResponse | null>(
        `/organizations/${organizationId}/payout-account`,
        { signal },
      ),
    enabled: !!organizationId,
  });
}

export function usePayoutSummary(organizationId: string | null) {
  return useQuery({
    queryKey: [...payoutKey(organizationId), 'summary'],
    queryFn: ({ signal }) =>
      apiFetch<PayoutSummaryResponse>(
        `/organizations/${organizationId}/payout-account/summary`,
        { signal },
      ),
    enabled: !!organizationId,
  });
}

export function useStartPayoutOnboarding(organizationId: string | null) {
  return useMutation({
    mutationFn: ({
      refreshUrl,
      returnUrl,
    }: {
      refreshUrl: string;
      returnUrl: string;
    }) =>
      apiFetch<{ onboardingUrl: string }>(
        `/organizations/${organizationId}/payout-account/onboarding-link`,
        {
          method: 'POST',
          body: { refreshUrl, returnUrl },
        },
      ),
  });
}

export function useRefreshPayoutAccount(organizationId: string | null) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () =>
      apiFetch<PayoutAccountResponse>(
        `/organizations/${organizationId}/payout-account/refresh`,
        { method: 'POST' },
      ),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: payoutKey(organizationId) });
      await queryClient.invalidateQueries({ queryKey: [...payoutKey(organizationId), 'summary'] });
    },
  });
}

export function useTriggerPayoutTransfer(organizationId: string | null) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () =>
      apiFetch<PayoutSummaryResponse>(
        `/organizations/${organizationId}/payout-account/transfer`,
        { method: 'POST' },
      ),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: [...payoutKey(organizationId), 'summary'] });
      await queryClient.invalidateQueries({ queryKey: ['me', 'action-center'] });
    },
  });
}
