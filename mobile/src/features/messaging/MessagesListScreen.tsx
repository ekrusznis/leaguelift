import Ionicons from '@expo/vector-icons/Ionicons';
import { router } from 'expo-router';
import { useMemo, useState } from 'react';
import { FlatList, Pressable, StyleSheet, TextInput, View } from 'react-native';

import { EmptyState } from '@/components/empty-state';
import { ErrorState } from '@/components/error-state';
import { LoadingState } from '@/components/loading-state';
import { PlatformStatusSpacer } from '@/components/platform-status-spacer';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { Brand, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

import { useMyMessageThreads } from './api';
import { formatRelativeListTime } from './dateFormat';
import type { MyMessageThreadResponse } from './types';

type FilterKey = 'ALL' | 'UNREAD' | 'TEAMS' | 'DIRECT';

const FILTERS: { key: FilterKey; label: string }[] = [
  { key: 'ALL', label: 'All' },
  { key: 'UNREAD', label: 'Unread' },
  { key: 'TEAMS', label: 'Teams' },
  { key: 'DIRECT', label: 'Direct' },
];

/**
 * A thread with more than two total recipients reads as a team/group conversation;
 * two or fewer (just the viewer + one other person) reads as direct — the closest
 * "Direct" heuristic derivable from the existing thread model without a new backend
 * field, since CONVERSATION threads don't otherwise distinguish 1:1 from group.
 */
function isDirectThread(item: MyMessageThreadResponse): boolean {
  return item.thread.threadType !== 'BROADCAST' && item.thread.recipientCount <= 2;
}

function matchesFilter(item: MyMessageThreadResponse, filter: FilterKey): boolean {
  switch (filter) {
    case 'UNREAD':
      return item.unreadCount > 0;
    case 'TEAMS':
      return !isDirectThread(item);
    case 'DIRECT':
      return isDirectThread(item);
    default:
      return true;
  }
}

function initials(label: string): string {
  const words = label.trim().split(/\s+/).filter(Boolean);
  if (words.length === 0) return '?';
  if (words.length === 1) return words[0].slice(0, 2).toUpperCase();
  return (words[0][0] + words[1][0]).toUpperCase();
}

function ThreadAvatar({ item }: { item: MyMessageThreadResponse }) {
  if (item.thread.threadType === 'BROADCAST') {
    return (
      <View style={[styles.avatar, styles.avatarBroadcast]}>
        <Ionicons name="megaphone" size={18} color={Brand.pureWhite} />
      </View>
    );
  }
  return (
    <View style={[styles.avatar, styles.avatarPerson]}>
      <ThemedText type="small" style={styles.avatarText}>
        {initials(item.thread.scopeName ?? item.thread.title)}
      </ThemedText>
    </View>
  );
}

/**
 * Shared "Messages" tab across every persona — /me/message-threads is caller-scoped,
 * not coach-specific (ADR-103). `onNewConversation` is optional and only passed by the
 * Athlete persona (ADR-104), whose peer-to-peer conversation creation has no equivalent
 * for Coach (broadcasts) or Parent (no self-initiated conversations at all).
 */
export function MessagesListScreen({
  onNewConversation,
  oversightLink,
}: { onNewConversation?: () => void; oversightLink?: { label: string; onPress: () => void } } = {}) {
  const threadsQuery = useMyMessageThreads();
  const theme = useTheme();
  const [query, setQuery] = useState('');
  const [filter, setFilter] = useState<FilterKey>('ALL');

  const items = useMemo(() => threadsQuery.data?.items ?? [], [threadsQuery.data]);
  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    return items
      .filter((item) => matchesFilter(item, filter))
      .filter((item) => {
        if (!q) return true;
        return item.thread.title.toLowerCase().includes(q) || (item.thread.scopeName ?? '').toLowerCase().includes(q);
      })
      .sort((a, b) => {
        const at = a.lastMessageAt ? new Date(a.lastMessageAt).getTime() : new Date(a.thread.createdAt).getTime();
        const bt = b.lastMessageAt ? new Date(b.lastMessageAt).getTime() : new Date(b.thread.createdAt).getTime();
        return bt - at;
      });
  }, [items, query, filter]);

  const noResultsFromFilter = items.length > 0 && filtered.length === 0;

  return (
    <ThemedView style={styles.container}>
      <PlatformStatusSpacer />
      <View style={styles.header}>
        <ThemedText type="title" style={styles.headerTitle}>
          Messages
        </ThemedText>
        {onNewConversation && (
          <Pressable hitSlop={8} onPress={onNewConversation} accessibilityLabel="New conversation" accessibilityRole="button">
            <Ionicons name="add-circle-outline" size={26} color={theme.text} />
          </Pressable>
        )}
      </View>

      <View style={[styles.searchBox, { backgroundColor: theme.backgroundElement }]}>
        <Ionicons name="search" size={16} color={theme.textSecondary} />
        <TextInput
          value={query}
          onChangeText={setQuery}
          placeholder="Search conversations…"
          placeholderTextColor={theme.textSecondary}
          style={[styles.searchInput, { color: theme.text }]}
          accessibilityLabel="Search conversations"
          returnKeyType="search"
        />
      </View>

      <FlatList
        horizontal
        showsHorizontalScrollIndicator={false}
        data={FILTERS}
        keyExtractor={(f) => f.key}
        contentContainerStyle={styles.filterRow}
        renderItem={({ item: f }) => {
          const active = filter === f.key;
          return (
            <Pressable
              onPress={() => setFilter(f.key)}
              accessibilityRole="button"
              accessibilityState={{ selected: active }}
              style={[
                styles.filterChip,
                { backgroundColor: active ? Brand.victoryGreen : theme.backgroundElement },
              ]}
            >
              <ThemedText type="small" style={active ? styles.filterChipTextActive : undefined} themeColor={active ? undefined : 'textSecondary'}>
                {f.label}
              </ThemedText>
            </Pressable>
          );
        }}
      />

      {oversightLink && (
        <Pressable
          onPress={oversightLink.onPress}
          accessibilityRole="button"
          style={[styles.oversightLink, { backgroundColor: theme.backgroundElement }]}
        >
          <Ionicons name="shield-checkmark-outline" size={16} color={theme.textSecondary} />
          <ThemedText type="small" themeColor="textSecondary" style={styles.oversightLinkText}>
            {oversightLink.label}
          </ThemedText>
          <Ionicons name="chevron-forward" size={16} color={theme.textSecondary} />
        </Pressable>
      )}

      {threadsQuery.isLoading && <LoadingState label="Loading messages…" />}
      {threadsQuery.isError && <ErrorState message="Could not load messages." onRetry={() => threadsQuery.refetch()} />}
      {threadsQuery.data && items.length === 0 && (
        <EmptyState title="No conversations yet" description="Team broadcasts and conversations will show up here." />
      )}
      {noResultsFromFilter && <EmptyState title="No matching conversations" description="Try a different search or filter." />}

      <FlatList
        data={filtered}
        keyExtractor={(item) => item.thread.id}
        contentContainerStyle={styles.list}
        renderItem={({ item }) => {
          const hasUnread = item.unreadCount > 0;
          return (
            <Pressable onPress={() => router.push({ pathname: '/messages/[threadId]', params: { threadId: item.thread.id } })}>
              <ThemedView type="backgroundElement" style={styles.row}>
                <ThreadAvatar item={item} />
                <View style={styles.rowBody}>
                  <View style={styles.rowHeader}>
                    <ThemedText type={hasUnread ? 'smallBold' : 'small'} style={styles.title} numberOfLines={1}>
                      {item.thread.title}
                    </ThemedText>
                    {item.lastMessageAt && (
                      <ThemedText type="small" themeColor="textSecondary">
                        {formatRelativeListTime(item.lastMessageAt)}
                      </ThemedText>
                    )}
                  </View>
                  <View style={styles.rowFooter}>
                    <ThemedText
                      type={hasUnread ? 'smallBold' : 'small'}
                      themeColor={hasUnread ? undefined : 'textSecondary'}
                      numberOfLines={1}
                      style={styles.preview}
                    >
                      {item.lastMessagePreview ?? item.thread.scopeName ?? 'No messages yet'}
                    </ThemedText>
                    {hasUnread && (
                      <View style={styles.badge}>
                        <ThemedText type="small" style={styles.badgeText}>
                          {item.unreadCount}
                        </ThemedText>
                      </View>
                    )}
                  </View>
                </View>
              </ThemedView>
            </Pressable>
          );
        }}
      />
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: Spacing.four,
    paddingTop: Spacing.two,
  },
  headerTitle: {
    fontSize: 28,
    lineHeight: 34,
  },
  searchBox: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
    marginHorizontal: Spacing.four,
    marginTop: Spacing.three,
    borderRadius: Spacing.three,
    paddingHorizontal: Spacing.three,
    height: 40,
  },
  searchInput: {
    flex: 1,
    fontSize: 14,
    height: '100%',
  },
  filterRow: {
    gap: Spacing.two,
    paddingHorizontal: Spacing.four,
    paddingVertical: Spacing.three,
  },
  oversightLink: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
    marginHorizontal: Spacing.four,
    marginBottom: Spacing.three,
    padding: Spacing.three,
    borderRadius: Spacing.three,
  },
  oversightLinkText: {
    flex: 1,
  },
  filterChip: {
    paddingHorizontal: Spacing.three,
    height: 32,
    borderRadius: 16,
    alignItems: 'center',
    justifyContent: 'center',
  },
  filterChipTextActive: {
    color: Brand.pureWhite,
    fontWeight: '700',
  },
  list: {
    paddingHorizontal: Spacing.four,
    paddingBottom: Spacing.six,
    gap: Spacing.two,
  },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.three,
    borderRadius: Spacing.three,
    padding: Spacing.three,
  },
  avatar: {
    width: 44,
    height: 44,
    borderRadius: 22,
    alignItems: 'center',
    justifyContent: 'center',
  },
  avatarPerson: {
    backgroundColor: Brand.navy,
  },
  avatarBroadcast: {
    backgroundColor: Brand.championshipGold,
  },
  avatarText: {
    color: Brand.pureWhite,
    fontWeight: '700',
  },
  rowBody: {
    flex: 1,
    gap: 2,
  },
  rowHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: Spacing.two,
  },
  rowFooter: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: Spacing.two,
  },
  title: {
    flex: 1,
  },
  preview: {
    flex: 1,
  },
  badge: {
    minWidth: 20,
    height: 20,
    borderRadius: 10,
    paddingHorizontal: 6,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: Brand.victoryGreen,
  },
  badgeText: {
    color: Brand.pureWhite,
    fontWeight: '700',
  },
});
