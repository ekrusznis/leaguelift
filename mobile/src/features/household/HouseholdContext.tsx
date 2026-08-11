import { createContext, useContext, type ReactNode } from 'react';

import { useDashboardContext } from '@/features/dashboard/api';

import { useMyAthletes } from './api';
import type { AthleteSummary } from './types';

interface HouseholdContextValue {
  organizationId: string | null;
  householdId: string | null;
  athletes: AthleteSummary[];
  isLoading: boolean;
  isError: boolean;
}

const HouseholdContext = createContext<HouseholdContextValue | null>(null);

/** Shared "which org / which household" state for the parent (tabs) group — mirrors CoachContext (ADR-103). */
export function HouseholdContextProvider({ children }: { children: ReactNode }) {
  const dashboardContext = useDashboardContext(true);
  const organizationId = dashboardContext.data?.organizationId ?? null;
  const householdId = dashboardContext.data?.householdId ?? null;
  const athletesQuery = useMyAthletes(organizationId, householdId);

  return (
    <HouseholdContext.Provider
      value={{
        organizationId,
        householdId,
        athletes: athletesQuery.data ?? [],
        isLoading: dashboardContext.isLoading || athletesQuery.isLoading,
        isError: dashboardContext.isError || athletesQuery.isError,
      }}>
      {children}
    </HouseholdContext.Provider>
  );
}

export function useHouseholdCtx(): HouseholdContextValue {
  const context = useContext(HouseholdContext);
  if (!context) throw new Error('useHouseholdCtx must be used within HouseholdContextProvider.');
  return context;
}
