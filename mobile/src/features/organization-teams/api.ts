import { useQuery } from '@tanstack/react-query';

import { apiFetch } from '@/lib/apiClient';
import type { PageResponse } from '@/lib/types';

import type { OrgTeamResponse } from './types';

/** GET /organizations/{organizationId}/teams — org-wide team list, read-only in this slice (ADR-105). */
export function useOrgTeams(organizationId: string | null) {
  return useQuery({
    queryKey: ['organizations', organizationId, 'teams'],
    queryFn: ({ signal }) => apiFetch<PageResponse<OrgTeamResponse>>(`/organizations/${organizationId}/teams?size=100`, { signal }),
    enabled: !!organizationId,
  });
}

export function useOrgTeam(organizationId: string | null, teamId: string | null) {
  return useQuery({
    queryKey: ['organizations', organizationId, 'teams', teamId],
    queryFn: ({ signal }) => apiFetch<OrgTeamResponse>(`/organizations/${organizationId}/teams/${teamId}`, { signal }),
    enabled: !!organizationId && !!teamId,
  });
}
