import { router } from 'expo-router';
import { Pressable, ScrollView, StyleSheet, View } from 'react-native';

import { EmptyState } from '@/components/empty-state';
import { ErrorState } from '@/components/error-state';
import { LoadingState } from '@/components/loading-state';
import { PlatformStatusSpacer } from '@/components/platform-status-spacer';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { useFamilyCreditBalance } from '@/features/credits/api';
import { useFeeAssignments, useOutstandingBalance } from '@/features/fees/api';
import { useHouseholdCtx } from '@/features/household/HouseholdContext';
import { Brand, Spacing } from '@/constants/theme';
import { formatMoneyMinorUnits } from '@/lib/money';

/** Payments (Payments tab) — real outstanding balance, itemized fees, and family credit balance (ADR-103). */
export default function PaymentsScreen() {
  const household = useHouseholdCtx();
  const balanceQuery = useOutstandingBalance(household.organizationId, household.householdId);
  const feesQuery = useFeeAssignments(household.organizationId, household.householdId);
  const creditsQuery = useFamilyCreditBalance(household.organizationId, household.householdId);

  return (
    <ThemedView style={styles.container}>
      <PlatformStatusSpacer />
      <View style={styles.header}>
        <ThemedText type="smallBold">Payments</ThemedText>
      </View>

      <ScrollView contentContainerStyle={styles.scrollContent}>
        {balanceQuery.isLoading && <LoadingState label="Loading balance…" />}
        {balanceQuery.isError && <ErrorState message="Could not load your balance." onRetry={() => balanceQuery.refetch()} />}
        {balanceQuery.data && (
          <ThemedView type="backgroundElement" style={styles.balanceCard}>
            <ThemedText type="small" themeColor="textSecondary">
              Total outstanding
            </ThemedText>
            <ThemedText type="title" style={styles.balanceAmount}>
              {formatMoneyMinorUnits(balanceQuery.data.totalOutstandingMinor, balanceQuery.data.currency)}
            </ThemedText>
          </ThemedView>
        )}

        {creditsQuery.isLoading && <LoadingState label="Loading credits…" />}
        {creditsQuery.data && (
          <ThemedView type="backgroundElement" style={styles.creditCard}>
            <View style={styles.creditRow}>
              <ThemedText type="small" themeColor="textSecondary">
                Family credit available
              </ThemedText>
              <ThemedText type="smallBold">{formatMoneyMinorUnits(creditsQuery.data.availableMinor, creditsQuery.data.currency)}</ThemedText>
            </View>
            {creditsQuery.data.pendingMinor > 0 && (
              <View style={styles.creditRow}>
                <ThemedText type="small" themeColor="textSecondary">
                  Pending
                </ThemedText>
                <ThemedText type="small" themeColor="textSecondary">
                  {formatMoneyMinorUnits(creditsQuery.data.pendingMinor, creditsQuery.data.currency)}
                </ThemedText>
              </View>
            )}
          </ThemedView>
        )}

        <ThemedText type="smallBold" style={styles.sectionTitle}>
          Fees
        </ThemedText>
        {feesQuery.isLoading && <LoadingState label="Loading fees…" />}
        {feesQuery.isError && <ErrorState message="Could not load fees." onRetry={() => feesQuery.refetch()} />}
        {feesQuery.data && feesQuery.data.items.length === 0 && (
          <EmptyState title="No fees" description="No fees have been assigned to your household yet." />
        )}
        <View style={styles.list}>
          {feesQuery.data?.items.map((fee) => (
            <Pressable key={fee.id} onPress={() => router.push({ pathname: '/fee-details', params: { id: fee.id } })}>
              <ThemedView type="backgroundElement" style={styles.feeRow}>
                <View style={styles.feeBody}>
                  <ThemedText type="smallBold">{fee.description}</ThemedText>
                  <ThemedText type="small" themeColor="textSecondary">
                    {fee.status} {fee.dueDate ? `· due ${new Date(fee.dueDate).toLocaleDateString('en-US', { month: 'short', day: 'numeric' })}` : ''}
                  </ThemedText>
                </View>
                <ThemedText type="smallBold" style={fee.balanceMinor > 0 ? styles.feeBalanceDue : undefined}>
                  {formatMoneyMinorUnits(fee.balanceMinor, fee.currency)}
                </ThemedText>
              </ThemedView>
            </Pressable>
          ))}
        </View>
      </ScrollView>
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  header: {
    paddingHorizontal: Spacing.four,
    paddingVertical: Spacing.two,
  },
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
  balanceAmount: {
    fontSize: 28,
  },
  creditCard: {
    borderRadius: Spacing.three,
    padding: Spacing.three,
    gap: Spacing.one,
    marginBottom: Spacing.three,
  },
  creditRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  sectionTitle: {
    marginBottom: Spacing.two,
  },
  list: {
    gap: Spacing.two,
  },
  feeRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    borderRadius: Spacing.three,
    padding: Spacing.three,
  },
  feeBody: {
    flex: 1,
    gap: 2,
  },
  feeBalanceDue: {
    color: Brand.championshipGold,
  },
});
