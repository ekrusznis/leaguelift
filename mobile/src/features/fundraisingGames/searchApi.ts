import { useInfiniteQuery } from '@tanstack/react-query';

import { apiFetch } from '@/lib/apiClient';
import type { PageResponse } from '@/lib/types';

import { gameKey } from './api';
import type { FundraisingGameEntry } from './types';

export type GameEntrySearchSort = 'NEWEST' | 'OLDEST' | 'NAME_ASC';

export function useInfiniteFundraisingGameEntrySearch(
  organizationId: string | null,
  campaignId: string | null,
  filters: {
    q?: string;
    winnerOnly?: boolean;
    sort?: GameEntrySearchSort;
  },
  enabled = true,
) {
  return useInfiniteQuery({
    queryKey: [...gameKey(organizationId, campaignId), 'entries', 'search', filters],
    queryFn: ({ pageParam, signal }) => {
      const search = new URLSearchParams({
        page: String(pageParam),
        size: '25',
        winnerOnly: String(filters.winnerOnly ?? false),
        sort: filters.sort ?? 'NEWEST',
      });
      if (filters.q?.trim()) search.set('q', filters.q.trim());
      return apiFetch<PageResponse<FundraisingGameEntry>>(
        `/organizations/${organizationId}/campaigns/${campaignId}/game/entries/search?${search.toString()}`,
        { signal },
      );
    },
    initialPageParam: 0,
    getNextPageParam: (lastPage) => {
      const loaded = (lastPage.page + 1) * lastPage.size;
      return loaded < lastPage.totalElements ? lastPage.page + 1 : undefined;
    },
    enabled: enabled && !!organizationId && !!campaignId,
  });
}

export function flattenGameEntryPages(pages: PageResponse<FundraisingGameEntry>[] | undefined) {
  return pages?.flatMap((page) => page.items) ?? [];
}
