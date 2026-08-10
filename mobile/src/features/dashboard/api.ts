import { useQuery } from '@tanstack/react-query';

import { apiFetch } from '@/lib/apiClient';

import type { DashboardContext } from './types';

export function useDashboardContext(enabled: boolean) {
  return useQuery({
    queryKey: ['me', 'dashboard-context'],
    queryFn: ({ signal }) => apiFetch<DashboardContext>('/me/dashboard-context', { signal }),
    enabled,
  });
}
