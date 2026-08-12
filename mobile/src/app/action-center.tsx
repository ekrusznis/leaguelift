import { router } from 'expo-router';
import { Pressable, ScrollView, StyleSheet, View } from 'react-native';

import { EmptyState } from '@/components/empty-state';
import { ErrorState } from '@/components/error-state';
import { LoadingState } from '@/components/loading-state';
import { ScreenHeader } from '@/components/screen-header';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { useActionCenter } from '@/features/actionCenter/api';
import { actionCenterDestination } from '@/features/actionCenter/destination';
import type { ActionCenterItem, ActionCenterPriority } from '@/features/actionCenter/types';
import { Brand, Spacing } from '@/constants/theme';

const PRIORITY_COLOR: Record<ActionCenterPriority, string> = {
  URGENT: Brand.errorRed,
  HIGH: Brand.errorRed,
  NORMAL: Brand.championshipGold,
  INFO: Brand.infoBlue,
};

const PRIORITY_LABEL: Record<ActionCenterPriority, string> = {
  URGENT: 'Urgent',
  HIGH: 'High priority',
  NORMAL: 'Action needed',
  INFO: 'For your information',
};

/**
 * Cross-persona to-do list (Phase 37.10, ADR-119) — every role (Owner/Coach/Parent/
 * Athlete/Platform Admin) sees the same GET /me/action-center, aggregated server-side
 * across every organization context the user has. Mirrors
 * frontend/src/features/actionCenter/ActionCenterPage.tsx's summary-tiles-plus-list
 * shape; items sorted by priority, same as web.
 */
export default function ActionCenterScreen() {
  const actionCenterQuery = useActionCenter();
  const data = actionCenterQuery.data;
  const items = data ? [...data.items].sort((a, b) => priorityRank(b.priority) - priorityRank(a.priority)) : [];

  return (
    <ThemedView style={styles.container}>
      <ScreenHeader title="Action Center" />
      <ScrollView contentContainerStyle={styles.scrollContent}>
        {actionCenterQuery.isLoading && <LoadingState label="Loading your action items…" />}
        {actionCenterQuery.isError && (
          <ErrorState message="Could not load your action items." onRetry={() => actionCenterQuery.refetch()} />
        )}
        {data && (
          <View style={styles.summaryRow}>
            <SummaryTile label="Total" value={data.totalCount} />
            <SummaryTile label="High priority" value={data.highPriorityCount} accent={data.highPriorityCount > 0} />
          </View>
        )}
        {data && items.length === 0 && <EmptyState title="All caught up" description="Nothing needs your attention right now." />}
        <View style={styles.list}>
          {items.map((item) => (
            <ActionCenterRow key={item.id} item={item} />
          ))}
        </View>
      </ScrollView>
    </ThemedView>
  );
}

function priorityRank(priority: ActionCenterPriority): number {
  return { URGENT: 3, HIGH: 2, NORMAL: 1, INFO: 0 }[priority];
}

function SummaryTile({ label, value, accent }: { label: string; value: number; accent?: boolean }) {
  return (
    <ThemedView type="backgroundElement" style={styles.tile}>
      <ThemedText type="title" style={accent ? { color: Brand.errorRed } : undefined}>
        {value}
      </ThemedText>
      <ThemedText type="small" themeColor="textSecondary">
        {label}
      </ThemedText>
    </ThemedView>
  );
}

function ActionCenterRow({ item }: { item: ActionCenterItem }) {
  return (
    <Pressable onPress={() => router.push(actionCenterDestination(item))}>
      <ThemedView type="backgroundElement" style={styles.card}>
        <View style={styles.cardHeader}>
          <ThemedText type="smallBold" style={styles.cardTitle}>
            {item.title}
          </ThemedText>
          <View style={[styles.badge, { borderColor: PRIORITY_COLOR[item.priority] }]}>
            <ThemedText type="small" style={{ color: PRIORITY_COLOR[item.priority] }}>
              {PRIORITY_LABEL[item.priority]}
            </ThemedText>
          </View>
        </View>
        <ThemedText type="small" themeColor="textSecondary">
          {item.description}
        </ThemedText>
        {item.dueAt && (
          <ThemedText type="small" themeColor="textSecondary" style={styles.due}>
            Due {new Date(item.dueAt).toLocaleDateString()}
          </ThemedText>
        )}
      </ThemedView>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  scrollContent: {
    paddingHorizontal: Spacing.four,
    paddingBottom: Spacing.six,
    gap: Spacing.three,
  },
  summaryRow: {
    flexDirection: 'row',
    gap: Spacing.three,
  },
  tile: {
    flex: 1,
    borderRadius: Spacing.three,
    padding: Spacing.three,
    alignItems: 'center',
    gap: Spacing.one,
  },
  list: {
    gap: Spacing.two,
  },
  card: {
    borderRadius: Spacing.three,
    padding: Spacing.three,
    gap: Spacing.one,
  },
  cardHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: Spacing.two,
  },
  cardTitle: {
    flex: 1,
  },
  badge: {
    borderWidth: 1,
    borderRadius: 999,
    paddingHorizontal: Spacing.two,
    paddingVertical: 2,
  },
  due: {
    marginTop: Spacing.one,
  },
});
