import Ionicons from '@expo/vector-icons/Ionicons';
import { router } from 'expo-router';
import { FlatList, Pressable, StyleSheet, View } from 'react-native';

import { EmptyState } from '@/components/empty-state';
import { ErrorState } from '@/components/error-state';
import { LoadingState } from '@/components/loading-state';
import { PlatformStatusSpacer } from '@/components/platform-status-spacer';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { Brand, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

import { useMyMessageThreads } from './api';

/**
 * Shared "Messages" tab across every persona — /me/message-threads is caller-scoped,
 * not coach-specific (ADR-103). `onNewConversation` is optional and only passed by the
 * Athlete persona (ADR-104), whose peer-to-peer conversation creation has no equivalent
 * for Coach (broadcasts) or Parent (no self-initiated conversations at all).
 */
export function MessagesListScreen({ onNewConversation }: { onNewConversation?: () => void } = {}) {
  const threadsQuery = useMyMessageThreads();
  const theme = useTheme();

  return (
    <ThemedView style={styles.container}>
      <PlatformStatusSpacer />
      <View style={styles.header}>
        <ThemedText type="smallBold">Messages</ThemedText>
        {onNewConversation && (
          <Pressable hitSlop={8} onPress={onNewConversation}>
            <Ionicons name="add-circle-outline" size={24} color={theme.text} />
          </Pressable>
        )}
      </View>

      {threadsQuery.isLoading && <LoadingState label="Loading messages…" />}
      {threadsQuery.isError && <ErrorState message="Could not load messages." onRetry={() => threadsQuery.refetch()} />}
      {threadsQuery.data && threadsQuery.data.items.length === 0 && (
        <EmptyState title="No conversations yet" description="Team broadcasts and conversations will show up here." />
      )}

      <FlatList
        data={threadsQuery.data?.items ?? []}
        keyExtractor={(item) => item.thread.id}
        contentContainerStyle={styles.list}
        renderItem={({ item }) => (
          <Pressable onPress={() => router.push({ pathname: '/messages/[threadId]', params: { threadId: item.thread.id } })}>
            <ThemedView type="backgroundElement" style={styles.row}>
              <View style={styles.rowHeader}>
                <ThemedText type="smallBold" style={styles.title} numberOfLines={1}>
                  {item.thread.title}
                </ThemedText>
                {item.unreadCount > 0 && (
                  <View style={styles.badge}>
                    <ThemedText type="small" style={styles.badgeText}>
                      {item.unreadCount}
                    </ThemedText>
                  </View>
                )}
              </View>
              {item.thread.scopeName && (
                <ThemedText type="small" themeColor="textSecondary">
                  {item.thread.scopeName}
                </ThemedText>
              )}
              {item.lastMessagePreview && (
                <ThemedText type="small" themeColor="textSecondary" numberOfLines={1}>
                  {item.lastMessagePreview}
                </ThemedText>
              )}
            </ThemedView>
          </Pressable>
        )}
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
    paddingVertical: Spacing.two,
  },
  list: {
    paddingHorizontal: Spacing.four,
    paddingBottom: Spacing.six,
    gap: Spacing.two,
  },
  row: {
    borderRadius: Spacing.three,
    padding: Spacing.three,
    gap: 2,
  },
  rowHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
  },
  title: {
    flex: 1,
  },
  badge: {
    minWidth: 20,
    height: 20,
    borderRadius: 10,
    paddingHorizontal: 6,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: Brand.championshipGold,
  },
  badgeText: {
    color: '#0B1F33',
    fontWeight: '700',
  },
});
