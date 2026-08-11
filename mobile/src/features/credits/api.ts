import { useQuery } from '@tanstack/react-query';

import { apiFetch } from '@/lib/apiClient';

import type { FamilyCreditBalanceResponse, FamilyCreditGrantResponse } from './types';

export function useFamilyCreditBalance(organizationId: string | null, householdId: string | null) {
  return useQuery({
    queryKey: ['organizations', organizationId, 'households', householdId, 'credits', 'balance'],
    queryFn: ({ signal }) =>
      apiFetch<FamilyCreditBalanceResponse>(
        `/organizations/${organizationId}/households/${householdId}/credits/balance`,
        { signal },
      ),
    enabled: !!organizationId && !!householdId,
  });
}

export function useFamilyCreditGrants(organizationId: string | null, householdId: string | null) {
  return useQuery({
    queryKey: ['organizations', organizationId, 'households', householdId, 'credits', 'grants'],
    queryFn: ({ signal }) =>
      apiFetch<FamilyCreditGrantResponse[]>(
        `/organizations/${organizationId}/households/${householdId}/credits/grants`,
        { signal },
      ),
    enabled: !!organizationId && !!householdId,
  });
}
