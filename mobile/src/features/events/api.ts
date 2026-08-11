import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { apiFetch } from '@/lib/apiClient';

import type {
  CreateEventRequest,
  DirectionsResponse,
  EventResponse,
  EventRsvpsResponse,
  SubmittableRsvpStatus,
  TimezoneDefaultResponse,
  UpdateEventRequest,
} from './types';

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

/** GET /participants/{participantId}/events?organizationId= — an athlete's own real schedule (used for both self and guardian-picked athletes). */
export function useParticipantEvents(organizationId: string | null, participantId: string | null) {
  return useQuery({
    queryKey: ['participants', participantId, 'events', organizationId],
    queryFn: ({ signal }) =>
      apiFetch<EventResponse[]>(`/participants/${participantId}/events?organizationId=${organizationId}&size=100`, { signal }),
    enabled: !!organizationId && !!participantId,
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

/** GET .../events/timezone-default — the create-event form's smart pre-fill (team override -> org default), never used to validate/snapshot the actual event (ADR-107). */
export function useEventTimezoneDefault(organizationId: string | null, teamId: string | null) {
  return useQuery({
    queryKey: ['organizations', organizationId, 'events', 'timezone-default', teamId],
    queryFn: ({ signal }) =>
      apiFetch<TimezoneDefaultResponse>(
        `/organizations/${organizationId}/events/timezone-default${teamId ? `?teamId=${teamId}` : ''}`,
        { signal },
      ),
    enabled: !!organizationId,
  });
}

export function useCreateEvent(organizationId: string | null) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: CreateEventRequest) =>
      apiFetch<EventResponse>(`/organizations/${organizationId}/events`, { method: 'POST', body: request }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['teams'] });
      queryClient.invalidateQueries({ queryKey: ['organizations', organizationId, 'dashboard'] });
    },
  });
}

export function useUpdateEvent(organizationId: string | null, eventId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: UpdateEventRequest) =>
      apiFetch<EventResponse>(`/organizations/${organizationId}/events/${eventId}`, { method: 'PATCH', body: request }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['organizations', organizationId, 'events', eventId] });
      queryClient.invalidateQueries({ queryKey: ['teams'] });
    },
  });
}

export function usePublishEvent(organizationId: string | null, eventId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => apiFetch<EventResponse>(`/organizations/${organizationId}/events/${eventId}/publish`, { method: 'POST' }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['organizations', organizationId, 'events', eventId] });
      queryClient.invalidateQueries({ queryKey: ['teams'] });
    },
  });
}

/** GET .../events/{eventId}/directions — real, keyless Google Maps URL (ADR-028); url is null when the event has no location data. */
export function useEventDirections(organizationId: string | null, eventId: string | null) {
  return useQuery({
    queryKey: ['organizations', organizationId, 'events', eventId, 'directions'],
    queryFn: ({ signal }) =>
      apiFetch<DirectionsResponse>(`/organizations/${organizationId}/events/${eventId}/directions`, { signal }),
    enabled: !!organizationId && !!eventId,
  });
}
