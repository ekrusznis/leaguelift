import { ScrollView, StyleSheet, View } from 'react-native';
import Ionicons from '@expo/vector-icons/Ionicons';

import { EmptyState } from '@/components/empty-state';
import { ErrorState } from '@/components/error-state';
import { LoadingState } from '@/components/loading-state';
import { ScreenHeader } from '@/components/screen-header';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { useDashboardContext } from '@/features/dashboard/api';
import { usePayoutAccount, usePayoutSummary } from '@/features/payout/api';
import { Brand, Spacing } from '@/constants/theme';
import { formatMoneyMinorUnits } from '@/lib/money';

/**
 * Payout account status (read-only, ADR-105). The `onboarding-link`/`refresh`/
 * `transfer` actions are real on the backend but deliberately deferred to a later
 * slice — onboarding-link hands off to a Stripe-hosted WebView and transfer moves real
 * money, both worth validating this read-only view against real orgs first.
 */
export default function OwnerPayoutScreen() {
  const dashboardContext = useDashboardContext(true);
  const organizationId = dashboardContext.data?.organizationId ?? null;
  const accountQuery = usePayoutAccount(organizationId);
  const summaryQuery = usePayoutSummary(organizationId);

  return (
    <ThemedView style={styles.container}>
      <ScreenHeader title="Payout Account" />
      <ScrollView contentContainerStyle={styles.content}>
        {accountQuery.isLoading && <LoadingState label="Loading payout status…" />}
        {accountQuery.isError && <ErrorState message="Could not load payout status." onRetry={() => accountQuery.refetch()} />}
        {accountQuery.data === null && !accountQuery.isLoading && (
          <EmptyState
            title="No payout account connected"
            description="Connect a Stripe payout account on the Rally26 web app to start receiving payouts."
          />
        )}
        {accountQuery.data && (
          <ThemedView type="backgroundElement" style={styles.card}>
            <StatusRow label="Details Submitted" ok={accountQuery.data.detailsSubmitted} />
            <StatusRow label="Charges Enabled" ok={accountQuery.data.chargesEnabled} />
            <StatusRow label="Payouts Enabled" ok={accountQuery.data.payoutsEnabled} />
            <StatusRow label="Fully Connected" ok={accountQuery.data.isFullyConnected} />
          </ThemedView>
        )}

        <ThemedText type="smallBold" style={styles.sectionTitle}>
          Balance
        </ThemedText>
        {summaryQuery.isLoading && <LoadingState label="Loading balance…" />}
        {summaryQuery.isError && <ErrorState message="Could not load payout balance." onRetry={() => summaryQuery.refetch()} />}
        {summaryQuery.data && (
          <ThemedView type="backgroundElement" style={styles.card}>
            <BalanceRow label="Net Available" valueMinor={summaryQuery.data.netAvailableMinor} />
            <BalanceRow label="Eligible" valueMinor={summaryQuery.data.eligibleMinor} />
            <BalanceRow label="Held" valueMinor={summaryQuery.data.heldMinor} />
            <BalanceRow label="Pending Debits" valueMinor={summaryQuery.data.pendingDebitsMinor} />
          </ThemedView>
        )}
      </ScrollView>
    </ThemedView>
  );
}

function StatusRow({ label, ok }: { label: string; ok: boolean }) {
  return (
    <View style={styles.statusRow}>
      <Ionicons name={ok ? 'checkmark-circle' : 'close-circle'} size={18} color={ok ? Brand.victoryGreen : Brand.errorRed} />
      <ThemedText type="small">{label}</ThemedText>
    </View>
  );
}

function BalanceRow({ label, valueMinor }: { label: string; valueMinor: number }) {
  return (
    <View style={styles.breakdownRow}>
      <ThemedText type="small" themeColor="textSecondary">
        {label}
      </ThemedText>
      <ThemedText type="smallBold">{formatMoneyMinorUnits(valueMinor, 'USD')}</ThemedText>
    </View>
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
  statusRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
  },
  breakdownRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
});
