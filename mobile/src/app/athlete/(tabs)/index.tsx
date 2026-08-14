import Ionicons from '@expo/vector-icons/Ionicons';
import { router } from 'expo-router';
import { useMemo, useState } from 'react';
import { Pressable, ScrollView, StyleSheet, View } from 'react-native';

import { ErrorState } from '@/components/error-state';
import { EventCard } from '@/components/event-card';
import { LoadingState } from '@/components/loading-state';
import { PlatformStatusSpacer } from '@/components/platform-status-spacer';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { useMyAnnouncements } from '@/features/announcements/api';
import { useAthleteOverview, useAthleteTeams } from '@/features/athlete/api';
import { useAthleteSelf } from '@/features/athlete/AthleteSelfContext';
import { useParticipantEvents } from '@/features/events/api';
import { Brand, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

export default function AthleteDashboardScreen() {
  const theme = useTheme();
  const athleteSelf = useAthleteSelf();
  const [now] = useState(() => Date.now());
  const overviewQuery = useAthleteOverview(true);
  const teamsQuery = useAthleteTeams(true);
  const announcementsQuery = useMyAnnouncements();
  const eventsQuery = useParticipantEvents(athleteSelf.organizationId, athleteSelf.participantId);

  const upcomingEvents = useMemo(() => {
    if (!eventsQuery.data) return [];
    return eventsQuery.data
      .filter((event) => !event.startAt || new Date(event.startAt).getTime() >= now)
      .sort((a, b) => (a.startAt ?? '').localeCompare(b.startAt ?? ''))
      .slice(0, 2);
  }, [eventsQuery.data, now]);

  const recentAnnouncements = announcementsQuery.data?.items.slice(0, 1) ?? [];

  if (overviewQuery.isLoading) {
    return (
      <ThemedView style={styles.container}>
        <PlatformStatusSpacer />
        <LoadingState label="Loading your dashboard…" />
      </ThemedView>
    );
  }

  if (overviewQuery.isError) {
    return (
      <ThemedView style={styles.container}>
        <PlatformStatusSpacer />
        <ErrorState message="Could not load your dashboard." onRetry={() => overviewQuery.refetch()} />
      </ThemedView>
    );
  }

  const overview = overviewQuery.data;
  return (
    <ThemedView style={styles.container}>
      <PlatformStatusSpacer />
      <View style={styles.topBar}>
        <ThemedText type="title" style={styles.wordmark}>
          RALLY<ThemedText type="title" style={[styles.wordmark, styles.wordmarkAccent]}>26</ThemedText>
        </ThemedText>
        <Pressable hitSlop={8} onPress={() => router.push('/settings')}>
          <Ionicons name="person-circle" size={24} color={theme.text} />
        </Pressable>
      </View>

      <ScrollView contentContainerStyle={styles.scrollContent}>
        <ThemedText type="title" style={styles.greeting}>
          Hey, {overview?.displayName.split(' ')[0] ?? 'there'}
        </ThemedText>

        {overview?.nextEvent ? (
          <ThemedView type="backgroundElement" style={styles.nextEventCard}>
            <ThemedText type="small" themeColor="textSecondary">Next Up</ThemedText>
            <ThemedText type="smallBold">{overview.nextEvent.title}</ThemedText>
            <ThemedText type="small" themeColor="textSecondary">
              {overview.nextEvent.dateLabel}
              {overview.nextEvent.subtitle ? ` · ${overview.nextEvent.subtitle}` : ''}
            </ThemedText>
            {overview.nextEvent.location && (
              <ThemedText type="small" themeColor="textSecondary">{overview.nextEvent.location}</ThemedText>
            )}
          </ThemedView>
        ) : (
          overview && <ThemedText type="small" themeColor="textSecondary" style={styles.emptyNextEvent}>Nothing coming up yet.</ThemedText>
        )}

        <View style={styles.sectionHeader}>
          <ThemedText type="smallBold">Your Teams</ThemedText>
          {athleteSelf.organizationId && athleteSelf.participantId && (
            <Pressable
              accessibilityRole="button"
              onPress={() =>
                router.push({
                  pathname: '/participant-teams',
                  params: {
                    organizationId: athleteSelf.organizationId ?? '',
                    participantId: athleteSelf.participantId ?? '',
                    participantName: overview?.displayName ?? '',
                  },
                })
              }>
              <ThemedText type="link" themeColor="textSecondary">Coaches & Staff</ThemedText>
            </Pressable>
          )}
        </View>
        {teamsQuery.isLoading && <LoadingState label="Loading teams…" />}
        {teamsQuery.data && teamsQuery.data.length === 0 && (
          <ThemedText type="small" themeColor="textSecondary">No teams linked yet.</ThemedText>
        )}
        <View style={styles.teamsList}>
          {teamsQuery.data?.map((team) => (
            <ThemedView key={team.name} type="backgroundElement" style={styles.teamCard}>
              <ThemedText type="smallBold">{team.name}</ThemedText>
              <ThemedText type="small" themeColor="textSecondary">{team.detail}</ThemedText>
              <ThemedText type="small" themeColor="textSecondary">Coach: {team.coachName}</ThemedText>
            </ThemedView>
          ))}
        </View>

        <View style={styles.sectionHeader}>
          <ThemedText type="smallBold">Upcoming</ThemedText>
          <Pressable onPress={() => router.push('/athlete/(tabs)/calendar')}>
            <ThemedText type="link" themeColor="textSecondary">View All</ThemedText>
          </Pressable>
        </View>
        {eventsQuery.isLoading && <LoadingState label="Loading schedule…" />}
        {eventsQuery.isError && <ErrorState message="Could not load your schedule." onRetry={() => eventsQuery.refetch()} />}
        {eventsQuery.data && upcomingEvents.length === 0 && (
          <ThemedText type="small" themeColor="textSecondary">Nothing scheduled yet.</ThemedText>
        )}
        <View style={styles.list}>
          {upcomingEvents.map((event) => <EventCard key={event.id} event={event} />)}
        </View>

        <View style={styles.sectionHeader}>
          <ThemedText type="smallBold">Recent Announcements</ThemedText>
          <Pressable onPress={() => router.push('/announcements')}>
            <ThemedText type="link" themeColor="textSecondary">View All</ThemedText>
          </Pressable>
        </View>
        {announcementsQuery.isLoading && <LoadingState label="Loading announcements…" />}
        {announcementsQuery.data && recentAnnouncements.length === 0 && (
          <ThemedText type="small" themeColor="textSecondary">No announcements yet.</ThemedText>
        )}
        <View style={styles.list}>
          {recentAnnouncements.map(({ announcement }) => (
            <ThemedView key={announcement.id} type="backgroundElement" style={styles.announcementCard}>
              <View style={styles.announcementHeader}>
                <Ionicons name="megaphone" size={18} color={Brand.championshipGold} />
                <ThemedText type="smallBold">{announcement.title}</ThemedText>
              </View>
              <ThemedText type="small" themeColor="textSecondary" numberOfLines={2}>{announcement.body}</ThemedText>
            </ThemedView>
          ))}
        </View>
      </ScrollView>
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  topBar: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: Spacing.four,
    paddingVertical: Spacing.two,
  },
  wordmark: { fontSize: 20, lineHeight: 24 },
  wordmarkAccent: { color: Brand.championshipGold },
  scrollContent: {
    paddingHorizontal: Spacing.four,
    paddingBottom: Spacing.six,
    gap: Spacing.two,
  },
  greeting: {
    marginTop: Spacing.two,
    marginBottom: Spacing.two,
  },
  nextEventCard: {
    borderRadius: Spacing.three,
    padding: Spacing.three,
    gap: 2,
    marginBottom: Spacing.two,
  },
  emptyNextEvent: { marginBottom: Spacing.two },
  sectionHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginTop: Spacing.three,
    marginBottom: Spacing.two,
  },
  teamsList: {
    gap: Spacing.two,
    marginBottom: Spacing.two,
  },
  teamCard: {
    borderRadius: Spacing.three,
    padding: Spacing.three,
    gap: 2,
  },
  list: { gap: Spacing.two },
  announcementCard: {
    borderRadius: Spacing.three,
    padding: Spacing.three,
    gap: Spacing.one,
  },
  announcementHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
  },
});
