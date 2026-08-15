import { useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query';

import { apiFetch } from '@/lib/apiClient';
import type { PageResponse } from '@/lib/types';
import { majorAmountToMinorUnits } from '@/lib/money';

import type { FeeAssignmentResponse } from './types';

export type FeeAssignmentStatus = 'OPEN' | 'PARTIALLY_PAID' | 'PAID' | 'WAIVED' | 'CANCELLED';

export type FeeAssignmentSearchSort =
  | 'DUE_DATE_ASC'
  | 'DUE_DATE_DESC'
  | 'BALANCE_DESC'
  | 'BALANCE_ASC'
  | 'DESCRIPTION_ASC'
  | 'HOUSEHOLD_ASC'
  | 'NEWEST'
  | 'OLDEST';

export interface FeeAssignmentSearchFilters {
  q?: string;
  status?: FeeAssignmentStatus | '';
  overdueOnly?: boolean;
  sort?: FeeAssignmentSearchSort;
}

export interface FeeTemplateResponse {
  id: string;
  organizationId: string;
  name: string;
  description: string | null;
  amountMinor: number;
  currency: string;
  status: 'ACTIVE' | 'ARCHIVED';
  createdAt: string;
  updatedAt: string;
}

export type FeeTemplateSearchSort =
  | 'NAME_ASC'
  | 'NAME_DESC'
  | 'AMOUNT_ASC'
  | 'AMOUNT_DESC'
  | 'NEWEST'
  | 'OLDEST';

export interface FeeTemplateSearchFilters {
  q?: string;
  status?: 'ACTIVE' | 'ARCHIVED' | '';
  sort?: FeeTemplateSearchSort;
}

export interface FeeAssignmentSummaryResponse extends FeeAssignmentResponse {
  householdName: string;
  participantName: string | null;
}

function assignmentParams(page: number, filters: FeeAssignmentSearchFilters) {
  const search = new URLSearchParams({
    page: String(page),
    size: '25',
    sort: filters.sort ?? 'DUE_DATE_ASC',
    overdueOnly: String(filters.overdueOnly ?? false),
  });
  if (filters.q?.trim()) search.set('q', filters.q.trim());
  if (filters.status) search.set('status', filters.status);
  return search;
}

export function useInfiniteHouseholdFeeSearch(
  organizationId: string | null,
  householdId: string | null,
  filters: FeeAssignmentSearchFilters,
) {
  return useInfiniteQuery({
    queryKey: [
      'organizations',
      organizationId,
      'households',
      householdId,
      'fee-assignments',
      'search',
      filters,
    ],
    queryFn: ({ pageParam, signal }) =>
      apiFetch<PageResponse<FeeAssignmentResponse>>(
        `/organizations/${organizationId}/households/${householdId}/fee-assignments/search?${assignmentParams(pageParam, filters).toString()}`,
        { signal },
      ),
    initialPageParam: 0,
    getNextPageParam: (lastPage) => {
      const loaded = (lastPage.page + 1) * lastPage.size;
      return loaded < lastPage.totalElements ? lastPage.page + 1 : undefined;
    },
    enabled: !!organizationId && !!householdId,
  });
}

export function useInfiniteOrganizationFeeSearch(
  organizationId: string | null,
  filters: FeeAssignmentSearchFilters,
) {
  return useInfiniteQuery({
    queryKey: ['organizations', organizationId, 'fee-assignments', 'search', filters],
    queryFn: ({ pageParam, signal }) =>
      apiFetch<PageResponse<FeeAssignmentSummaryResponse>>(
        `/organizations/${organizationId}/fee-assignments/search?${assignmentParams(pageParam, filters).toString()}`,
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

export function useInfiniteFeeTemplateSearch(
  organizationId: string | null,
  filters: FeeTemplateSearchFilters,
) {
  return useInfiniteQuery({
    queryKey: ['organizations', organizationId, 'fee-templates', 'search', filters],
    queryFn: ({ pageParam, signal }) => {
      const search = new URLSearchParams({
        page: String(pageParam),
        size: '25',
        sort: filters.sort ?? 'NAME_ASC',
      });
      if (filters.q?.trim()) search.set('q', filters.q.trim());
      if (filters.status) search.set('status', filters.status);
      return apiFetch<PageResponse<FeeTemplateResponse>>(
        `/organizations/${organizationId}/fee-templates/search?${search.toString()}`,
        { signal },
      );
    },
    initialPageParam: 0,
    getNextPageParam: (lastPage) => {
      const loaded = (lastPage.page + 1) * lastPage.size;
      return loaded < lastPage.totalElements ? lastPage.page + 1 : undefined;
    },
    enabled: !!organizationId,
  });
}

export function useCreateFeeTemplate(organizationId: string | null) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (values: {
      name: string;
      description: string;
      amountMajor: string;
      currency: string;
    }) =>
      apiFetch<FeeTemplateResponse>(`/organizations/${organizationId}/fee-templates`, {
        method: 'POST',
        body: {
          name: values.name.trim(),
          description: values.description.trim() || null,
          amountMinor: majorAmountToMinorUnits(values.amountMajor, values.currency),
          currency: values.currency,
        },
      }),
    onSuccess: () =>
      queryClient.invalidateQueries({
        queryKey: ['organizations', organizationId, 'fee-templates'],
      }),
  });
}

export function useArchiveFeeTemplate(organizationId: string | null) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (templateId: string) =>
      apiFetch(`/organizations/${organizationId}/fee-templates/${templateId}`, {
        method: 'DELETE',
      }),
    onSuccess: () =>
      queryClient.invalidateQueries({
        queryKey: ['organizations', organizationId, 'fee-templates'],
      }),
  });
}

export function flattenPages<T>(pages: PageResponse<T>[] | undefined): T[] {
  return pages?.flatMap((page) => page.items) ?? [];
}
