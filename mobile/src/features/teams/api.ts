import { useQuery } from '@tanstack/react-query';

import { apiFetch } from '@/lib/apiClient';

import type { CoachTeamSummary } from './types';

/** GET .../dashboard/coach/teams — scoped to the caller's real assigned teams (ADR-102), NOT the org-wide team list. */
export function useCoachTeams(organizationId: string | null) {
  return useQuery({
    queryKey: ['organizations', organizationId, 'dashboard', 'coach', 'teams'],
    queryFn: ({ signal }) =>
      apiFetch<CoachTeamSummary[]>(`/organizations/${organizationId}/dashboard/coach/teams`, { signal }),
    enabled: !!organizationId,
  });
}
