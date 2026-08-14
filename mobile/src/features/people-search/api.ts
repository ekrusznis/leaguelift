import { useInfiniteQuery } from '@tanstack/react-query';

import { apiFetch } from '@/lib/apiClient';
import type { PageResponse } from '@/lib/types';
import type { MembershipResponse } from '@/features/membership/types';
import type { OrgTeamResponse } from '@/features/organization-teams/types';

const PAGE_SIZE = 25;

export type TeamSearchSort = 'NAME_ASC' | 'NAME_DESC' | 'SPORT_ASC' | 'NEWEST' | 'OLDEST';

export interface TeamSearchFilters {
  q?: string;
  sport?: string;
  season?: string;
  genderCategory?: string;
  status?: string;
  sort?: TeamSearchSort;
}

export function useInfiniteTeamSearch(organizationId: string | null, filters: TeamSearchFilters) {
  return useInfiniteQuery({
    queryKey: ['organizations', organizationId, 'teams', 'search', filters],
    queryFn: ({ pageParam, signal }) => {
      const search = new URLSearchParams({
        page: String(pageParam),
        size: String(PAGE_SIZE),
        sort: filters.sort ?? 'NAME_ASC',
      });
      if (filters.q?.trim()) search.set('q', filters.q.trim());
      if (filters.sport?.trim()) search.set('sport', filters.sport.trim());
      if (filters.season?.trim()) search.set('season', filters.season.trim());
      if (filters.genderCategory) search.set('genderCategory', filters.genderCategory);
      if (filters.status) search.set('status', filters.status);
      return apiFetch<PageResponse<OrgTeamResponse>>(`/organizations/${organizationId}/teams/search?${search.toString()}`, { signal });
    },
    initialPageParam: 0,
    getNextPageParam: (lastPage) => {
      const loaded = (lastPage.page + 1) * lastPage.size;
      return loaded < lastPage.totalElements ? lastPage.page + 1 : undefined;
    },
    enabled: !!organizationId,
  });
}

export type MemberSearchSort = 'NAME_ASC' | 'NAME_DESC' | 'ROLE_ASC' | 'NEWEST' | 'OLDEST';

export interface MemberSearchFilters {
  q?: string;
  role?: string;
  status?: string;
  sort?: MemberSearchSort;
}

export function useInfiniteMemberSearch(organizationId: string | null, filters: MemberSearchFilters) {
  return useInfiniteQuery({
    queryKey: ['organizations', organizationId, 'members', 'search', filters],
    queryFn: ({ pageParam, signal }) => {
      const search = new URLSearchParams({
        page: String(pageParam),
        size: String(PAGE_SIZE),
        sort: filters.sort ?? 'NAME_ASC',
      });
      if (filters.q?.trim()) search.set('q', filters.q.trim());
      if (filters.role) search.set('role', filters.role);
      if (filters.status) search.set('status', filters.status);
      return apiFetch<PageResponse<MembershipResponse>>(`/organizations/${organizationId}/members/search?${search.toString()}`, { signal });
    },
    initialPageParam: 0,
    getNextPageParam: (lastPage) => {
      const loaded = (lastPage.page + 1) * lastPage.size;
      return loaded < lastPage.totalElements ? lastPage.page + 1 : undefined;
    },
    enabled: !!organizationId,
  });
}

export function flattenInfiniteItems<T>(pages: PageResponse<T>[] | undefined): T[] {
  return pages?.flatMap((page) => page.items) ?? [];
}
