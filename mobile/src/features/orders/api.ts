import { useInfiniteQuery, useQuery } from '@tanstack/react-query';

import { apiFetch } from '@/lib/apiClient';
import type { PageResponse } from '@/lib/types';

export type StoreStatus = 'DRAFT' | 'ACTIVE' | 'ARCHIVED';

export interface StoreResponse {
  id: string;
  organizationId: string;
  teamId: string | null;
  name: string;
  slug: string;
  status: StoreStatus;
  createdAt: string;
  updatedAt: string;
}

export type OrderStatus = 'CONFIRMED' | 'REFUNDED';
export type PaymentSource = 'STRIPE' | 'OFFLINE';
export type FulfillmentStatus =
  | 'NOT_SUBMITTED'
  | 'DRAFT_CREATED'
  | 'FAILED'
  | 'READY'
  | 'IN_PRODUCTION'
  | 'SHIPPED'
  | 'DELIVERED'
  | 'NEEDS_ATTENTION'
  | 'CANCELED';

export interface ShippingAddress {
  name: string | null;
  line1: string | null;
  line2: string | null;
  city: string | null;
  state: string | null;
  postalCode: string | null;
  country: string | null;
}

export interface OrderSearchItem {
  id: string;
  storeId: string;
  status: OrderStatus;
  paymentSource: PaymentSource;
  currency: string;
  supporterName: string | null;
  supporterEmail: string | null;
  shippingAddress: ShippingAddress | null;
  fulfillmentStatus: FulfillmentStatus | null;
  confirmedAt: string | null;
  refundedAt: string | null;
  createdAt: string;
}

export type OrderSearchSort =
  | 'NEWEST'
  | 'OLDEST'
  | 'SUPPORTER_ASC'
  | 'STATUS_ASC'
  | 'FULFILLMENT_ASC';

export interface OrderSearchFilters {
  q?: string;
  status?: OrderStatus | '';
  paymentSource?: PaymentSource | '';
  fulfillmentStatus?: FulfillmentStatus | '';
  sort?: OrderSearchSort;
}

export function useStores(organizationId: string | null) {
  return useQuery({
    queryKey: ['organizations', organizationId, 'stores', 'owner-order-selector'],
    queryFn: ({ signal }) =>
      apiFetch<PageResponse<StoreResponse>>(
        `/organizations/${organizationId}/stores?page=0&size=100`,
        { signal },
      ),
    enabled: !!organizationId,
  });
}

export function useInfiniteOrderSearch(
  organizationId: string | null,
  storeId: string | null,
  filters: OrderSearchFilters,
) {
  return useInfiniteQuery({
    queryKey: ['organizations', organizationId, 'stores', storeId, 'orders', 'search', filters],
    queryFn: ({ pageParam, signal }) => {
      const search = new URLSearchParams({
        page: String(pageParam),
        size: '25',
        sort: filters.sort ?? 'NEWEST',
      });
      if (filters.q?.trim()) search.set('q', filters.q.trim());
      if (filters.status) search.set('status', filters.status);
      if (filters.paymentSource) search.set('paymentSource', filters.paymentSource);
      if (filters.fulfillmentStatus) search.set('fulfillmentStatus', filters.fulfillmentStatus);

      return apiFetch<PageResponse<OrderSearchItem>>(
        `/organizations/${organizationId}/stores/${storeId}/orders/search?${search.toString()}`,
        { signal },
      );
    },
    initialPageParam: 0,
    getNextPageParam: (lastPage) => {
      const loaded = (lastPage.page + 1) * lastPage.size;
      return loaded < lastPage.totalElements ? lastPage.page + 1 : undefined;
    },
    enabled: !!organizationId && !!storeId,
  });
}

export function flattenOrderPages(pages: PageResponse<OrderSearchItem>[] | undefined) {
  return pages?.flatMap((page) => page.items) ?? [];
}
