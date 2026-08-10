import { router } from 'expo-router';
import { Pressable, ScrollView, StyleSheet, View } from 'react-native';
import Ionicons from '@expo/vector-icons/Ionicons';

import { EmptyState } from '@/components/empty-state';
import { ErrorState } from '@/components/error-state';
import { LoadingState } from '@/components/loading-state';
import { ScreenHeader } from '@/components/screen-header';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { useToast } from '@/components/toast';
import { useManagedAnnouncements, usePublishAnnouncement } from '@/features/announcements/api';
import { useDashboardContext } from '@/features/dashboard/api';
import { Brand, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

/** Announcement management: list drafts/published/archived, publish a draft (ADR-105). Compose is a separate pushed screen. */
export default function OwnerAnnouncementsManageScreen() {
  const theme = useTheme();
  const toast = useToast();
  const dashboardContext = useDashboardContext(true);
  const organizationId = dashboardContext.data?.organizationId ?? null;
  const announcementsQuery = useManagedAnnouncements(organizationId);
  const publish = usePublishAnnouncement(organizationId);

  return (
    <ThemedView style={styles.container}>
      <ScreenHeader
        title="Announcements"
        right={
          <Pressable hitSlop={8} onPress={() => router.push('/owner/announcement-compose')}>
            <Ionicons name="add-circle-outline" size={24} color={theme.text} />
          </Pressable>
        }
      />

      {announcementsQuery.isLoading && <LoadingState label="Loading announcements…" />}
      {announcementsQuery.isError && (
        <ErrorState message="Could not load announcements." onRetry={() => announcementsQuery.refetch()} />
      )}
      {announcementsQuery.data && announcementsQuery.data.items.length === 0 && (
        <EmptyState title="No announcements yet" description="Compose one to reach your organization." />
      )}

      <ScrollView contentContainerStyle={styles.list}>
        {announcementsQuery.data?.items.map((announcement) => (
          <ThemedView key={announcement.id} type="backgroundElement" style={styles.row}>
            <View style={styles.rowHeader}>
              <ThemedText type="smallBold" style={styles.title} numberOfLines={1}>
                {announcement.title}
              </ThemedText>
              <View style={[styles.statusBadge, announcement.status === 'DRAFT' && styles.draftBadge]}>
                <ThemedText type="small" style={styles.statusText}>
                  {announcement.status}
                </ThemedText>
              </View>
            </View>
            <ThemedText type="small" themeColor="textSecondary" numberOfLines={2}>
              {announcement.body}
            </ThemedText>
            <ThemedText type="small" themeColor="textSecondary">
              {announcement.scopeName ?? announcement.scopeType} · {announcement.recipientCount} recipients
            </ThemedText>
            {announcement.status === 'DRAFT' && (
              <Pressable
                onPress={() =>
                  publish.mutate(announcement.id, {
                    onSuccess: () => toast.show('Announcement published.', 'success'),
                    onError: () => toast.show('Could not publish that announcement.', 'error'),
                  })
                }
                disabled={publish.isPending}
                style={styles.publishRow}>
                <Ionicons name="send" size={16} color={Brand.championshipGold} />
                <ThemedText type="link">Publish</ThemedText>
              </Pressable>
            )}
          </ThemedView>
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
  statusBadge: {
    borderRadius: Spacing.one,
    paddingHorizontal: Spacing.two,
    paddingVertical: 2,
    backgroundColor: Brand.slateGray,
  },
  draftBadge: {
    backgroundColor: Brand.championshipGold,
  },
  statusText: {
    color: '#0B1F33',
    fontWeight: '700',
  },
  publishRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.one,
    marginTop: Spacing.one,
  },
});
