import { createContext, useContext, useState, type ReactNode } from 'react';

import { useDashboardContext } from '@/features/dashboard/api';

import { useCoachTeams } from './api';
import type { CoachTeamSummary } from './types';

interface CoachContextValue {
  organizationId: string | null;
  teams: CoachTeamSummary[];
  selectedTeamId: string | null;
  setSelectedTeamId: (id: string) => void;
  isLoading: boolean;
  isError: boolean;
}

const CoachContext = createContext<CoachContextValue | null>(null);

/**
 * Shared "which org / which team" state for the (tabs) group — Dashboard, Calendar,
 * and Teams all need the coach's real organizationId + currently-selected team.
 * Defaults to the first team, matching the backend's own default (coach-dashboard
 * endpoints default ?teamId= to the alphabetically-first accessible team, ADR-102).
 *
 * `teamOverride` only holds a value once the coach has manually switched teams —
 * the effective selection is derived at render time (`teamOverride ?? teams[0]`)
 * rather than synced into state via an effect, which would cause an extra
 * cascading render on every teams-query update for no benefit.
 */
export function CoachContextProvider({ children }: { children: ReactNode }) {
  const dashboardContext = useDashboardContext(true);
  const organizationId = dashboardContext.data?.organizationId ?? null;
  const teamsQuery = useCoachTeams(organizationId);
  const teams = teamsQuery.data ?? [];
  const [teamOverride, setTeamOverride] = useState<string | null>(null);

  const selectedTeamId = teams.some((t) => t.teamId === teamOverride) ? teamOverride : (teams[0]?.teamId ?? null);

  return (
    <CoachContext.Provider
      value={{
        organizationId,
        teams,
        selectedTeamId,
        setSelectedTeamId: setTeamOverride,
        isLoading: dashboardContext.isLoading || teamsQuery.isLoading,
        isError: dashboardContext.isError || teamsQuery.isError,
      }}>
      {children}
    </CoachContext.Provider>
  );
}

export function useCoach(): CoachContextValue {
  const context = useContext(CoachContext);
  if (!context) throw new Error('useCoach must be used within CoachContextProvider.');
  return context;
}
