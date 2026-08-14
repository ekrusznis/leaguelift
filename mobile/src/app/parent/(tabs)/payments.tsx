import Ionicons from '@expo/vector-icons/Ionicons';
import { router } from 'expo-router';
import { useMemo, useState } from 'react';
import { Pressable, ScrollView, StyleSheet, View } from 'react-native';

import { EmptyState } from '@/components/empty-state';
import { ErrorState } from '@/components/error-state';
import { ListControls } from '@/components/list-controls';
import { ListFooter } from '@/components/list-footer';
import { LoadingState } from '@/components/loading-state';
import { Modal } from '@/components/modal';
import { PlatformStatusSpacer } from '@/components/platform-status-spacer';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { useFamilyCreditBalance } from '@/features/credits/api';
import { useOutstandingBalance } from '@/features/fees/api';
import {
  flattenPages,
  useInfiniteHouseholdFeeSearch,
  type FeeAssignmentSearchSort,
  type FeeAssignmentStatus,
} from '@/features/fees/searchApi';
import { useHouseholdCtx } from '@/features/household/HouseholdContext';
import { Brand, Spacing } from '@/constants/theme';
import { formatMoneyMinorUnits } from '@/lib/money';
import { useTheme } from '@/hooks/use-theme';

const STATUS_LABELS: Record<FeeAssignmentStatus, string> = {
  OPEN: 'Open',
  PARTIALLY_PAID: 'Partially paid',
  PAID: 'Paid',
  WAIVED: 'Waived',
  CANCELLED: 'Cancelled',
};

export default function PaymentsScreen() {
  const household = useHouseholdCtx();
  const theme = useTheme();
  const balanceQuery = useOutstandingBalance(household.organizationId, household.householdId);
  const creditsQuery = useFamilyCreditBalance(household.organizationId, household.householdId);

  const [query, setQuery] = useState('');
  const [status, setStatus] = useState<FeeAssignmentStatus | ''>('');
  const [overdueOnly, setOverdueOnly] = useState(false);
  const [sort, setSort] = useState<FeeAssignmentSearchSort>('DUE_DATE_ASC');
  const [filterOpen, setFilterOpen] = useState(false);
  const [sortOpen, setSortOpen] = useState(false);

  const feesQuery = useInfiniteHouseholdFeeSearch(
    household.organizationId,
    household.householdId,
    { q: query, status, overdueOnly, sort },
  );
  const fees = useMemo(() => flattenPages(feesQuery.data?.pages), [feesQuery.data?.pages]);
  const total = feesQuery.data?.pages[0]?.totalElements ?? 0;

  const activeFilters = [
    ...(status ? [STATUS_LABELS[status]] : []),
    ...(overdueOnly ? ['Overdue only'] : []),
  ];

  const sortLabel =
    sort === 'DUE_DATE_ASC' ? 'Due soonest'
    : sort === 'DUE_DATE_DESC' ? 'Due latest'
    : sort === 'BALANCE_DESC' ? 'Balance high–low'
    : sort === 'BALANCE_ASC' ? 'Balance low–high'
    : sort === 'DESCRIPTION_ASC' ? 'Fee A–Z'
    : sort === 'NEWEST' ? 'Newest'
    : sort === 'OLDEST' ? 'Oldest'
    : 'Household A–Z';

  return (
    <ThemedView style={styles.container}>
      <PlatformStatusSpacer />
      <View style={styles.header}>
        <ThemedText type="smallBold">Payments</ThemedText>
      </View>

      <ScrollView contentContainerStyle={styles.scrollContent} keyboardShouldPersistTaps="handled">
        {balanceQuery.isLoading && <LoadingState label="Loading balance…" />}
        {balanceQuery.isError && (
          <ErrorState message="Could not load your balance." onRetry={() => balanceQuery.refetch()} />
        )}
        {balanceQuery.data && (
          <ThemedView type="backgroundElement" style={styles.balanceCard}>
            <ThemedText type="small" themeColor="textSecondary">Total outstanding</ThemedText>
            <ThemedText type="title" style={styles.balanceAmount}>
              {formatMoneyMinorUnits(
                balanceQuery.data.totalOutstandingMinor,
                balanceQuery.data.currency,
              )}
            </ThemedText>
          </ThemedView>
        )}

        {creditsQuery.isLoading && <LoadingState label="Loading credits…" />}
        {creditsQuery.data && (
          <ThemedView type="backgroundElement" style={styles.creditCard}>
            <View style={styles.creditRow}>
              <ThemedText type="small" themeColor="textSecondary">Family credit available</ThemedText>
              <ThemedText type="smallBold">
                {formatMoneyMinorUnits(
                  creditsQuery.data.availableMinor,
                  creditsQuery.data.currency,
                )}
              </ThemedText>
            </View>
            {creditsQuery.data.pendingMinor > 0 && (
              <View style={styles.creditRow}>
                <ThemedText type="small" themeColor="textSecondary">Pending</ThemedText>
                <ThemedText type="small" themeColor="textSecondary">
                  {formatMoneyMinorUnits(
                    creditsQuery.data.pendingMinor,
                    creditsQuery.data.currency,
                  )}
                </ThemedText>
              </View>
            )}
          </ThemedView>
        )}

        <ThemedText type="smallBold" style={styles.sectionTitle}>Fees</ThemedText>
        <ListControls
          query={query}
          onChangeQuery={setQuery}
          searchPlaceholder="Search fees"
          resultCount={total}
          activeFilters={activeFilters}
          onRemoveFilter={(index) => {
            if (status && index === 0) setStatus('');
            else setOverdueOnly(false);
          }}
          onClearFilters={() => {
            setQuery('');
            setStatus('');
            setOverdueOnly(false);
            setSort('DUE_DATE_ASC');
          }}
          onPressFilter={() => setFilterOpen(true)}
          onPressSort={() => setSortOpen(true)}
          sortLabel={sortLabel}
        />

        {feesQuery.isLoading && <LoadingState label="Loading fees…" />}
        {feesQuery.isError && (
          <ErrorState message="Could not load fees." onRetry={() => feesQuery.refetch()} />
        )}
        {!feesQuery.isLoading && !feesQuery.isError && fees.length === 0 && (
          <EmptyState
            title={query.trim() || activeFilters.length > 0 ? 'No results found' : 'No fees'}
            description={
              query.trim() || activeFilters.length > 0
                ? 'Try changing your search or filters.'
                : 'No fees have been assigned to your household yet.'
            }
          />
        )}

        <View style={styles.list}>
          {fees.map((fee) => (
            <Pressable
              key={fee.id}
              accessibilityRole="button"
              onPress={() => router.push({ pathname: '/fee-details', params: { id: fee.id } })}>
              <ThemedView type="backgroundElement" style={styles.feeRow}>
                <View style={styles.feeBody}>
                  <ThemedText type="smallBold">{fee.description}</ThemedText>
                  <ThemedText type="small" themeColor="textSecondary">
                    {STATUS_LABELS[fee.status as FeeAssignmentStatus] ?? fee.status}
                    {fee.dueDate
                      ? ` · due ${new Date(`${fee.dueDate}T12:00:00`).toLocaleDateString('en-US', {
                          month: 'short',
                          day: 'numeric',
                        })}`
                      : ''}
                  </ThemedText>
                </View>
                <ThemedText
                  type="smallBold"
                  style={fee.balanceMinor > 0 ? styles.feeBalanceDue : undefined}>
                  {formatMoneyMinorUnits(fee.balanceMinor, fee.currency)}
                </ThemedText>
              </ThemedView>
            </Pressable>
          ))}
        </View>

        <ListFooter
          loadedCount={fees.length}
          totalCount={total}
          hasMore={!!feesQuery.hasNextPage}
          loadingMore={feesQuery.isFetchingNextPage}
          onLoadMore={() => feesQuery.fetchNextPage()}
        />
      </ScrollView>

      <Modal visible={filterOpen} onClose={() => setFilterOpen(false)}>
        <ThemedText type="smallBold" style={styles.modalTitle}>Filter fees</ThemedText>
        <ScrollView style={styles.modalScroll} showsVerticalScrollIndicator={false}>
          <Option selected={!status} label="All statuses" onPress={() => setStatus('')} />
          {(Object.keys(STATUS_LABELS) as FeeAssignmentStatus[]).map((value) => (
            <Option
              key={value}
              selected={status === value}
              label={STATUS_LABELS[value]}
              onPress={() => setStatus(value)}
            />
          ))}
          <Option
            selected={overdueOnly}
            label="Overdue only"
            onPress={() => setOverdueOnly((value) => !value)}
          />
        </ScrollView>
        <Pressable style={styles.done} onPress={() => setFilterOpen(false)}>
          <ThemedText type="smallBold">Done</ThemedText>
        </Pressable>
      </Modal>

      <Modal visible={sortOpen} onClose={() => setSortOpen(false)}>
        <ThemedText type="smallBold" style={styles.modalTitle}>Sort fees</ThemedText>
        {([
          ['DUE_DATE_ASC', 'Due soonest'],
          ['DUE_DATE_DESC', 'Due latest'],
          ['BALANCE_DESC', 'Balance high–low'],
          ['BALANCE_ASC', 'Balance low–high'],
          ['DESCRIPTION_ASC', 'Fee A–Z'],
          ['NEWEST', 'Newest'],
          ['OLDEST', 'Oldest'],
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

const styles = StyleSheet.create({
  container: { flex: 1 },
  header: { paddingHorizontal: Spacing.four, paddingVertical: Spacing.two },
  scrollContent: {
    paddingHorizontal: Spacing.four,
    paddingBottom: Spacing.six,
    gap: Spacing.two,
  },
  balanceCard: {
    borderRadius: Spacing.three,
    padding: Spacing.three,
    gap: 2,
    marginBottom: Spacing.two,
  },
  balanceAmount: { fontSize: 28 },
  creditCard: {
    borderRadius: Spacing.three,
    padding: Spacing.three,
    gap: Spacing.one,
    marginBottom: Spacing.three,
  },
  creditRow: { flexDirection: 'row', justifyContent: 'space-between' },
  sectionTitle: { marginBottom: Spacing.two },
  list: { gap: Spacing.two },
  feeRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    gap: Spacing.three,
    borderRadius: Spacing.three,
    padding: Spacing.three,
  },
  feeBody: { flex: 1, gap: 2 },
  feeBalanceDue: { color: Brand.championshipGold },
  modalTitle: { marginBottom: Spacing.two },
  modalScroll: { maxHeight: 430 },
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
