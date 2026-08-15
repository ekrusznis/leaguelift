import { useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query';

import { apiFetch } from '@/lib/apiClient';
import type { PageResponse } from '@/lib/types';

export type OfflineFinancialRecordType = 'CONTRIBUTION' | 'SPONSORSHIP' | 'ORDER';
export type OfflinePaymentMethod =
  | 'CASH'
  | 'CHECK'
  | 'ACH'
  | 'EXTERNAL_CARD'
  | 'VENMO'
  | 'ZELLE'
  | 'OTHER';
export type OfflineVerificationStatus = 'PENDING_VERIFICATION' | 'VERIFIED' | 'REVERSED';

export interface OfflineFinancialRecord {
  id: string;
  organizationId: string;
  recordType: OfflineFinancialRecordType;
  recordId: string;
  displayLabel: string;
  paymentMethod: OfflinePaymentMethod;
  verificationStatus: OfflineVerificationStatus;
  amountMinor: number;
  currency: string;
  payerName: string | null;
  payerEmail: string | null;
  paymentReference: string | null;
  receivedAt: string;
  internalNotes: string | null;
  sendAcknowledgement: boolean;
  recordedByUserId: string;
  verifiedByUserId: string | null;
  verifiedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

interface OfflineCommonInput {
  paymentMethod: OfflinePaymentMethod;
  paymentReference?: string | null;
  receivedAt: string;
  internalNotes?: string | null;
  idempotencyKey: string;
  markVerified: boolean;
  sendAcknowledgement: boolean;
}

export interface CreateOfflineContributionInput extends OfflineCommonInput {
  campaignId: string;
  amountMinor: number;
  supporterName?: string | null;
  isAnonymous: boolean;
  supporterEmail?: string | null;
}

export interface CreateOfflineSponsorshipInput extends OfflineCommonInput {
  packageId: string;
  sponsorName: string;
  sponsorContactEmail?: string | null;
  sponsorPhone?: string | null;
  sponsorCompanyName?: string | null;
}

export interface CreateOfflineOrderInput extends OfflineCommonInput {
  storeId: string;
  items: { productVariantId: string; quantity: number }[];
  supporterName?: string | null;
  supporterEmail?: string | null;
  shippingAddress?: {
    name?: string | null;
    line1?: string | null;
    line2?: string | null;
    city?: string | null;
    state?: string | null;
    postalCode?: string | null;
    country?: string | null;
  } | null;
}

export interface OfflineRecordFilters {
  q?: string;
  verificationStatus?: OfflineVerificationStatus | '';
  recordType?: OfflineFinancialRecordType | '';
  paymentMethod?: OfflinePaymentMethod | '';
  sort?: 'newest' | 'oldest';
}

const offlineKey = (organizationId: string | null) =>
  ['organizations', organizationId, 'offline-financial-records'] as const;

export function useInfiniteOfflineFinancialRecords(
  organizationId: string | null,
  filters: OfflineRecordFilters,
) {
  return useInfiniteQuery({
    queryKey: [...offlineKey(organizationId), 'search', filters],
    queryFn: ({ pageParam, signal }) => {
      const params = new URLSearchParams({
        page: String(pageParam),
        size: '25',
        sort: filters.sort ?? 'newest',
      });
      if (filters.q?.trim()) params.set('q', filters.q.trim());
      if (filters.verificationStatus) params.set('verificationStatus', filters.verificationStatus);
      if (filters.recordType) params.set('recordType', filters.recordType);
      if (filters.paymentMethod) params.set('paymentMethod', filters.paymentMethod);
      return apiFetch<PageResponse<OfflineFinancialRecord>>(
        `/organizations/${organizationId}/offline-financial-records?${params.toString()}`,
        { signal },
      );
    },
    initialPageParam: 0,
    getNextPageParam: nextPage,
    enabled: !!organizationId,
  });
}

function useCreateOfflineRecord<T>(organizationId: string | null, suffix: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: T) =>
      apiFetch<OfflineFinancialRecord>(
        `/organizations/${organizationId}/offline-financial-records/${suffix}`,
        { method: 'POST', body: input },
      ),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: offlineKey(organizationId) });
      await queryClient.invalidateQueries({ queryKey: ['me', 'action-center'] });
    },
  });
}

export function useCreateOfflineContribution(organizationId: string | null) {
  return useCreateOfflineRecord<CreateOfflineContributionInput>(organizationId, 'contributions');
}

export function useCreateOfflineSponsorship(organizationId: string | null) {
  return useCreateOfflineRecord<CreateOfflineSponsorshipInput>(organizationId, 'sponsorships');
}

export function useCreateOfflineOrder(organizationId: string | null) {
  return useCreateOfflineRecord<CreateOfflineOrderInput>(organizationId, 'orders');
}

export function useVerifyOfflineFinancialRecord(organizationId: string | null) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (recordId: string) =>
      apiFetch<OfflineFinancialRecord>(
        `/organizations/${organizationId}/offline-financial-records/${recordId}/verify`,
        { method: 'POST' },
      ),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: offlineKey(organizationId) });
      await queryClient.invalidateQueries({ queryKey: ['me', 'action-center'] });
      await queryClient.invalidateQueries({ queryKey: ['organizations', organizationId, 'campaigns'] });
      await queryClient.invalidateQueries({ queryKey: ['organizations', organizationId, 'sponsorship-packages'] });
      await queryClient.invalidateQueries({ queryKey: ['organizations', organizationId, 'stores'] });
    },
  });
}

export type FinancialCorrectionTargetType =
  | 'CONTRIBUTION'
  | 'SPONSORSHIP'
  | 'ORDER'
  | 'OFFLINE_FINANCIAL_RECORD';
export type FinancialCorrectionType = 'REFUND' | 'REVERSAL';

export interface FinancialCorrection {
  id: string;
  organizationId: string;
  correctionType: FinancialCorrectionType;
  targetType: FinancialCorrectionTargetType;
  targetId: string;
  amountMinor: number;
  currency: string;
  reason: string;
  providerReference: string | null;
  createdByUserId: string;
  createdAt: string;
}

export interface FinancialCorrectionPreview {
  correctionType: FinancialCorrectionType;
  targetType: FinancialCorrectionTargetType;
  targetId: string;
  targetLabel: string;
  paymentSource: string;
  originalAmountMinor: number;
  previouslyCorrectedMinor: number;
  requestedAmountMinor: number;
  remainingAfterMinor: number;
  currency: string;
  willFullyCorrect: boolean;
  warnings: string[];
  confirmationHash: string;
}

export interface CorrectionInput {
  targetType: FinancialCorrectionTargetType;
  targetId: string;
  amountMinor: number | null;
  reason: string;
}

export interface CorrectionFilters {
  q?: string;
  targetType?: FinancialCorrectionTargetType | '';
  correctionType?: FinancialCorrectionType | '';
  sort?: 'newest' | 'oldest';
}

const correctionsKey = (organizationId: string | null) =>
  ['organizations', organizationId, 'financial-corrections'] as const;

export function useInfiniteFinancialCorrections(
  organizationId: string | null,
  filters: CorrectionFilters,
) {
  return useInfiniteQuery({
    queryKey: [...correctionsKey(organizationId), 'search', filters],
    queryFn: ({ pageParam, signal }) => {
      const params = new URLSearchParams({
        page: String(pageParam),
        size: '25',
        sort: filters.sort ?? 'newest',
      });
      if (filters.q?.trim()) params.set('q', filters.q.trim());
      if (filters.targetType) params.set('targetType', filters.targetType);
      if (filters.correctionType) params.set('correctionType', filters.correctionType);
      return apiFetch<PageResponse<FinancialCorrection>>(
        `/organizations/${organizationId}/financial-corrections?${params.toString()}`,
        { signal },
      );
    },
    initialPageParam: 0,
    getNextPageParam: nextPage,
    enabled: !!organizationId,
  });
}

export function usePreviewFinancialCorrection(organizationId: string | null) {
  return useMutation({
    mutationFn: (input: CorrectionInput) =>
      apiFetch<FinancialCorrectionPreview>(
        `/organizations/${organizationId}/financial-corrections/preview`,
        { method: 'POST', body: input },
      ),
  });
}

export function useExecuteFinancialCorrection(organizationId: string | null) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: CorrectionInput & { confirmationHash: string; idempotencyKey: string }) =>
      apiFetch<FinancialCorrection>(
        `/organizations/${organizationId}/financial-corrections/execute`,
        { method: 'POST', body: input },
      ),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: correctionsKey(organizationId) }),
  });
}

export type ReconciliationRunStatus = 'RUNNING' | 'COMPLETED' | 'FAILED';
export type ReconciliationSeverity = 'HIGH' | 'MEDIUM' | 'LOW';

export interface ReconciliationRun {
  id: string;
  organizationId: string;
  status: ReconciliationRunStatus;
  issueCount: number;
  highCount: number;
  mediumCount: number;
  lowCount: number;
  startedByUserId: string;
  startedAt: string;
  completedAt: string | null;
}

export interface ReconciliationIssue {
  id: string;
  issueType: string;
  severity: ReconciliationSeverity;
  resourceType: string;
  resourceId: string | null;
  title: string;
  detail: string;
  actionPath: string | null;
  createdAt: string;
}

export interface ReconciliationResult {
  run: ReconciliationRun;
  issues: ReconciliationIssue[];
}

export interface ReconciliationRunFilters {
  status?: ReconciliationRunStatus | '';
  sort?: 'newest' | 'oldest';
}

export interface ReconciliationIssueFilters {
  q?: string;
  severity?: ReconciliationSeverity | '';
  resourceType?: string;
  sort?: 'newest' | 'oldest';
}

const runsKey = (organizationId: string | null) =>
  ['organizations', organizationId, 'reconciliation-runs'] as const;
const issuesKey = (organizationId: string | null) =>
  ['organizations', organizationId, 'reconciliation-issues'] as const;

export function useInfiniteReconciliationRuns(
  organizationId: string | null,
  filters: ReconciliationRunFilters,
) {
  return useInfiniteQuery({
    queryKey: [...runsKey(organizationId), 'search', filters],
    queryFn: ({ pageParam, signal }) => {
      const params = new URLSearchParams({
        page: String(pageParam),
        size: '25',
        sort: filters.sort ?? 'newest',
      });
      if (filters.status) params.set('status', filters.status);
      return apiFetch<PageResponse<ReconciliationRun>>(
        `/organizations/${organizationId}/reconciliation-runs?${params.toString()}`,
        { signal },
      );
    },
    initialPageParam: 0,
    getNextPageParam: nextPage,
    enabled: !!organizationId,
  });
}

export function useInfiniteReconciliationIssues(
  organizationId: string | null,
  runId: string | null,
  filters: ReconciliationIssueFilters,
) {
  return useInfiniteQuery({
    queryKey: [...issuesKey(organizationId), runId, 'search', filters],
    queryFn: ({ pageParam, signal }) => {
      const params = new URLSearchParams({
        page: String(pageParam),
        size: '25',
        sort: filters.sort ?? 'newest',
      });
      if (filters.q?.trim()) params.set('q', filters.q.trim());
      if (filters.severity) params.set('severity', filters.severity);
      if (filters.resourceType?.trim()) params.set('resourceType', filters.resourceType.trim());
      return apiFetch<PageResponse<ReconciliationIssue>>(
        `/organizations/${organizationId}/reconciliation-runs/${runId}/issues?${params.toString()}`,
        { signal },
      );
    },
    initialPageParam: 0,
    getNextPageParam: nextPage,
    enabled: !!organizationId && !!runId,
  });
}

export function useRunReconciliation(organizationId: string | null) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () =>
      apiFetch<ReconciliationResult>(
        `/organizations/${organizationId}/reconciliation-runs`,
        { method: 'POST' },
      ),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: runsKey(organizationId) });
      await queryClient.invalidateQueries({ queryKey: issuesKey(organizationId) });
    },
  });
}

function nextPage<T>(lastPage: PageResponse<T>) {
  const loaded = (lastPage.page + 1) * lastPage.size;
  return loaded < lastPage.totalElements ? lastPage.page + 1 : undefined;
}

export function flattenFinancialOperationPages<T>(
  pages: PageResponse<T>[] | undefined,
): T[] {
  return pages?.flatMap((page) => page.items) ?? [];
}
