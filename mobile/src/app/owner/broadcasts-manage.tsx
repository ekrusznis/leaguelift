import { router } from 'expo-router';
import { Pressable, ScrollView, StyleSheet, View } from 'react-native';
import Ionicons from '@expo/vector-icons/Ionicons';

import { EmptyState } from '@/components/empty-state';
import { ErrorState } from '@/components/error-state';
import { LoadingState } from '@/components/loading-state';
import { ScreenHeader } from '@/components/screen-header';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { useDashboardContext } from '@/features/dashboard/api';
import { useManagedThreads } from '@/features/messaging/api';
import type { MessageThreadType } from '@/features/messaging/types';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

const TYPE_LABEL: Record<MessageThreadType, string> = {
  BROADCAST: 'Broadcast',
  CONVERSATION: 'Conversation',
  ATHLETE_CONVERSATION: 'Athlete conversation',
};

const TYPE_ICON: Record<MessageThreadType, keyof typeof Ionicons.glyphMap> = {
  BROADCAST: 'megaphone',
  CONVERSATION: 'chatbubbles',
  ATHLETE_CONVERSATION: 'people',
};

/**
 * Org-wide message oversight: every thread in the organization, any type — broadcasts,
 * coach<->family conversations, athlete peer conversations — not filtered to broadcasts
 * despite the route name (a Phase 25.1 naming leftover from before conversations
 * existed; `useManagedThreads`/the backend's `listForManagement` never filtered by
 * thread type, this screen just didn't previously make that visible). Founder
 * direction: an Owner must be able to see every message in their org for youth-safety
 * oversight, including conversations they aren't personally a member of — real message
 * content is already reachable via `listMessagesForManagement`, which gates on the
 * org/team manager role, not thread membership.
 */
export default function OwnerBroadcastsManageScreen() {
  const theme = useTheme();
  const dashboardContext = useDashboardContext(true);
  const organizationId = dashboardContext.data?.organizationId ?? null;
  const threadsQuery = useManagedThreads(organizationId);

  return (
    <ThemedView style={styles.container}>
      <ScreenHeader
        title="Organization Messages"
        right={
          <Pressable hitSlop={8} onPress={() => router.push('/owner/broadcast-compose')} accessibilityLabel="New broadcast">
            <Ionicons name="add-circle-outline" size={24} color={theme.text} />
          </Pressable>
        }
      />
      <ThemedText type="small" themeColor="textSecondary" style={styles.subtitle}>
        Every broadcast and conversation in your organization, for oversight.
      </ThemedText>

      {threadsQuery.isLoading && <LoadingState label="Loading messages…" />}
      {threadsQuery.isError && <ErrorState message="Could not load messages." onRetry={() => threadsQuery.refetch()} />}
      {threadsQuery.data && threadsQuery.data.items.length === 0 && (
        <EmptyState title="No messages yet" description="Broadcasts and conversations across your organization will show up here." />
      )}

      <ScrollView contentContainerStyle={styles.list}>
        {threadsQuery.data?.items.map((thread) => (
          <Pressable key={thread.id} onPress={() => router.push({ pathname: '/owner/broadcast-detail', params: { threadId: thread.id } })}>
            <ThemedView type="backgroundElement" style={styles.row}>
              <Ionicons name={TYPE_ICON[thread.threadType]} size={18} color={theme.textSecondary} />
              <View style={styles.rowBody}>
                <ThemedText type="smallBold" numberOfLines={1}>
                  {thread.title}
                </ThemedText>
                <ThemedText type="small" themeColor="textSecondary">
                  {TYPE_LABEL[thread.threadType]} · {thread.scopeName ?? thread.scopeType} · {thread.messageCount} messages
                </ThemedText>
              </View>
              <Ionicons name="chevron-forward" size={18} color={theme.textSecondary} />
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
  subtitle: {
    paddingHorizontal: Spacing.four,
    paddingBottom: Spacing.two,
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
  rowBody: {
    flex: 1,
    gap: 2,
  },
});
