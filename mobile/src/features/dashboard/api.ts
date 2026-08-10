import { useQuery } from '@tanstack/react-query';

import { apiFetch } from '@/lib/apiClient';

import type { ContextResponse, DashboardContext } from './types';

export function useDashboardContext(enabled: boolean) {
  return useQuery({
    queryKey: ['me', 'dashboard-context'],
    queryFn: ({ signal }) => apiFetch<DashboardContext>('/me/dashboard-context', { signal }),
    enabled,
  });
}

/** GET /me/contexts — the capability-based context list; athlete self-discovery filters this for contextType === 'ATHLETE'. */
export function useContexts(enabled: boolean) {
  return useQuery({
    queryKey: ['me', 'contexts'],
    queryFn: ({ signal }) => apiFetch<ContextResponse[]>('/me/contexts', { signal }),
    enabled,
  });
}
