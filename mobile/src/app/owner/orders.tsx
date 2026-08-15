import Ionicons from '@expo/vector-icons/Ionicons';
import { useEffect, useMemo, useState } from 'react';
import { Pressable, ScrollView, StyleSheet, View } from 'react-native';

import { EmptyState } from '@/components/empty-state';
import { ErrorState } from '@/components/error-state';
import { ListControls } from '@/components/list-controls';
import { ListFooter } from '@/components/list-footer';
import { LoadingState } from '@/components/loading-state';
import { Modal } from '@/components/modal';
import { ScreenHeader } from '@/components/screen-header';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { useDashboardContext } from '@/features/dashboard/api';
import {
  flattenOrderPages,
  useInfiniteOrderSearch,
  useStores,
  type FulfillmentStatus,
  type OrderSearchSort,
  type OrderStatus,
  type PaymentSource,
} from '@/features/orders/api';
import { Brand, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

const FULFILLMENT_LABELS: Record<FulfillmentStatus, string> = {
  NOT_SUBMITTED: 'Not submitted',
  DRAFT_CREATED: 'Draft created',
  FAILED: 'Failed',
  READY: 'Ready',
  IN_PRODUCTION: 'In production',
  SHIPPED: 'Shipped',
  DELIVERED: 'Delivered',
  NEEDS_ATTENTION: 'Needs attention',
  CANCELED: 'Canceled',
};

export default function OwnerOrdersScreen() {
  const theme = useTheme();
  const dashboard = useDashboardContext(true);
  const organizationId = dashboard.data?.organizationId ?? null;
  const stores = useStores(organizationId);
  const [storeId, setStoreId] = useState<string | null>(null);

  useEffect(() => {
    if (!storeId && stores.data?.items.length) {
      const preferred =
        stores.data.items.find((store) => store.status === 'ACTIVE') ?? stores.data.items[0];
      setStoreId(preferred.id);
    }
  }, [storeId, stores.data?.items]);

  const selectedStore = stores.data?.items.find((store) => store.id === storeId) ?? null;
  const [storeOpen, setStoreOpen] = useState(false);
  const [query, setQuery] = useState('');
  const [status, setStatus] = useState<OrderStatus | ''>('');
  const [paymentSource, setPaymentSource] = useState<PaymentSource | ''>('');
  const [fulfillmentStatus, setFulfillmentStatus] = useState<FulfillmentStatus | ''>('');
  const [sort, setSort] = useState<OrderSearchSort>('NEWEST');
  const [filterOpen, setFilterOpen] = useState(false);
  const [sortOpen, setSortOpen] = useState(false);

  const orders = useInfiniteOrderSearch(organizationId, storeId, {
    q: query,
    status,
    paymentSource,
    fulfillmentStatus,
    sort,
  });
  const items = useMemo(() => flattenOrderPages(orders.data?.pages), [orders.data?.pages]);
  const total = orders.data?.pages[0]?.totalElements ?? 0;

  const activeFilters = [
    ...(status ? [status === 'CONFIRMED' ? 'Confirmed' : 'Refunded'] : []),
    ...(paymentSource ? [paymentSource === 'STRIPE' ? 'Online card' : 'Recorded offline'] : []),
    ...(fulfillmentStatus ? [FULFILLMENT_LABELS[fulfillmentStatus]] : []),
  ];

  const sortLabel =
    sort === 'NEWEST' ? 'Newest'
    : sort === 'OLDEST' ? 'Oldest'
    : sort === 'SUPPORTER_ASC' ? 'Supporter A–Z'
    : sort === 'STATUS_ASC' ? 'Payment status'
    : 'Fulfillment';

  return (
    <ThemedView style={styles.container}>
      <ScreenHeader title="Swag Orders" />
      <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
        {stores.isLoading && <LoadingState label="Loading stores…" />}
        {stores.isError && (
          <ErrorState message="Could not load your stores." onRetry={() => stores.refetch()} />
        )}
        {stores.data?.items.length === 0 && (
          <EmptyState
            title="No stores yet"
            description="Create a Swag Shop store before orders can appear here."
          />
        )}

        {selectedStore && (
          <>
            <Pressable
              accessibilityRole="button"
              accessibilityLabel="Choose store"
              onPress={() => setStoreOpen(true)}>
              <ThemedView type="backgroundElement" style={styles.storeSelector}>
                <View style={styles.flexOne}>
                  <ThemedText type="small" themeColor="textSecondary">Store</ThemedText>
                  <ThemedText type="smallBold">{selectedStore.name}</ThemedText>
                </View>
                <Ionicons name="chevron-down" size={18} color={theme.textSecondary} />
              </ThemedView>
            </Pressable>

            <ListControls
              query={query}
              onChangeQuery={setQuery}
              searchPlaceholder="Search supporter, email, address, or order ID"
              resultCount={total}
              activeFilters={activeFilters}
              onRemoveFilter={(index) => {
                let cursor = 0;
                if (status) {
                  if (index === cursor) return setStatus('');
                  cursor += 1;
                }
                if (paymentSource) {
                  if (index === cursor) return setPaymentSource('');
                  cursor += 1;
                }
                if (fulfillmentStatus && index === cursor) setFulfillmentStatus('');
              }}
              onClearFilters={() => {
                setQuery('');
                setStatus('');
                setPaymentSource('');
                setFulfillmentStatus('');
                setSort('NEWEST');
              }}
              onPressFilter={() => setFilterOpen(true)}
              onPressSort={() => setSortOpen(true)}
              sortLabel={sortLabel}
            />

            {orders.isLoading && <LoadingState label="Loading orders…" />}
            {orders.isError && (
              <ErrorState message="Could not load orders." onRetry={() => orders.refetch()} />
            )}
            {!orders.isLoading && !orders.isError && items.length === 0 && (
              <EmptyState
                title={query.trim() || activeFilters.length > 0 ? 'No results found' : 'No confirmed orders yet'}
                description={
                  query.trim() || activeFilters.length > 0
                    ? 'Try changing your search or filters.'
                    : 'Confirmed Swag Shop orders will appear here.'
                }
              />
            )}

            {items.map((order) => (
              <ThemedView key={order.id} type="backgroundElement" style={styles.orderCard}>
                <View style={styles.cardTop}>
                  <View style={styles.flexOne}>
                    <ThemedText type="smallBold">
                      {order.supporterName || 'Anonymous supporter'}
                    </ThemedText>
                    <ThemedText type="small" themeColor="textSecondary">
                      {order.supporterEmail || 'No email recorded'}
                    </ThemedText>
                  </View>
                  <StatusBadge
                    label={order.fulfillmentStatus ? FULFILLMENT_LABELS[order.fulfillmentStatus] : 'Fulfillment pending'}
                    attention={
                      order.fulfillmentStatus === 'FAILED' ||
                      order.fulfillmentStatus === 'NEEDS_ATTENTION'
                    }
                  />
                </View>
                <View style={styles.metaRow}>
                  <ThemedText type="small" themeColor="textSecondary">
                    Order {order.id.slice(0, 8)}
                  </ThemedText>
                  <ThemedText type="small" themeColor="textSecondary">
                    {order.paymentSource === 'STRIPE' ? 'Online card' : 'Recorded offline'}
                    {order.status === 'REFUNDED' ? ' · Refunded' : ''}
                  </ThemedText>
                </View>
                {order.shippingAddress && (
                  <ThemedText type="small" themeColor="textSecondary">
                    {formatAddress(order.shippingAddress)}
                  </ThemedText>
                )}
                <ThemedText type="small" themeColor="textSecondary">
                  {new Date(order.confirmedAt ?? order.createdAt).toLocaleString()}
                </ThemedText>
              </ThemedView>
            ))}

            <ListFooter
              loadedCount={items.length}
              totalCount={total}
              hasMore={!!orders.hasNextPage}
              loadingMore={orders.isFetchingNextPage}
              onLoadMore={() => orders.fetchNextPage()}
            />
          </>
        )}
      </ScrollView>

      <Modal visible={storeOpen} onClose={() => setStoreOpen(false)}>
        <ThemedText type="smallBold" style={styles.modalTitle}>Choose store</ThemedText>
        <ScrollView style={styles.modalScroll}>
          {stores.data?.items.map((store) => (
            <Option
              key={store.id}
              selected={store.id === storeId}
              label={`${store.name}${store.status !== 'ACTIVE' ? ` · ${store.status}` : ''}`}
              onPress={() => {
                setStoreId(store.id);
                setStoreOpen(false);
              }}
            />
          ))}
        </ScrollView>
      </Modal>

      <Modal visible={filterOpen} onClose={() => setFilterOpen(false)}>
        <ThemedText type="smallBold" style={styles.modalTitle}>Filter orders</ThemedText>
        <ScrollView style={styles.modalScroll}>
          <ThemedText type="smallBold" style={styles.groupTitle}>Payment status</ThemedText>
          <Option selected={!status} label="All payment statuses" onPress={() => setStatus('')} />
          <Option selected={status === 'CONFIRMED'} label="Confirmed" onPress={() => setStatus('CONFIRMED')} />
          <Option selected={status === 'REFUNDED'} label="Refunded" onPress={() => setStatus('REFUNDED')} />

          <ThemedText type="smallBold" style={styles.groupTitle}>Payment source</ThemedText>
          <Option selected={!paymentSource} label="All payment sources" onPress={() => setPaymentSource('')} />
          <Option selected={paymentSource === 'STRIPE'} label="Online card" onPress={() => setPaymentSource('STRIPE')} />
          <Option selected={paymentSource === 'OFFLINE'} label="Recorded offline" onPress={() => setPaymentSource('OFFLINE')} />

          <ThemedText type="smallBold" style={styles.groupTitle}>Fulfillment</ThemedText>
          <Option selected={!fulfillmentStatus} label="All fulfillment statuses" onPress={() => setFulfillmentStatus('')} />
          {(Object.keys(FULFILLMENT_LABELS) as FulfillmentStatus[]).map((value) => (
            <Option
              key={value}
              selected={fulfillmentStatus === value}
              label={FULFILLMENT_LABELS[value]}
              onPress={() => setFulfillmentStatus(value)}
            />
          ))}
        </ScrollView>
        <Pressable style={styles.done} onPress={() => setFilterOpen(false)}>
          <ThemedText type="smallBold">Done</ThemedText>
        </Pressable>
      </Modal>

      <Modal visible={sortOpen} onClose={() => setSortOpen(false)}>
        <ThemedText type="smallBold" style={styles.modalTitle}>Sort orders</ThemedText>
        {([
          ['NEWEST', 'Newest'],
          ['OLDEST', 'Oldest'],
          ['SUPPORTER_ASC', 'Supporter A–Z'],
          ['STATUS_ASC', 'Payment status'],
          ['FULFILLMENT_ASC', 'Fulfillment status'],
        ] as const).map(([value, label]) => (
          <Option
            key={value}
            selected={sort === value}
            label={label}
            onPress={() => {
              setSort(value);
              setSortOpen(false);
            }}
          />
        ))}
      </Modal>
    </ThemedView>
  );

  function Option({
    selected,
    label,
    onPress,
  }: {
    selected: boolean;
    label: string;
    onPress: () => void;
  }) {
    return (
      <Pressable accessibilityRole="button" onPress={onPress} style={styles.option}>
        <ThemedText type={selected ? 'smallBold' : 'default'}>{label}</ThemedText>
        {selected && <Ionicons name="checkmark" size={18} color={theme.text} />}
      </Pressable>
    );
  }
}

function StatusBadge({ label, attention }: { label: string; attention?: boolean }) {
  return (
    <View style={[styles.badge, attention && styles.badgeAttention]}>
      <ThemedText type="small" style={attention ? styles.badgeAttentionText : undefined}>
        {label}
      </ThemedText>
    </View>
  );
}

function formatAddress(address: {
  line1: string | null;
  line2: string | null;
  city: string | null;
  state: string | null;
  postalCode: string | null;
}) {
  return [
    address.line1,
    address.line2,
    [address.city, address.state].filter(Boolean).join(', '),
    address.postalCode,
  ]
    .filter(Boolean)
    .join(' · ');
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  content: {
    paddingHorizontal: Spacing.four,
    paddingBottom: Spacing.six,
    gap: Spacing.three,
  },
  flexOne: { flex: 1 },
  storeSelector: {
    minHeight: 58,
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.three,
    borderRadius: Spacing.three,
    padding: Spacing.three,
  },
  orderCard: {
    borderRadius: Spacing.three,
    padding: Spacing.three,
    gap: Spacing.two,
  },
  cardTop: { flexDirection: 'row', alignItems: 'flex-start', gap: Spacing.two },
  metaRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    justifyContent: 'space-between',
    gap: Spacing.two,
  },
  badge: {
    borderRadius: 999,
    paddingHorizontal: Spacing.two,
    paddingVertical: 3,
    backgroundColor: 'rgba(113,128,150,0.14)',
  },
  badgeAttention: { backgroundColor: 'rgba(201,54,54,0.12)' },
  badgeAttentionText: { color: Brand.errorRed },
  modalTitle: { marginBottom: Spacing.two },
  modalScroll: { maxHeight: 440 },
  groupTitle: { marginTop: Spacing.two, marginBottom: Spacing.one },
  option: {
    minHeight: 46,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: Spacing.two,
  },
  done: {
    minHeight: 46,
    alignItems: 'center',
    justifyContent: 'center',
    marginTop: Spacing.three,
  },
});
