import Ionicons from '@expo/vector-icons/Ionicons';
import { router } from 'expo-router';
import { Pressable, ScrollView, StyleSheet, View } from 'react-native';

import { EmptyState } from '@/components/empty-state';
import { ErrorState } from '@/components/error-state';
import { LoadingState } from '@/components/loading-state';
import { PlatformStatusSpacer } from '@/components/platform-status-spacer';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { useDashboardContext } from '@/features/dashboard/api';
import {
  useOwnerFinancialOverview,
  useOwnerRecentActivity,
  useOwnerReportsSnapshot,
  useOwnerSummary,
  useOwnerTeamPerformance,
  useOwnerUpcomingEvents,
} from '@/features/owner/api';
import { Brand, Spacing } from '@/constants/theme';
import { formatMoneyMinorUnits } from '@/lib/money';

/**
 * Owner Dashboard (Home tab) — real backend data (ADR-105). No "attention required"
 * card: OwnerDashboardService.getAttentionRequired returns 4 unconditionally
 * hardcoded rows with no backing table, so there's no honest way to present it.
 * "Onboarding progress" card is the same — always isDemoData=true — also skipped.
 */
export default function OwnerDashboardScreen() {
  const dashboardContext = useDashboardContext(true);
  const organizationId = dashboardContext.data?.organizationId ?? null;

  const summaryQuery = useOwnerSummary(organizationId);
  const financialQuery = useOwnerFinancialOverview(organizationId);
  const teamPerformanceQuery = useOwnerTeamPerformance(organizationId);
  const upcomingEventsQuery = useOwnerUpcomingEvents(organizationId);
  const activityQuery = useOwnerRecentActivity(organizationId);
  const reportsSnapshotQuery = useOwnerReportsSnapshot(organizationId);

  if (dashboardContext.isLoading) {
    return (
      <ThemedView style={styles.container}>
        <PlatformStatusSpacer />
        <LoadingState label="Loading your organization…" />
      </ThemedView>
    );
  }

  if (!organizationId) {
    return (
      <ThemedView style={styles.container}>
        <PlatformStatusSpacer />
        <EmptyState
          title="Organization setup isn't finished"
          description="Finish your organization's onboarding on the Rally26 web app, then come back here."
        />
      </ThemedView>
    );
  }

  return (
    <ThemedView style={styles.container}>
      <PlatformStatusSpacer />
      <View style={styles.topBar}>
        <ThemedText type="title" style={styles.wordmark}>
          RALLY<ThemedText type="title" style={[styles.wordmark, styles.wordmarkAccent]}>26</ThemedText>
        </ThemedText>
      </View>

      <ScrollView contentContainerStyle={styles.scrollContent}>
        {summaryQuery.isLoading && <LoadingState label="Loading summary…" />}
        {summaryQuery.isError && <ErrorState message="Could not load your organization." onRetry={() => summaryQuery.refetch()} />}
        {summaryQuery.data && (
          <>
            <ThemedText type="title" style={styles.orgName}>
              {summaryQuery.data.organizationName}
            </ThemedText>
            <View style={styles.statsRow}>
              <StatCard label="Teams" value={summaryQuery.data.activeTeams} />
              <StatCard label="Athletes" value={summaryQuery.data.participants} />
              <StatCard label="Households" value={summaryQuery.data.households} />
              <StatCard label="Tournaments" value={summaryQuery.data.upcomingTournaments} />
            </View>
          </>
        )}

        <ThemedText type="smallBold" style={styles.sectionTitle}>
          Financials
        </ThemedText>
        {financialQuery.isLoading && <LoadingState label="Loading financials…" />}
        {financialQuery.isError && <ErrorState message="Could not load financials." onRetry={() => financialQuery.refetch()} />}
        {financialQuery.data && (
          <View style={styles.financialGrid}>
            <FinancialCard label="Fees Collected" valueMinor={financialQuery.data.feesCollectedMinor} currency={financialQuery.data.currency} />
            <FinancialCard label="Outstanding" valueMinor={financialQuery.data.outstandingMinor} currency={financialQuery.data.currency} />
            <FinancialCard
              label="Fundraising"
              valueMinor={financialQuery.data.fundraisingMinor}
              currency={financialQuery.data.currency}
              demo={financialQuery.data.isFundraisingDemoData}
            />
            <FinancialCard label="Apparel Sales" valueMinor={financialQuery.data.apparelSalesMinor} currency={financialQuery.data.currency} />
            <FinancialCard label="Pending Payout" valueMinor={financialQuery.data.pendingPayoutMinor} currency={financialQuery.data.currency} />
          </View>
        )}

        <View style={styles.sectionHeader}>
          <ThemedText type="smallBold">Team Performance</ThemedText>
        </View>
        {teamPerformanceQuery.isLoading && <LoadingState label="Loading teams…" />}
        {teamPerformanceQuery.data && teamPerformanceQuery.data.length === 0 && (
          <ThemedText type="small" themeColor="textSecondary">
            No teams yet.
          </ThemedText>
        )}
        <View style={styles.list}>
          {teamPerformanceQuery.data?.map((team) => (
            <ThemedView key={team.teamId} type="backgroundElement" style={styles.teamRow}>
              <View style={styles.teamRowBody}>
                <ThemedText type="smallBold">{team.name}</ThemedText>
                <ThemedText type="small" themeColor="textSecondary">
                  {team.sport} · {team.participants} athletes
                </ThemedText>
              </View>
              {!team.isFundraisingDemoData && team.fundraisingRaisedMinor != null && (
                <ThemedText type="small" themeColor="textSecondary">
                  {formatMoneyMinorUnits(team.fundraisingRaisedMinor, financialQuery.data?.currency ?? 'USD')} raised
                </ThemedText>
              )}
            </ThemedView>
          ))}
        </View>

        <View style={styles.sectionHeader}>
          <ThemedText type="smallBold">Upcoming Events</ThemedText>
        </View>
        {upcomingEventsQuery.isLoading && <LoadingState label="Loading events…" />}
        {upcomingEventsQuery.data && upcomingEventsQuery.data.length === 0 && (
          <ThemedText type="small" themeColor="textSecondary">
            Nothing scheduled yet.
          </ThemedText>
        )}
        <View style={styles.list}>
          {upcomingEventsQuery.data?.map((item) => (
            <Pressable key={item.id} onPress={() => router.push({ pathname: '/event-details', params: { id: item.id } })}>
              <ThemedView type="backgroundElement" style={styles.eventRow}>
                <ThemedText type="smallBold">{item.title}</ThemedText>
                <ThemedText type="small" themeColor="textSecondary">
                  {item.day} {item.date} · {item.time}
                </ThemedText>
                {item.subtitle && (
                  <ThemedText type="small" themeColor="textSecondary">
                    {item.subtitle}
                  </ThemedText>
                )}
              </ThemedView>
            </Pressable>
          ))}
        </View>

        <View style={styles.sectionHeader}>
          <ThemedText type="smallBold">Reports Snapshot</ThemedText>
          <ThemedText type="link" themeColor="textSecondary" onPress={() => router.push('/owner/reports')}>
            View All
          </ThemedText>
        </View>
        {reportsSnapshotQuery.isLoading && <LoadingState label="Loading reports…" />}
        <View style={styles.financialGrid}>
          {reportsSnapshotQuery.data?.map((metric) => (
            <FinancialCard key={metric.label} label={metric.label} valueMinor={metric.valueMinor} currency={financialQuery.data?.currency ?? 'USD'} />
          ))}
        </View>

        <View style={styles.sectionHeader}>
          <ThemedText type="smallBold">Recent Activity</ThemedText>
        </View>
        {activityQuery.isLoading && <LoadingState label="Loading activity…" />}
        {activityQuery.data && activityQuery.data.length === 0 && (
          <ThemedText type="small" themeColor="textSecondary">
            No recent activity.
          </ThemedText>
        )}
        <View style={styles.list}>
          {activityQuery.data?.map((item) => (
            <View key={item.id} style={styles.activityRow}>
              <Ionicons name="time-outline" size={16} color={Brand.slateGray} />
              <ThemedText type="small" themeColor="textSecondary" style={styles.activityText}>
                {item.action.replaceAll('_', ' ').replaceAll('.', ' ')} · {new Date(item.occurredAt).toLocaleString()}
              </ThemedText>
            </View>
          ))}
        </View>
      </ScrollView>
    </ThemedView>
  );
}

function StatCard({ label, value }: { label: string; value: number }) {
  return (
    <ThemedView type="backgroundElement" style={styles.statCard}>
      <ThemedText type="title" style={styles.statValue}>
        {value}
      </ThemedText>
      <ThemedText type="small" themeColor="textSecondary">
        {label}
      </ThemedText>
    </ThemedView>
  );
}

function FinancialCard({ label, valueMinor, currency, demo }: { label: string; valueMinor: number; currency: string; demo?: boolean }) {
  return (
    <ThemedView type="backgroundElement" style={styles.financialCard}>
      <ThemedText type="small" themeColor="textSecondary">
        {label}
        {demo ? ' (no active campaign)' : ''}
      </ThemedText>
      <ThemedText type="smallBold">{formatMoneyMinorUnits(valueMinor, currency)}</ThemedText>
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  topBar: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: Spacing.four,
    paddingVertical: Spacing.two,
  },
  wordmark: {
    fontSize: 20,
    lineHeight: 24,
  },
  wordmarkAccent: {
    color: Brand.championshipGold,
  },
  scrollContent: {
    paddingHorizontal: Spacing.four,
    paddingBottom: Spacing.six,
    gap: Spacing.two,
  },
  orgName: {
    fontSize: 28,
    lineHeight: 32,
    marginTop: Spacing.two,
    marginBottom: Spacing.two,
  },
  statsRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: Spacing.two,
    marginBottom: Spacing.two,
  },
  statCard: {
    flexGrow: 1,
    minWidth: '22%',
    borderRadius: Spacing.three,
    padding: Spacing.three,
    alignItems: 'center',
    gap: 2,
  },
  statValue: {
    fontSize: 22,
    lineHeight: 26,
  },
  sectionTitle: {
    marginTop: Spacing.two,
    marginBottom: Spacing.two,
  },
  sectionHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginTop: Spacing.three,
    marginBottom: Spacing.two,
  },
  financialGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: Spacing.two,
  },
  financialCard: {
    flexGrow: 1,
    minWidth: '45%',
    borderRadius: Spacing.three,
    padding: Spacing.three,
    gap: 2,
  },
  list: {
    gap: Spacing.two,
  },
  teamRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    borderRadius: Spacing.three,
    padding: Spacing.three,
  },
  teamRowBody: {
    gap: 2,
  },
  eventRow: {
    borderRadius: Spacing.three,
    padding: Spacing.three,
    gap: 2,
  },
  activityRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
    paddingVertical: 4,
  },
  activityText: {
    flex: 1,
  },
});
