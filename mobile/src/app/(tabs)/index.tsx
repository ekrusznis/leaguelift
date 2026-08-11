import Ionicons from '@expo/vector-icons/Ionicons';
import { router } from 'expo-router';
import { useMemo, useState } from 'react';
import { Pressable, ScrollView, StyleSheet, View } from 'react-native';

import { EventCard } from '@/components/event-card';
import { EmptyState } from '@/components/empty-state';
import { ErrorState } from '@/components/error-state';
import { LoadingState } from '@/components/loading-state';
import { Modal } from '@/components/modal';
import { PlatformStatusSpacer } from '@/components/platform-status-spacer';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { useMyAnnouncements } from '@/features/announcements/api';
import { useTeamEvents } from '@/features/events/api';
import { useCoach } from '@/features/teams/CoachContext';
import { Brand, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

/** Coach Dashboard (Home tab) — real backend data (ADR-102). */
export default function DashboardScreen() {
  const theme = useTheme();
  const coach = useCoach();
  const [teamPickerOpen, setTeamPickerOpen] = useState(false);

  const selectedTeam = coach.teams.find((t) => t.teamId === coach.selectedTeamId) ?? null;
  const eventsQuery = useTeamEvents(coach.organizationId, coach.selectedTeamId);
  const announcementsQuery = useMyAnnouncements();
  // Captured once per mount, not read fresh on every render — Date.now() is impure
  // and React's purity rules flag calling it directly during render/useMemo.
  const [now] = useState(() => Date.now());

  const upcomingEvents = useMemo(() => {
    if (!eventsQuery.data) return [];
    return eventsQuery.data
      .filter((event) => !event.startAt || new Date(event.startAt).getTime() >= now)
      .sort((a, b) => (a.startAt ?? '').localeCompare(b.startAt ?? ''))
      .slice(0, 2);
  }, [eventsQuery.data, now]);

  const recentAnnouncements = announcementsQuery.data?.items.slice(0, 1) ?? [];

  if (coach.isLoading) {
    return (
      <ThemedView style={styles.container}>
        <PlatformStatusSpacer />
        <LoadingState label="Loading your teams…" />
      </ThemedView>
    );
  }

  if (coach.isError) {
    return (
      <ThemedView style={styles.container}>
        <PlatformStatusSpacer />
        <ErrorState message="Could not load your dashboard." />
      </ThemedView>
    );
  }

  if (coach.teams.length === 0) {
    return (
      <ThemedView style={styles.container}>
        <PlatformStatusSpacer />
        <EmptyState title="No teams yet" description="You aren't assigned to a team as a coach in this organization." />
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
        <View style={styles.topBarActions}>
          <Pressable hitSlop={8} onPress={() => router.push('/settings')}>
            <Ionicons name="person-circle" size={24} color={theme.text} />
          </Pressable>
        </View>
      </View>

      <ScrollView contentContainerStyle={styles.scrollContent}>
        <Pressable
          style={styles.teamRow}
          onPress={() => coach.teams.length > 1 && setTeamPickerOpen(true)}
          disabled={coach.teams.length <= 1}>
          <ThemedView type="backgroundSelected" style={styles.teamBadge}>
            <Ionicons name="shield" size={24} color={Brand.championshipGold} />
          </ThemedView>
          <View style={styles.teamRowText}>
            <ThemedText type="subtitle">{selectedTeam?.name ?? '—'}</ThemedText>
            <ThemedText type="small" themeColor="textSecondary">
              {selectedTeam?.sport} · {selectedTeam?.participants} participants
            </ThemedText>
          </View>
          {coach.teams.length > 1 && <Ionicons name="chevron-down" size={18} color={theme.textSecondary} />}
        </Pressable>

        <View style={styles.sectionHeader}>
          <ThemedText type="smallBold">Upcoming</ThemedText>
          <Pressable onPress={() => router.push('/(tabs)/calendar')}>
            <ThemedText type="link" themeColor="textSecondary">
              View All
            </ThemedText>
          </Pressable>
        </View>
        {eventsQuery.isLoading && <LoadingState label="Loading schedule…" />}
        {eventsQuery.isError && <ErrorState message="Could not load the team schedule." onRetry={() => eventsQuery.refetch()} />}
        {eventsQuery.data && upcomingEvents.length === 0 && (
          <ThemedText type="small" themeColor="textSecondary">
            Nothing scheduled yet.
          </ThemedText>
        )}
        <View style={styles.list}>
          {upcomingEvents.map((event) => (
            <EventCard key={event.id} event={event} />
          ))}
        </View>

        <View style={styles.sectionHeader}>
          <ThemedText type="smallBold">Recent Announcements</ThemedText>
          <Pressable onPress={() => router.push('/announcements')}>
            <ThemedText type="link" themeColor="textSecondary">
              View All
            </ThemedText>
          </Pressable>
        </View>
        {announcementsQuery.isLoading && <LoadingState label="Loading announcements…" />}
        {announcementsQuery.isError && (
          <ErrorState message="Could not load announcements." onRetry={() => announcementsQuery.refetch()} />
        )}
        {announcementsQuery.data && recentAnnouncements.length === 0 && (
          <ThemedText type="small" themeColor="textSecondary">
            No announcements yet.
          </ThemedText>
        )}
        <View style={styles.list}>
          {recentAnnouncements.map(({ announcement }) => (
            <ThemedView key={announcement.id} type="backgroundElement" style={styles.announcementCard}>
              <View style={styles.announcementHeader}>
                <Ionicons name="megaphone" size={18} color={Brand.championshipGold} />
                <ThemedText type="smallBold">{announcement.title}</ThemedText>
              </View>
              <ThemedText type="small" themeColor="textSecondary" numberOfLines={2}>
                {announcement.body}
              </ThemedText>
            </ThemedView>
          ))}
        </View>
      </ScrollView>

      <Modal visible={teamPickerOpen} onClose={() => setTeamPickerOpen(false)}>
        <ThemedText type="smallBold" style={styles.pickerTitle}>
          Switch team
        </ThemedText>
        {coach.teams.map((team) => (
          <Pressable
            key={team.teamId}
            onPress={() => {
              coach.setSelectedTeamId(team.teamId);
              setTeamPickerOpen(false);
            }}
            style={styles.pickerRow}>
            <ThemedText type={team.teamId === coach.selectedTeamId ? 'smallBold' : 'default'}>{team.name}</ThemedText>
            {team.teamId === coach.selectedTeamId && <Ionicons name="checkmark" size={18} color={Brand.championshipGold} />}
          </Pressable>
        ))}
      </Modal>
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
  topBarActions: {
    flexDirection: 'row',
    gap: Spacing.three,
  },
  scrollContent: {
    paddingHorizontal: Spacing.four,
    paddingBottom: Spacing.six,
    gap: Spacing.two,
  },
  teamRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.three,
    marginTop: Spacing.two,
    marginBottom: Spacing.three,
  },
  teamRowText: {
    flex: 1,
  },
  teamBadge: {
    width: 48,
    height: 48,
    borderRadius: Spacing.two,
    alignItems: 'center',
    justifyContent: 'center',
  },
  sectionHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginTop: Spacing.three,
    marginBottom: Spacing.two,
  },
  list: {
    gap: Spacing.two,
  },
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
  pickerTitle: {
    marginBottom: Spacing.three,
  },
  pickerRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: Spacing.three,
  },
});
