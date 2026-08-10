import { useQuery } from '@tanstack/react-query';

import { apiFetch } from '@/lib/apiClient';

import type { PayoutAccountResponse, PayoutSummaryResponse } from './types';

/**
 * GET /organizations/{organizationId}/payout-account(/summary) — read-only this slice
 * (ADR-105). The `onboarding-link`/`refresh`/`transfer` actions are real but
 * deliberately deferred: onboarding-link is a Stripe-hosted WebView handoff and
 * transfer is a live money-movement action, both worth validating the read-only view
 * against real orgs before shipping.
 */
export function usePayoutAccount(organizationId: string | null) {
  return useQuery({
    queryKey: ['organizations', organizationId, 'payout-account'],
    queryFn: ({ signal }) => apiFetch<PayoutAccountResponse | null>(`/organizations/${organizationId}/payout-account`, { signal }),
    enabled: !!organizationId,
  });
}

export function usePayoutSummary(organizationId: string | null) {
  return useQuery({
    queryKey: ['organizations', organizationId, 'payout-account', 'summary'],
    queryFn: ({ signal }) =>
      apiFetch<PayoutSummaryResponse>(`/organizations/${organizationId}/payout-account/summary`, { signal }),
    enabled: !!organizationId,
  });
}
