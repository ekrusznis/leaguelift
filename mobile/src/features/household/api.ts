import { useQuery } from '@tanstack/react-query';

import { apiFetch } from '@/lib/apiClient';

import type { AthleteSummary, HouseholdResponse, ParticipantResponse, ParticipantTeamResponse } from './types';

/** GET .../dashboard/parent/athletes — pre-joined participant + team names, the parent-persona equivalent of coach's "my teams" (ADR-103). */
export function useMyAthletes(organizationId: string | null, householdId: string | null) {
  return useQuery({
    queryKey: ['organizations', organizationId, 'households', householdId, 'dashboard', 'parent', 'athletes'],
    queryFn: ({ signal }) =>
      apiFetch<AthleteSummary[]>(
        `/organizations/${organizationId}/households/${householdId}/dashboard/parent/athletes`,
        { signal },
      ),
    enabled: !!organizationId && !!householdId,
  });
}

/** GET .../households/{id}/participants — full participant fields (DOB etc.), used where AthleteSummary isn't enough. */
export function useHouseholdParticipants(organizationId: string | null, householdId: string | null) {
  return useQuery({
    queryKey: ['organizations', organizationId, 'households', householdId, 'participants'],
    queryFn: ({ signal }) =>
      apiFetch<ParticipantResponse[]>(`/organizations/${organizationId}/households/${householdId}/participants`, { signal }),
    enabled: !!organizationId && !!householdId,
  });
}

export function useParticipantTeams(organizationId: string | null, participantId: string | null) {
  return useQuery({
    queryKey: ['organizations', organizationId, 'participants', participantId, 'teams'],
    queryFn: ({ signal }) =>
      apiFetch<ParticipantTeamResponse[]>(`/organizations/${organizationId}/participants/${participantId}/teams`, { signal }),
    enabled: !!organizationId && !!participantId,
  });
}

export function useHousehold(organizationId: string | null, householdId: string | null) {
  return useQuery({
    queryKey: ['organizations', organizationId, 'households', householdId],
    queryFn: ({ signal }) => apiFetch<HouseholdResponse>(`/organizations/${organizationId}/households/${householdId}`, { signal }),
    enabled: !!organizationId && !!householdId,
  });
}
