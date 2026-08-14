import { useInfiniteQuery } from '@tanstack/react-query';

import { apiFetch } from '@/lib/apiClient';
import type { PageResponse } from '@/lib/types';

import type { EventResponse, EventStatus, EventType } from './types';

export type EventSearchSort = 'DATE_ASC' | 'DATE_DESC' | 'TITLE_ASC';

export type EventSearchScope =
  | { type: 'team'; organizationId: string | null; teamId: string | null }
  | { type: 'household'; organizationId: string | null; householdId: string | null }
  | { type: 'participant'; organizationId: string | null; participantId: string | null };

export interface EventSearchFilters {
  q?: string;
  eventType?: EventType | '';
  status?: EventStatus | '';
  sort?: EventSearchSort;
}

function scopeReady(scope: EventSearchScope) {
  if (!scope.organizationId) return false;
  if (scope.type === 'team') return !!scope.teamId;
  if (scope.type === 'household') return !!scope.householdId;
  return !!scope.participantId;
}

function scopePath(scope: EventSearchScope) {
  if (scope.type === 'team') return `/teams/${scope.teamId}/events/search`;
  if (scope.type === 'household') return `/households/${scope.householdId}/events/search`;
  return `/participants/${scope.participantId}/events/search`;
}

function monthRange(year: number, month0: number) {
  const from = new Date(year, month0, 1, 0, 0, 0, 0);
  const to = new Date(year, month0 + 1, 1, 0, 0, 0, 0);
  to.setMilliseconds(to.getMilliseconds() - 1);
  return { from: from.toISOString(), to: to.toISOString() };
}

/**
 * Native calendar pagination is month-scoped and then row-paged within the month.
 * This prevents the previous invisible size=100 cap while keeping the calendar grid useful.
 */
export function useInfiniteEventMonthSearch(
  scope: EventSearchScope,
  year: number,
  month0: number,
  filters: EventSearchFilters,
) {
  const range = monthRange(year, month0);

  return useInfiniteQuery({
    queryKey: [
      'events',
      'search',
      scope,
      year,
      month0,
      filters,
    ],
    queryFn: ({ pageParam, signal }) => {
      const search = new URLSearchParams({
        organizationId: scope.organizationId ?? '',
        page: String(pageParam),
        size: '25',
        from: range.from,
        to: range.to,
        sort: filters.sort ?? 'DATE_ASC',
      });
      if (filters.q?.trim()) search.set('q', filters.q.trim());
      if (filters.eventType) search.set('eventType', filters.eventType);
      if (filters.status) search.set('status', filters.status);

      return apiFetch<PageResponse<EventResponse>>(
        `${scopePath(scope)}?${search.toString()}`,
        { signal },
      );
    },
    initialPageParam: 0,
    getNextPageParam: (lastPage) => {
      const loaded = (lastPage.page + 1) * lastPage.size;
      return loaded < lastPage.totalElements ? lastPage.page + 1 : undefined;
    },
    enabled: scopeReady(scope),
  });
}

export function flattenEventPages(pages: PageResponse<EventResponse>[] | undefined) {
  return pages?.flatMap((page) => page.items) ?? [];
}
