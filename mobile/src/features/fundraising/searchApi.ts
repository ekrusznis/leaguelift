import { useInfiniteQuery, useQuery } from '@tanstack/react-query';

import { apiFetch } from '@/lib/apiClient';
import type { PageResponse } from '@/lib/types';

import { campaignsKey } from './api';
import type {
  Campaign,
  CampaignStatus,
  CampaignType,
  Contribution,
  FundraiserTemplateKey,
} from './types';

export type CampaignSearchSort =
  | 'NEWEST'
  | 'NAME_ASC'
  | 'START_DATE_ASC'
  | 'END_DATE_ASC'
  | 'RAISED_DESC'
  | 'GOAL_DESC';

export interface CampaignSearchFilters {
  q?: string;
  status?: CampaignStatus | '';
  campaignType?: CampaignType | '';
  templateKey?: FundraiserTemplateKey | '';
  teamId?: string;
  sort?: CampaignSearchSort;
}

function campaignSearchParams(page: number, filters: CampaignSearchFilters, size = 25) {
  const search = new URLSearchParams({
    page: String(page),
    size: String(size),
    sort: filters.sort ?? 'NEWEST',
  });
  if (filters.q?.trim()) search.set('q', filters.q.trim());
  if (filters.status) search.set('status', filters.status);
  if (filters.campaignType) search.set('campaignType', filters.campaignType);
  if (filters.templateKey) search.set('templateKey', filters.templateKey);
  if (filters.teamId) search.set('teamId', filters.teamId);
  return search;
}

export function useInfiniteCampaignSearch(
  organizationId: string | null,
  filters: CampaignSearchFilters,
) {
  return useInfiniteQuery({
    queryKey: [...campaignsKey(organizationId), 'search', filters],
    queryFn: ({ pageParam, signal }) =>
      apiFetch<PageResponse<Campaign>>(
        `/organizations/${organizationId}/campaigns/search?${campaignSearchParams(pageParam, filters).toString()}`,
        { signal },
      ),
    initialPageParam: 0,
    getNextPageParam: (lastPage) => {
      const loaded = (lastPage.page + 1) * lastPage.size;
      return loaded < lastPage.totalElements ? lastPage.page + 1 : undefined;
    },
    enabled: !!organizationId,
  });
}

export function useCampaignSearchPage(
  organizationId: string | null,
  filters: CampaignSearchFilters,
  size = 100,
) {
  const params = campaignSearchParams(0, filters, size);
  return useQuery({
    queryKey: [...campaignsKey(organizationId), 'search-page', Object.fromEntries(params.entries())],
    queryFn: ({ signal }) =>
      apiFetch<PageResponse<Campaign>>(
        `/organizations/${organizationId}/campaigns/search?${params.toString()}`,
        { signal },
      ),
    enabled: !!organizationId,
  });
}

export function flattenCampaignPages(pages: PageResponse<Campaign>[] | undefined) {
  return pages?.flatMap((page) => page.items) ?? [];
}

export type ContributionSearchSort =
  | 'NEWEST'
  | 'OLDEST'
  | 'AMOUNT_DESC'
  | 'AMOUNT_ASC'
  | 'SUPPORTER_ASC';

export interface ContributionSearchFilters {
  q?: string;
  status?: 'CONFIRMED' | 'REFUNDED' | '';
  paymentSource?: 'STRIPE' | 'OFFLINE' | '';
  sort?: ContributionSearchSort;
}

export function useInfiniteContributionSearch(
  organizationId: string | null,
  campaignId: string | null,
  filters: ContributionSearchFilters,
) {
  return useInfiniteQuery({
    queryKey: [
      'organizations',
      organizationId,
      'campaigns',
      campaignId,
      'contributions',
      'search',
      filters,
    ],
    queryFn: ({ pageParam, signal }) => {
      const search = new URLSearchParams({
        page: String(pageParam),
        size: '25',
        sort: filters.sort ?? 'NEWEST',
      });
      if (filters.q?.trim()) search.set('q', filters.q.trim());
      if (filters.status) search.set('status', filters.status);
      if (filters.paymentSource) search.set('paymentSource', filters.paymentSource);
      return apiFetch<PageResponse<Contribution>>(
        `/organizations/${organizationId}/campaigns/${campaignId}/contributions/search?${search.toString()}`,
        { signal },
      );
    },
    initialPageParam: 0,
    getNextPageParam: (lastPage) => {
      const loaded = (lastPage.page + 1) * lastPage.size;
      return loaded < lastPage.totalElements ? lastPage.page + 1 : undefined;
    },
    enabled: !!organizationId && !!campaignId,
  });
}

export function flattenContributionPages(pages: PageResponse<Contribution>[] | undefined) {
  return pages?.flatMap((page) => page.items) ?? [];
}
