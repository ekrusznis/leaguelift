import { createContext, useContext, useMemo, type ReactNode } from 'react';

import { useContexts } from '@/features/dashboard/api';

interface AthleteSelfContextValue {
  organizationId: string | null;
  participantId: string | null;
  isLoading: boolean;
  isError: boolean;
}

const AthleteSelfContext = createContext<AthleteSelfContextValue | null>(null);

/**
 * Shared "which participant am I" state for the athlete (tabs) group — mirrors
 * CoachContext/HouseholdContext, but sourced from GET /me/contexts rather than
 * /me/dashboard-context, since dashboard-context has no participant field. There is
 * deliberately no endpoint to create this self-link (linkAthleteSelf has no controller
 * route) — an athlete without one just sees real, honest empty states everywhere.
 */
export function AthleteSelfContextProvider({ children }: { children: ReactNode }) {
  const contextsQuery = useContexts(true);

  const athleteContext = useMemo(() => contextsQuery.data?.find((c) => c.contextType === 'ATHLETE') ?? null, [contextsQuery.data]);

  return (
    <AthleteSelfContext.Provider
      value={{
        organizationId: athleteContext?.organizationId ?? null,
        participantId: athleteContext?.resourceId ?? null,
        isLoading: contextsQuery.isLoading,
        isError: contextsQuery.isError,
      }}>
      {children}
    </AthleteSelfContext.Provider>
  );
}

export function useAthleteSelf(): AthleteSelfContextValue {
  const context = useContext(AthleteSelfContext);
  if (!context) throw new Error('useAthleteSelf must be used within AthleteSelfContextProvider.');
  return context;
}
