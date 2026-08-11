import { useQuery } from '@tanstack/react-query';

import { apiFetch } from '@/lib/apiClient';

import type { AthleteOverviewResponse, AthleteTeamSummary, GuardianSummary } from './types';

/**
 * GET /me/dashboard/athlete/* — self-resolving from the caller's own
 * role_assignment(PARTICIPANT, ATHLETE_SELF) link, no organizationId/participantId
 * path params needed. `recent-history` and `orders` are confirmed hardcoded
 * emptyList() server-side (AthleteDashboardService) — deliberately not wired here,
 * see rally26-mobile-scaffold memory / ADR-104.
 */
export function useAthleteOverview(enabled: boolean) {
  return useQuery({
    queryKey: ['me', 'dashboard', 'athlete', 'overview'],
    queryFn: ({ signal }) => apiFetch<AthleteOverviewResponse>('/me/dashboard/athlete/overview', { signal }),
    enabled,
  });
}

export function useAthleteTeams(enabled: boolean) {
  return useQuery({
    queryKey: ['me', 'dashboard', 'athlete', 'teams'],
    queryFn: ({ signal }) => apiFetch<AthleteTeamSummary[]>('/me/dashboard/athlete/teams', { signal }),
    enabled,
  });
}

export function useAthleteGuardians(enabled: boolean) {
  return useQuery({
    queryKey: ['me', 'dashboard', 'athlete', 'guardians'],
    queryFn: ({ signal }) => apiFetch<GuardianSummary[]>('/me/dashboard/athlete/guardians', { signal }),
    enabled,
  });
}
