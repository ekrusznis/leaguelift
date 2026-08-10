import { useLocalSearchParams } from 'expo-router';
import { ScrollView, StyleSheet, View } from 'react-native';

import { EmptyState } from '@/components/empty-state';
import { ErrorState } from '@/components/error-state';
import { LoadingState } from '@/components/loading-state';
import { ScreenHeader } from '@/components/screen-header';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { useDashboardContext } from '@/features/dashboard/api';
import { useFeeAssignments, useFeePayments } from '@/features/fees/api';
import { Spacing } from '@/constants/theme';
import { formatMoneyMinorUnits } from '@/lib/money';

/** Pushed from the Payments screen's fee list — payment history for one fee assignment (ADR-103). */
export default function FeeDetailsScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const dashboardContext = useDashboardContext(true);
  const organizationId = dashboardContext.data?.organizationId ?? null;
  const householdId = dashboardContext.data?.householdId ?? null;
  const feesQuery = useFeeAssignments(organizationId, householdId);
  const paymentsQuery = useFeePayments(organizationId, id ?? null);

  const fee = feesQuery.data?.items.find((item) => item.id === id);

  return (
    <ThemedView style={styles.container}>
      <ScreenHeader title={fee?.description ?? 'Fee'} />
      <ScrollView contentContainerStyle={styles.content}>
        {fee && (
          <ThemedView type="backgroundElement" style={styles.summaryCard}>
            <ThemedText type="title" style={styles.amount}>
              {formatMoneyMinorUnits(fee.balanceMinor, fee.currency)}
            </ThemedText>
            <ThemedText type="small" themeColor="textSecondary">
              {fee.status} · original {formatMoneyMinorUnits(fee.originalAmountMinor, fee.currency)}
              {fee.paidMinor > 0 ? ` · paid ${formatMoneyMinorUnits(fee.paidMinor, fee.currency)}` : ''}
            </ThemedText>
          </ThemedView>
        )}

        <ThemedText type="smallBold" style={styles.sectionTitle}>
          Payment History
        </ThemedText>
        {paymentsQuery.isLoading && <LoadingState label="Loading payments…" />}
        {paymentsQuery.isError && <ErrorState message="Could not load payment history." onRetry={() => paymentsQuery.refetch()} />}
        {paymentsQuery.data && paymentsQuery.data.length === 0 && (
          <EmptyState title="No payments yet" description="No payments have been recorded for this fee." />
        )}
        <View style={styles.list}>
          {paymentsQuery.data?.map((payment) => (
            <ThemedView key={payment.id} type="backgroundElement" style={styles.paymentRow}>
              <View style={styles.paymentBody}>
                <ThemedText type="smallBold">{formatMoneyMinorUnits(payment.amountMinor, payment.currency)}</ThemedText>
                <ThemedText type="small" themeColor="textSecondary">
                  {payment.method} · {new Date(payment.paidAt).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })}
                </ThemedText>
                {payment.voidedAt && (
                  <ThemedText type="small" style={styles.voided}>
                    Voided{payment.voidReason ? `: ${payment.voidReason}` : ''}
                  </ThemedText>
                )}
              </View>
            </ThemedView>
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
  content: {
    paddingHorizontal: Spacing.four,
    paddingBottom: Spacing.six,
    gap: Spacing.two,
  },
  summaryCard: {
    borderRadius: Spacing.three,
    padding: Spacing.three,
    gap: 2,
    marginBottom: Spacing.three,
  },
  amount: {
    fontSize: 28,
  },
  sectionTitle: {
    marginBottom: Spacing.two,
  },
  list: {
    gap: Spacing.two,
  },
  paymentRow: {
    borderRadius: Spacing.three,
    padding: Spacing.three,
  },
  paymentBody: {
    gap: 2,
  },
  voided: {
    color: '#C93636',
  },
});
