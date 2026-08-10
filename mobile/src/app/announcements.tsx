import { router } from 'expo-router';
import { useMemo, useState } from 'react';
import { Pressable, ScrollView, StyleSheet, View } from 'react-native';

import { EmptyState } from '@/components/empty-state';
import { ErrorState } from '@/components/error-state';
import { LoadingState } from '@/components/loading-state';
import { ScreenHeader } from '@/components/screen-header';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { useMarkAnnouncementRead, useMyAnnouncements } from '@/features/announcements/api';
import { Brand, Spacing } from '@/constants/theme';

type Filter = 'ALL' | 'UNREAD';
const FILTERS: { value: Filter; label: string }[] = [
  { value: 'ALL', label: 'All' },
  { value: 'UNREAD', label: 'Unread' },
];

/** Real /me/announcements — the Communication domain, not the fake coach-dashboard demo card (ADR-102). */
export default function AnnouncementsScreen() {
  const [filter, setFilter] = useState<Filter>('ALL');
  const announcementsQuery = useMyAnnouncements();
  const markRead = useMarkAnnouncementRead();

  const visible = useMemo(() => {
    const items = announcementsQuery.data?.items ?? [];
    return filter === 'UNREAD' ? items.filter((item) => !item.readAt) : items;
  }, [announcementsQuery.data, filter]);

  return (
    <ThemedView style={styles.container}>
      <ScreenHeader title="Announcements" />

      <View style={styles.filterRow}>
        {FILTERS.map((option) => {
          const active = option.value === filter;
          return (
            <Pressable
              key={option.value}
              onPress={() => setFilter(option.value)}
              style={[styles.filterChip, active && { backgroundColor: Brand.championshipGold }]}>
              <ThemedText type="small" style={active ? styles.filterTextActive : undefined} themeColor={active ? undefined : 'textSecondary'}>
                {option.label}
              </ThemedText>
            </Pressable>
          );
        })}
      </View>

      {announcementsQuery.isLoading && <LoadingState label="Loading announcements…" />}
      {announcementsQuery.isError && (
        <ErrorState message="Could not load announcements." onRetry={() => announcementsQuery.refetch()} />
      )}

      <ScrollView contentContainerStyle={styles.list}>
        {announcementsQuery.data && visible.length === 0 && (
          <EmptyState title="Nothing here" description={filter === 'UNREAD' ? 'No unread announcements.' : 'No announcements yet.'} />
        )}
        {visible.map(({ announcement, readAt }) => (
          <Pressable
            key={announcement.id}
            onPress={() => {
              if (!readAt) markRead.mutate(announcement.id);
              router.push({ pathname: '/announcement-details', params: { id: announcement.id } });
            }}>
            <ThemedView type="backgroundElement" style={styles.card}>
              <View style={styles.cardHeader}>
                <ThemedText type="smallBold" style={styles.cardTitle}>
                  {announcement.title}
                </ThemedText>
                {!readAt && <View style={styles.unreadDot} />}
              </View>
              <ThemedText themeColor="textSecondary" numberOfLines={2}>
                {announcement.body}
              </ThemedText>
              <ThemedText type="small" themeColor="textSecondary" style={styles.meta}>
                {announcement.publishedAt ? new Date(announcement.publishedAt).toLocaleDateString('en-US', { month: 'short', day: 'numeric' }) : ''}
                {announcement.scopeName ? ` · ${announcement.scopeName}` : ''}
              </ThemedText>
            </ThemedView>
          </Pressable>
        ))}
      </ScrollView>
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  filterRow: {
    flexDirection: 'row',
    gap: Spacing.two,
    paddingHorizontal: Spacing.four,
    paddingBottom: Spacing.three,
  },
  filterChip: {
    paddingHorizontal: Spacing.three,
    paddingVertical: Spacing.one,
    borderRadius: Spacing.four,
    backgroundColor: '#102B46',
  },
  filterTextActive: {
    color: '#0B1F33',
  },
  list: {
    paddingHorizontal: Spacing.four,
    paddingBottom: Spacing.six,
    gap: Spacing.two,
  },
  card: {
    borderRadius: Spacing.three,
    padding: Spacing.three,
    gap: Spacing.one,
  },
  cardHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  cardTitle: {
    flex: 1,
  },
  unreadDot: {
    width: 8,
    height: 8,
    borderRadius: 4,
    backgroundColor: Brand.championshipGold,
  },
  meta: {
    marginTop: Spacing.one,
  },
});
