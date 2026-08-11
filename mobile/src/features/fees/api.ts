import { useQuery } from '@tanstack/react-query';

import { apiFetch } from '@/lib/apiClient';
import type { PageResponse } from '@/lib/types';

import type { FeeAssignmentResponse, FeePaymentResponse, OutstandingBalance } from './types';

/** GET .../dashboard/parent/outstanding-balance — aggregate summary, capped at 50 assignments server-side (ADR-103). */
export function useOutstandingBalance(organizationId: string | null, householdId: string | null) {
  return useQuery({
    queryKey: ['organizations', organizationId, 'households', householdId, 'dashboard', 'parent', 'outstanding-balance'],
    queryFn: ({ signal }) =>
      apiFetch<OutstandingBalance>(
        `/organizations/${organizationId}/households/${householdId}/dashboard/parent/outstanding-balance`,
        { signal },
      ),
    enabled: !!organizationId && !!householdId,
  });
}

/** GET .../households/{id}/fee-assignments — full itemized list with participant linkage, for the Payments screen's real list. */
export function useFeeAssignments(organizationId: string | null, householdId: string | null) {
  return useQuery({
    queryKey: ['organizations', organizationId, 'households', householdId, 'fee-assignments'],
    queryFn: ({ signal }) =>
      apiFetch<PageResponse<FeeAssignmentResponse>>(
        `/organizations/${organizationId}/households/${householdId}/fee-assignments?size=50`,
        { signal },
      ),
    enabled: !!organizationId && !!householdId,
  });
}

export function useFeePayments(organizationId: string | null, feeAssignmentId: string | null) {
  return useQuery({
    queryKey: ['organizations', organizationId, 'fee-assignments', feeAssignmentId, 'payments'],
    queryFn: ({ signal }) =>
      apiFetch<FeePaymentResponse[]>(`/organizations/${organizationId}/fee-assignments/${feeAssignmentId}/payments`, { signal }),
    enabled: !!organizationId && !!feeAssignmentId,
  });
}
