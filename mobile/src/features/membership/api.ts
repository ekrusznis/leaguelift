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

/** Owner-only server-side; the target must already be an active Administrator member. */
export function useTransferOwnership(organizationId: string | null) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (newOwnerMembershipId: string) =>
      apiFetch(`/organizations/${organizationId}/members/ownership-transfer`, {
        method: 'PATCH',
        body: { newOwnerMembershipId },
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['organizations', organizationId, 'members'] });
    },
  });
}

export interface OwnershipTransferInvitation {
  id: string;
  organizationId: string;
  email: string;
  status: string;
  expiresAt: string;
  createdAt: string;
}

/** Owner-only server-side — pass `enabled: false` for any viewer who isn't the current owner. */
export function usePendingOwnershipTransferInvitation(organizationId: string | null, enabled: boolean) {
  return useQuery({
    queryKey: ['organizations', organizationId, 'ownership-transfer-invitation'],
    queryFn: () =>
      apiFetch<OwnershipTransferInvitation | null>(`/organizations/${organizationId}/ownership-transfer-invitations/pending`),
    enabled: !!organizationId && enabled,
  });
}

export function useInviteOwnershipTransfer(organizationId: string | null) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (email: string) =>
      apiFetch<OwnershipTransferInvitation>(`/organizations/${organizationId}/ownership-transfer-invitations`, {
        method: 'POST',
        body: { email },
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['organizations', organizationId, 'ownership-transfer-invitation'] });
    },
  });
}

export function useRevokeOwnershipTransferInvitation(organizationId: string | null) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (invitationId: string) =>
      apiFetch(`/organizations/${organizationId}/ownership-transfer-invitations/${invitationId}`, { method: 'DELETE' }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['organizations', organizationId, 'ownership-transfer-invitation'] });
    },
  });
}
