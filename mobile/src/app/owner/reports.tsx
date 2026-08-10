import { ScrollView, StyleSheet, View } from 'react-native';

import { ErrorState } from '@/components/error-state';
import { LoadingState } from '@/components/loading-state';
import { ScreenHeader } from '@/components/screen-header';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { useDashboardContext } from '@/features/dashboard/api';
import { useFeeCollectionsReport, useRefundsReport, useRevenueReport } from '@/features/reporting/api';
import { Spacing } from '@/constants/theme';
import { formatMoneyMinorUnits } from '@/lib/money';

/** Revenue/fee-collections/refunds reports, trailing 30 days (server default), read-only this slice (ADR-105). CSV export not wired to mobile yet. */
export default function OwnerReportsScreen() {
  const dashboardContext = useDashboardContext(true);
  const organizationId = dashboardContext.data?.organizationId ?? null;
  const revenueQuery = useRevenueReport(organizationId);
  const feeCollectionsQuery = useFeeCollectionsReport(organizationId);
  const refundsQuery = useRefundsReport(organizationId);

  return (
    <ThemedView style={styles.container}>
      <ScreenHeader title="Reports" />
      <ScrollView contentContainerStyle={styles.content}>
        <ThemedText type="smallBold">Revenue</ThemedText>
        {revenueQuery.isLoading && <LoadingState label="Loading revenue…" />}
        {revenueQuery.isError && <ErrorState message="Could not load the revenue report." onRetry={() => revenueQuery.refetch()} />}
        {revenueQuery.data && (
          <ThemedView type="backgroundElement" style={styles.card}>
            <ThemedText type="title" style={styles.totalValue}>
              {formatMoneyMinorUnits(revenueQuery.data.totalMinor, 'USD')}
            </ThemedText>
            <ThemedText type="small" themeColor="textSecondary">
              {revenueQuery.data.from} – {revenueQuery.data.to}
            </ThemedText>
            {revenueQuery.data.bySourceType.map((row) => (
              <View key={row.sourceType} style={styles.breakdownRow}>
                <ThemedText type="small" themeColor="textSecondary">
                  {row.sourceType}
                </ThemedText>
                <ThemedText type="small">{formatMoneyMinorUnits(row.amountMinor, 'USD')}</ThemedText>
              </View>
            ))}
          </ThemedView>
        )}

        <ThemedText type="smallBold" style={styles.sectionTitle}>
          Fee Collections
        </ThemedText>
        {feeCollectionsQuery.isLoading && <LoadingState label="Loading fee collections…" />}
        {feeCollectionsQuery.isError && (
          <ErrorState message="Could not load fee collections." onRetry={() => feeCollectionsQuery.refetch()} />
        )}
        {feeCollectionsQuery.data && (
          <ThemedView type="backgroundElement" style={styles.card}>
            <View style={styles.breakdownRow}>
              <ThemedText type="small" themeColor="textSecondary">
                Collected
              </ThemedText>
              <ThemedText type="smallBold">{formatMoneyMinorUnits(feeCollectionsQuery.data.collectedMinor, 'USD')}</ThemedText>
            </View>
            <View style={styles.breakdownRow}>
              <ThemedText type="small" themeColor="textSecondary">
                Outstanding
              </ThemedText>
              <ThemedText type="smallBold">{formatMoneyMinorUnits(feeCollectionsQuery.data.outstandingMinor, 'USD')}</ThemedText>
            </View>
          </ThemedView>
        )}

        <ThemedText type="smallBold" style={styles.sectionTitle}>
          Refunds
        </ThemedText>
        {refundsQuery.isLoading && <LoadingState label="Loading refunds…" />}
        {refundsQuery.isError && <ErrorState message="Could not load refunds." onRetry={() => refundsQuery.refetch()} />}
        {refundsQuery.data && (
          <ThemedView type="backgroundElement" style={styles.card}>
            <View style={styles.breakdownRow}>
              <ThemedText type="small" themeColor="textSecondary">
                {refundsQuery.data.count} refund{refundsQuery.data.count === 1 ? '' : 's'}
              </ThemedText>
              <ThemedText type="smallBold">{formatMoneyMinorUnits(refundsQuery.data.totalMinor, 'USD')}</ThemedText>
            </View>
          </ThemedView>
        )}
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
  sectionTitle: {
    marginTop: Spacing.four,
  },
  card: {
    borderRadius: Spacing.three,
    padding: Spacing.three,
    gap: Spacing.two,
  },
  totalValue: {
    fontSize: 28,
    lineHeight: 32,
  },
  breakdownRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
});
