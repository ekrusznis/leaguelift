import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { apiFetch } from '@/lib/apiClient';

import type {
  CreateMessageContactRestrictionRequest,
  GuardianMessagingParticipantResponse,
  LiftMessageContactRestrictionRequest,
  MessageContactRestrictionResponse,
} from './types';

/**
 * Guardian communication controls (ADR-108) — real endpoints that already existed on
 * the backend (built alongside the Athlete-messaging SafeSport gate, ADR-104's
 * research) but were never wired to mobile; matches the same real feature already
 * shipped on web as `GuardianMessageSafetyControls` (frontend/src/features/messaging/
 * SafeSportPolicyPanel.tsx). Lets a guardian stop staff→athlete messages or all
 * messaging entirely for one of their own linked athletes, and lift that restriction
 * later with a required note.
 */
export function useGuardianMessagingParticipants() {
  return useQuery({
    queryKey: ['me', 'messaging', 'guardian-participants'],
    queryFn: ({ signal }) =>
      apiFetch<GuardianMessagingParticipantResponse[]>('/me/messaging/guardian-participants', { signal }),
  });
}

export function useMyContactRestrictions() {
  return useQuery({
    queryKey: ['me', 'messaging', 'contact-restrictions'],
    queryFn: ({ signal }) => apiFetch<MessageContactRestrictionResponse[]>('/me/messaging/contact-restrictions', { signal }),
  });
}

export function useCreateContactRestriction() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: CreateMessageContactRestrictionRequest) =>
      apiFetch<MessageContactRestrictionResponse>('/me/messaging/contact-restrictions', { method: 'POST', body: request }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['me', 'messaging', 'contact-restrictions'] });
    },
  });
}

export function useLiftContactRestriction() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ restrictionId, note }: { restrictionId: string } & LiftMessageContactRestrictionRequest) =>
      apiFetch<MessageContactRestrictionResponse>(`/me/messaging/contact-restrictions/${restrictionId}/lift`, {
        method: 'POST',
        body: { note },
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['me', 'messaging', 'contact-restrictions'] });
    },
  });
}
