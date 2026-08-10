import { useQuery } from '@tanstack/react-query';

import { apiFetch } from '@/lib/apiClient';

import type { ParticipantResponse } from './types';

/** GET .../teams/{teamId}/participants — explicitly documented server-side as "a coach's team roster picker" (ADR-102). Plain array, not paginated. */
export function useTeamRoster(organizationId: string | null, teamId: string | null) {
  return useQuery({
    queryKey: ['organizations', organizationId, 'teams', teamId, 'participants'],
    queryFn: ({ signal }) =>
      apiFetch<ParticipantResponse[]>(`/organizations/${organizationId}/teams/${teamId}/participants`, { signal }),
    enabled: !!organizationId && !!teamId,
  });
}
