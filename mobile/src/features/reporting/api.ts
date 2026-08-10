import { useQuery } from '@tanstack/react-query';

import { apiFetch } from '@/lib/apiClient';

import type { FeeCollectionsReportResponse, RefundsReportResponse, RevenueReportResponse } from './types';

/**
 * GET /organizations/{organizationId}/reports/* — from/to (ISO LocalDate) default to
 * the trailing 30 days server-side when omitted, so this slice never sends them
 * (ADR-105). CSV export exists but isn't wired to mobile in this slice.
 */
export function useRevenueReport(organizationId: string | null) {
  return useQuery({
    queryKey: ['organizations', organizationId, 'reports', 'revenue'],
    queryFn: ({ signal }) => apiFetch<RevenueReportResponse>(`/organizations/${organizationId}/reports/revenue`, { signal }),
    enabled: !!organizationId,
  });
}

export function useRefundsReport(organizationId: string | null) {
  return useQuery({
    queryKey: ['organizations', organizationId, 'reports', 'refunds'],
    queryFn: ({ signal }) => apiFetch<RefundsReportResponse>(`/organizations/${organizationId}/reports/refunds`, { signal }),
    enabled: !!organizationId,
  });
}

export function useFeeCollectionsReport(organizationId: string | null) {
  return useQuery({
    queryKey: ['organizations', organizationId, 'reports', 'fee-collections'],
    queryFn: ({ signal }) =>
      apiFetch<FeeCollectionsReportResponse>(`/organizations/${organizationId}/reports/fee-collections`, { signal }),
    enabled: !!organizationId,
  });
}
