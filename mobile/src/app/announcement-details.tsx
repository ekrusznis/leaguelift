import { useLocalSearchParams } from 'expo-router';
import { ScrollView, StyleSheet } from 'react-native';

import { ErrorState } from '@/components/error-state';
import { LoadingState } from '@/components/loading-state';
import { ScreenHeader } from '@/components/screen-header';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { useMyAnnouncements } from '@/features/announcements/api';
import { Spacing } from '@/constants/theme';

export default function AnnouncementDetailsScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const announcementsQuery = useMyAnnouncements();
  const item = announcementsQuery.data?.items.find((entry) => entry.announcement.id === id);

  if (announcementsQuery.isLoading) {
    return (
      <ThemedView style={styles.container}>
        <ScreenHeader title="Announcement" />
        <LoadingState label="Loading…" />
      </ThemedView>
    );
  }

  if (!item) {
    return (
      <ThemedView style={styles.container}>
        <ScreenHeader title="Announcement" />
        <ErrorState message="Could not find this announcement." />
      </ThemedView>
    );
  }

  return (
    <ThemedView style={styles.container}>
      <ScreenHeader title={item.announcement.title} />
      <ScrollView contentContainerStyle={styles.content}>
        <ThemedText type="title" style={styles.title}>
          {item.announcement.title}
        </ThemedText>
        {item.announcement.scopeName && (
          <ThemedText type="small" themeColor="textSecondary">
            {item.announcement.scopeName}
            {item.announcement.publishedAt
              ? ` · ${new Date(item.announcement.publishedAt).toLocaleDateString('en-US', { month: 'long', day: 'numeric' })}`
              : ''}
          </ThemedText>
        )}
        <ThemedText style={styles.body}>{item.announcement.body}</ThemedText>
      </ScrollView>
    </ThemedView>
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
  title: {
    marginTop: Spacing.two,
  },
  body: {
    marginTop: Spacing.three,
    lineHeight: 22,
  },
});
