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
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

/** Broadcast thread management: list + link to create/send (ADR-105). */
export default function OwnerBroadcastsManageScreen() {
  const theme = useTheme();
  const dashboardContext = useDashboardContext(true);
  const organizationId = dashboardContext.data?.organizationId ?? null;
  const threadsQuery = useManagedThreads(organizationId);

  return (
    <ThemedView style={styles.container}>
      <ScreenHeader
        title="Broadcasts"
        right={
          <Pressable hitSlop={8} onPress={() => router.push('/owner/broadcast-compose')}>
            <Ionicons name="add-circle-outline" size={24} color={theme.text} />
          </Pressable>
        }
      />

      {threadsQuery.isLoading && <LoadingState label="Loading broadcasts…" />}
      {threadsQuery.isError && <ErrorState message="Could not load broadcasts." onRetry={() => threadsQuery.refetch()} />}
      {threadsQuery.data && threadsQuery.data.items.length === 0 && (
        <EmptyState title="No broadcasts yet" description="Create one to message your organization." />
      )}

      <ScrollView contentContainerStyle={styles.list}>
        {threadsQuery.data?.items.map((thread) => (
          <Pressable key={thread.id} onPress={() => router.push({ pathname: '/owner/broadcast-detail', params: { threadId: thread.id } })}>
            <ThemedView type="backgroundElement" style={styles.row}>
              <View style={styles.rowBody}>
                <ThemedText type="smallBold" numberOfLines={1}>
                  {thread.title}
                </ThemedText>
                <ThemedText type="small" themeColor="textSecondary">
                  {thread.scopeName ?? thread.scopeType} · {thread.messageCount} messages · {thread.recipientCount} recipients
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
