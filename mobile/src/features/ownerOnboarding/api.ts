import { useQuery } from '@tanstack/react-query';

import { apiFetch } from '@/lib/apiClient';
import type { OwnerOnboarding } from './types';

export const ownerOnboardingQueryKey = ['owner-onboarding'] as const;

/**
 * Authenticated mobile mirror of GET /api/v1/owner-onboarding.
 * The shared apiClient supplies the current bearer token; the backend remains the
 * authority for both the unfinished step and subscription/organization activation.
 */
export function useOwnerOnboarding(enabled: boolean) {
  return useQuery({
    queryKey: ownerOnboardingQueryKey,
    queryFn: ({ signal }) => apiFetch<OwnerOnboarding>('/owner-onboarding', { signal }),
    enabled,
    staleTime: 0,
    retry: 1,
  });
}
