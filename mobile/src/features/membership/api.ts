import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { apiFetch } from '@/lib/apiClient';
import type { PageResponse } from '@/lib/types';

import type { MembershipResponse, MembershipRole } from './types';

/** GET /organizations/{organizationId}/members — any active member can view (ADR-105). */
export function useMembers(organizationId: string | null) {
  return useQuery({
    queryKey: ['organizations', organizationId, 'members'],
    queryFn: ({ signal }) => apiFetch<PageResponse<MembershipResponse>>(`/organizations/${organizationId}/members?size=100`, { signal }),
    enabled: !!organizationId,
  });
}

/** PATCH /organizations/{organizationId}/members/{memberId} — manager-tier only server-side (OWNER/ADMINISTRATOR); cannot target the OWNER role. */
export function useUpdateMemberRole(organizationId: string | null) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ memberId, role }: { memberId: string; role: MembershipRole }) =>
      apiFetch(`/organizations/${organizationId}/members/${memberId}`, { method: 'PATCH', body: { role } }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['organizations', organizationId, 'members'] });
    },
  });
}

export function useRevokeMember(organizationId: string | null) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (memberId: string) => apiFetch(`/organizations/${organizationId}/members/${memberId}`, { method: 'DELETE' }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['organizations', organizationId, 'members'] });
    },
  });
}
