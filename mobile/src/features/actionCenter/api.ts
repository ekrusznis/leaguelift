import { useQuery } from '@tanstack/react-query';

import { apiFetch } from '@/lib/apiClient';

import type { ActionCenter } from './types';

/** Matches frontend's 60s refetchInterval — Action Center is meant to feel close to live without a push/websocket channel. */
export function useActionCenter() {
  return useQuery({
    queryKey: ['me', 'action-center'],
    queryFn: ({ signal }) => apiFetch<ActionCenter>('/me/action-center', { signal }),
    refetchInterval: 60_000,
  });
}
