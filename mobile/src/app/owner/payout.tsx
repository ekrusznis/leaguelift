import Ionicons from '@expo/vector-icons/Ionicons';
import * as WebBrowser from 'expo-web-browser';
import { ScrollView, StyleSheet, View } from 'react-native';

import { Button } from '@/components/button';
import { ConfirmDialog } from '@/components/confirm-dialog';
import { EmptyState } from '@/components/empty-state';
import { ErrorState } from '@/components/error-state';
import { LoadingState } from '@/components/loading-state';
import { ScreenHeader } from '@/components/screen-header';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { useToast } from '@/components/toast';
import { useDashboardContext } from '@/features/dashboard/api';
import {
  usePayoutAccount,
  usePayoutSummary,
  useRefreshPayoutAccount,
  useStartPayoutOnboarding,
  useTriggerPayoutTransfer,
} from '@/features/payout/api';
import { Brand, Spacing } from '@/constants/theme';
import { env } from '@/lib/env';
import { formatMoneyMinorUnits } from '@/lib/money';
import { useState } from 'react';

export default function OwnerPayoutScreen() {
  const toast = useToast();
  const dashboardContext = useDashboardContext(true);
  const organizationId = dashboardContext.data?.organizationId ?? null;
  const accountQuery = usePayoutAccount(organizationId);
  const summaryQuery = usePayoutSummary(organizationId);
  const onboarding = useStartPayoutOnboarding(organizationId);
  const refresh = useRefreshPayoutAccount(organizationId);
  const transfer = useTriggerPayoutTransfer(organizationId);
  const [transferConfirmOpen, setTransferConfirmOpen] = useState(false);

  async function startOnboarding() {
    if (!organizationId) return;
    try {
      // Stripe requires browser-safe HTTPS return/refresh URLs in live mode.
      // We use the organization's authenticated web overview as the safe fallback.
      // When the secure browser closes, the native app refreshes Stripe status itself.
      const webReturnUrl = `${env.frontendBaseUrl}/app/organizations/${organizationId}/overview`;
      const link = await onboarding.mutateAsync({
        refreshUrl: webReturnUrl,
        returnUrl: webReturnUrl,
      });
      await WebBrowser.openBrowserAsync(link.onboardingUrl);
      await refresh.mutateAsync();
      toast.show('Payout account status refreshed.', 'success');
    } catch {
      toast.show('Could not start or refresh Stripe payout onboarding.', 'error');
    }
  }

  async function refreshStatus() {
    try {
      await refresh.mutateAsync();
      toast.show('Payout account status refreshed.', 'success');
    } catch {
      toast.show('Could not refresh the payout account.', 'error');
    }
  }

  async function confirmTransfer() {
    setTransferConfirmOpen(false);
    try {
      await transfer.mutateAsync();
      toast.show('Payout transfer requested.', 'success');
    } catch {
      toast.show('Could not request the payout transfer.', 'error');
    }
  }

  const fullyConnected = accountQuery.data?.isFullyConnected === true;
  const netAvailable = summaryQuery.data?.netAvailableMinor ?? 0;
  const onboardingBusy = onboarding.isPending || refresh.isPending;

  return (
    <ThemedView style={styles.container}>
      <ScreenHeader title="Payout Account" />
      <ScrollView contentContainerStyle={styles.content}>
        {accountQuery.isLoading && <LoadingState label="Loading payout status…" />}
        {accountQuery.isError && (
          <ErrorState message="Could not load payout status." onRetry={() => accountQuery.refetch()} />
        )}

        {accountQuery.data === null && !accountQuery.isLoading && (
          <EmptyState
            title="No payout account connected"
            description="Connect securely with Stripe to receive eligible Rally26 payouts."
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

        {!fullyConnected && (
          <ThemedView type="backgroundElement" style={styles.browserNotice}>
            <Ionicons name="shield-checkmark-outline" size={21} color={Brand.championshipGold} />
            <View style={styles.flexOne}>
              <ThemedText type="smallBold">Secure Stripe onboarding</ThemedText>
              <ThemedText type="small" themeColor="textSecondary">
                Stripe onboarding opens in your device browser, not inside the Rally26 WebView.
                When you finish or save for later, return to Rally26 and the account status is refreshed.
              </ThemedText>
            </View>
          </ThemedView>
        )}

        <View style={styles.actions}>
          {!fullyConnected && (
            <Button disabled={onboardingBusy} onPress={startOnboarding}>
              {onboardingBusy
                ? 'Opening Stripe…'
                : accountQuery.data
                  ? 'Continue Stripe Setup'
                  : 'Connect with Stripe'}
            </Button>
          )}
          {accountQuery.data && (
            <Button variant="secondary" disabled={refresh.isPending} onPress={refreshStatus}>
              {refresh.isPending ? 'Refreshing…' : 'Refresh Status'}
            </Button>
          )}
        </View>

        <ThemedText type="smallBold" style={styles.sectionTitle}>Balance</ThemedText>
        {summaryQuery.isLoading && <LoadingState label="Loading balance…" />}
        {summaryQuery.isError && (
          <ErrorState message="Could not load payout balance." onRetry={() => summaryQuery.refetch()} />
        )}
        {summaryQuery.data && (
          <ThemedView type="backgroundElement" style={styles.card}>
            <BalanceRow label="Net Available" valueMinor={summaryQuery.data.netAvailableMinor} />
            <BalanceRow label="Eligible" valueMinor={summaryQuery.data.eligibleMinor} />
            <BalanceRow label="Held" valueMinor={summaryQuery.data.heldMinor} />
            <BalanceRow label="Pending Debits" valueMinor={summaryQuery.data.pendingDebitsMinor} />
          </ThemedView>
        )}

        {fullyConnected && netAvailable > 0 && (
          <View style={styles.transferSection}>
            <Button
              disabled={transfer.isPending}
              onPress={() => setTransferConfirmOpen(true)}>
              {transfer.isPending ? 'Requesting transfer…' : 'Transfer Available Balance'}
            </Button>
            <ThemedText type="small" themeColor="textSecondary">
              This requests a real payout transfer of the currently eligible net balance.
            </ThemedText>
          </View>
        )}
      </ScrollView>

      <ConfirmDialog
        visible={transferConfirmOpen}
        title="Transfer available balance?"
        message={`Request a real payout transfer of ${formatMoneyMinorUnits(netAvailable, 'USD')}? This action moves eligible organization funds through the connected Stripe payout account.`}
        confirmLabel="Transfer"
        onConfirm={confirmTransfer}
        onCancel={() => setTransferConfirmOpen(false)}
      />
    </ThemedView>
  );
}

function StatusRow({ label, ok }: { label: string; ok: boolean }) {
  return (
    <View style={styles.statusRow}>
      <Ionicons
        name={ok ? 'checkmark-circle' : 'close-circle'}
        size={18}
        color={ok ? Brand.victoryGreen : Brand.errorRed}
      />
      <ThemedText type="small">{label}</ThemedText>
    </View>
  );
}

function BalanceRow({ label, valueMinor }: { label: string; valueMinor: number }) {
  return (
    <View style={styles.breakdownRow}>
      <ThemedText type="small" themeColor="textSecondary">{label}</ThemedText>
      <ThemedText type="smallBold">{formatMoneyMinorUnits(valueMinor, 'USD')}</ThemedText>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  content: {
    paddingHorizontal: Spacing.four,
    paddingBottom: Spacing.six,
    gap: Spacing.three,
  },
  flexOne: { flex: 1 },
  sectionTitle: { marginTop: Spacing.two },
  card: {
    borderRadius: Spacing.three,
    padding: Spacing.three,
    gap: Spacing.two,
  },
  browserNotice: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: Spacing.three,
    borderRadius: Spacing.three,
    padding: Spacing.three,
  },
  actions: { flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.two },
  statusRow: { flexDirection: 'row', alignItems: 'center', gap: Spacing.two },
  breakdownRow: { flexDirection: 'row', justifyContent: 'space-between', gap: Spacing.three },
  transferSection: { gap: Spacing.two, marginTop: Spacing.two },
});
