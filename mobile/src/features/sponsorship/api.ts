import { useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query';

import { apiFetch } from '@/lib/apiClient';
import type { PageResponse } from '@/lib/types';
import { majorAmountToMinorUnits } from '@/lib/money';

export type SponsorshipPackageStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED';
export type SponsorshipStatus = 'CONFIRMED' | 'REFUNDED';
export type SponsorshipReviewStatus = 'PENDING_REVIEW' | 'APPROVED' | 'REJECTED';

export interface SponsorshipPackage {
  id: string;
  organizationId: string;
  name: string;
  description: string | null;
  priceMinor: number;
  currency: string;
  maxQuantity: number | null;
  exclusive: boolean;
  placementStartDate: string | null;
  placementEndDate: string | null;
  status: SponsorshipPackageStatus;
  createdAt: string;
  updatedAt: string;
  confirmedCount: number;
  soldOut: boolean;
}

export interface SponsorshipSearchItem {
  id: string;
  packageId: string;
  packageName: string;
  status: SponsorshipStatus;
  paymentSource: 'STRIPE' | 'OFFLINE';
  amountMinor: number;
  currency: string;
  sponsorId: string;
  sponsorName: string;
  sponsorContactEmail: string | null;
  sponsorCompanyName: string | null;
  confirmedAt: string | null;
  refundedAt: string | null;
  reviewStatus: SponsorshipReviewStatus;
  reviewedAt: string | null;
  createdAt: string;
}

export type PackageSort =
  | 'NEWEST'
  | 'OLDEST'
  | 'NAME_ASC'
  | 'NAME_DESC'
  | 'PRICE_ASC'
  | 'PRICE_DESC'
  | 'SPONSORS_DESC';

export type SponsorshipSort =
  | 'NEWEST'
  | 'OLDEST'
  | 'SPONSOR_ASC'
  | 'AMOUNT_ASC'
  | 'AMOUNT_DESC'
  | 'PACKAGE_ASC'
  | 'REVIEW_STATUS_ASC';

const packageKey = (organizationId: string | null) =>
  ['organizations', organizationId, 'sponsorship-packages'] as const;
const sponsorshipKey = (organizationId: string | null) =>
  ['organizations', organizationId, 'sponsorships'] as const;

export function useInfiniteSponsorshipPackages(
  organizationId: string | null,
  filters: {
    q?: string;
    status?: SponsorshipPackageStatus | '';
    exclusive?: boolean;
    sort?: PackageSort;
  },
) {
  return useInfiniteQuery({
    queryKey: [...packageKey(organizationId), 'search', filters],
    queryFn: ({ pageParam, signal }) => {
      const search = new URLSearchParams({
        page: String(pageParam),
        size: '25',
        sort: filters.sort ?? 'NEWEST',
      });
      if (filters.q?.trim()) search.set('q', filters.q.trim());
      if (filters.status) search.set('status', filters.status);
      if (filters.exclusive !== undefined) search.set('exclusive', String(filters.exclusive));
      return apiFetch<PageResponse<SponsorshipPackage>>(
        `/organizations/${organizationId}/sponsorship-packages/search?${search.toString()}`,
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

export function useInfiniteSponsorships(
  organizationId: string | null,
  filters: {
    q?: string;
    packageId?: string;
    status?: SponsorshipStatus | '';
    reviewStatus?: SponsorshipReviewStatus | '';
    paymentSource?: 'STRIPE' | 'OFFLINE' | '';
    sort?: SponsorshipSort;
  },
) {
  return useInfiniteQuery({
    queryKey: [...sponsorshipKey(organizationId), 'search', filters],
    queryFn: ({ pageParam, signal }) => {
      const search = new URLSearchParams({
        page: String(pageParam),
        size: '25',
        sort: filters.sort ?? 'NEWEST',
      });
      if (filters.q?.trim()) search.set('q', filters.q.trim());
      if (filters.packageId) search.set('packageId', filters.packageId);
      if (filters.status) search.set('status', filters.status);
      if (filters.reviewStatus) search.set('reviewStatus', filters.reviewStatus);
      if (filters.paymentSource) search.set('paymentSource', filters.paymentSource);
      return apiFetch<PageResponse<SponsorshipSearchItem>>(
        `/organizations/${organizationId}/sponsorships/search?${search.toString()}`,
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

export function useCreateSponsorshipPackage(organizationId: string | null) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (values: {
      name: string;
      description: string;
      priceMajor: string;
      currency: string;
      maxQuantity: string;
      exclusive: boolean;
    }) =>
      apiFetch<SponsorshipPackage>(`/organizations/${organizationId}/sponsorship-packages`, {
        method: 'POST',
        body: {
          name: values.name.trim(),
          description: values.description.trim() || null,
          priceMinor: majorAmountToMinorUnits(values.priceMajor, values.currency),
          currency: values.currency,
          maxQuantity: values.maxQuantity.trim() ? Number(values.maxQuantity) : null,
          exclusive: values.exclusive,
          placementStartDate: null,
          placementEndDate: null,
        },
      }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: packageKey(organizationId) }),
  });
}

export function usePublishSponsorshipPackage(organizationId: string | null) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (packageId: string) =>
      apiFetch<SponsorshipPackage>(
        `/organizations/${organizationId}/sponsorship-packages/${packageId}/publish`,
        { method: 'POST' },
      ),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: packageKey(organizationId) }),
  });
}

export function useArchiveSponsorshipPackage(organizationId: string | null) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (packageId: string) =>
      apiFetch<SponsorshipPackage>(
        `/organizations/${organizationId}/sponsorship-packages/${packageId}/status`,
        { method: 'PATCH', body: { status: 'ARCHIVED' } },
      ),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: packageKey(organizationId) }),
  });
}

function useReviewMutation(
  organizationId: string | null,
  action: 'approve' | 'reject',
) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (sponsorshipId: string) =>
      apiFetch(
        `/organizations/${organizationId}/sponsorships/${sponsorshipId}/${action}`,
        { method: 'POST' },
      ),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: sponsorshipKey(organizationId) });
      await queryClient.invalidateQueries({ queryKey: packageKey(organizationId) });
      await queryClient.invalidateQueries({ queryKey: ['me', 'action-center'] });
    },
  });
}

export function useApproveSponsorship(organizationId: string | null) {
  return useReviewMutation(organizationId, 'approve');
}

export function useRejectSponsorship(organizationId: string | null) {
  return useReviewMutation(organizationId, 'reject');
}

export function flattenSponsorshipPages<T>(pages: PageResponse<T>[] | undefined): T[] {
  return pages?.flatMap((page) => page.items) ?? [];
}
