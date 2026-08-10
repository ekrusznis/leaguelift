import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { apiFetch } from '@/lib/apiClient';

import type { EventResponse, EventRsvpsResponse, SubmittableRsvpStatus } from './types';

/** GET /teams/{teamId}/events?organizationId= — a coach's team schedule (ADR-102). Not PageResponse-wrapped; a plain array. */
export function useTeamEvents(organizationId: string | null, teamId: string | null) {
  return useQuery({
    queryKey: ['teams', teamId, 'events', organizationId],
    queryFn: ({ signal }) =>
      apiFetch<EventResponse[]>(`/teams/${teamId}/events?organizationId=${organizationId}&size=100`, { signal }),
    enabled: !!organizationId && !!teamId,
  });
}

/** GET /households/{householdId}/events?organizationId= — real combined schedule across every linked athlete's teams, server-side union (ADR-103). */
export function useHouseholdEvents(organizationId: string | null, householdId: string | null) {
  return useQuery({
    queryKey: ['households', householdId, 'events', organizationId],
    queryFn: ({ signal }) =>
      apiFetch<EventResponse[]>(`/households/${householdId}/events?organizationId=${organizationId}&size=100`, { signal }),
    enabled: !!organizationId && !!householdId,
  });
}

export function useEvent(organizationId: string | null, eventId: string | null) {
  return useQuery({
    queryKey: ['organizations', organizationId, 'events', eventId],
    queryFn: ({ signal }) => apiFetch<EventResponse>(`/organizations/${organizationId}/events/${eventId}`, { signal }),
    enabled: !!organizationId && !!eventId,
  });
}

export function useEventRsvps(organizationId: string | null, eventId: string | null) {
  return useQuery({
    queryKey: ['events', eventId, 'rsvps', organizationId],
    queryFn: ({ signal }) =>
      apiFetch<EventRsvpsResponse>(`/events/${eventId}/rsvps?organizationId=${organizationId}`, { signal }),
    enabled: !!organizationId && !!eventId,
  });
}

export function useSubmitRsvp(organizationId: string | null, eventId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ participantId, response, note }: { participantId: string; response: SubmittableRsvpStatus; note?: string }) =>
      apiFetch(`/events/${eventId}/participants/${participantId}/rsvp?organizationId=${organizationId}`, {
        method: 'PUT',
        body: { response, note },
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['events', eventId, 'rsvps'] });
    },
  });
}
