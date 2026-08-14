import { useQuery } from '@tanstack/react-query';

import { apiFetch } from '@/lib/apiClient';

export interface TeamStaffMember {
  userId: string;
  displayName: string;
  role: string;
  roleLabel: string;
}

export function useTeamStaff(organizationId: string | null, teamId: string | null) {
  return useQuery({
    queryKey: ['organizations', organizationId, 'teams', teamId, 'staff'],
    queryFn: ({ signal }) =>
      apiFetch<TeamStaffMember[]>(`/organizations/${organizationId}/teams/${teamId}/staff`, { signal }),
    enabled: !!organizationId && !!teamId,
  });
}
