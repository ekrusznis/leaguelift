import Ionicons from '@expo/vector-icons/Ionicons';
import { router } from 'expo-router';
import { useMemo, useState } from 'react';
import { Pressable, ScrollView, StyleSheet, View } from 'react-native';

import { EmptyState } from '@/components/empty-state';
import { ErrorState } from '@/components/error-state';
import { EventCard } from '@/components/event-card';
import { LoadingState } from '@/components/loading-state';
import { PlatformStatusSpacer } from '@/components/platform-status-spacer';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { useMyAnnouncements } from '@/features/announcements/api';
import { useOutstandingBalance } from '@/features/fees/api';
import { useHouseholdEvents } from '@/features/events/api';
import { useHouseholdCtx } from '@/features/household/HouseholdContext';
import { Brand, Spacing } from '@/constants/theme';
import { formatMoneyMinorUnits } from '@/lib/money';
import { useTheme } from '@/hooks/use-theme';

export default function ParentDashboardScreen() {
  const theme = useTheme();
  const household = useHouseholdCtx();
  const [now] = useState(() => Date.now());

  const eventsQuery = useHouseholdEvents(household.organizationId, household.householdId);
  const announcementsQuery = useMyAnnouncements();
  const balanceQuery = useOutstandingBalance(household.organizationId, household.householdId);
  const upcomingEvents = useMemo(() => {
    if (!eventsQuery.data) return [];
    return eventsQuery.data
      .filter((event) => !event.startAt || new Date(event.startAt).getTime() >= now)
      .sort((a, b) => (a.startAt ?? '').localeCompare(b.startAt ?? ''))
      .slice(0, 2);
  }, [eventsQuery.data, now]);

  const recentAnnouncements = announcementsQuery.data?.items.slice(0, 1) ?? [];

  if (household.isLoading) {
    return (
      <ThemedView style={styles.container}>
        <PlatformStatusSpacer />
        <LoadingState label="Loading your household…" />
      </ThemedView>
    );
  }

  if (household.isError) {
    return (
      <ThemedView style={styles.container}>
        <PlatformStatusSpacer />
        <ErrorState message="Could not load your household." />
      </ThemedView>
    );
  }

  if (household.athletes.length === 0) {
    return (
      <ThemedView style={styles.container}>
        <PlatformStatusSpacer />
        <EmptyState title="No linked athletes" description="No participants are linked to your household in this organization yet." />
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
        <Pressable hitSlop={8} onPress={() => router.push('/settings')}>
          <Ionicons name="person-circle" size={24} color={theme.text} />
        </Pressable>
      </View>
      <ScrollView contentContainerStyle={styles.scrollContent}>
        <ThemedText type="smallBold" style={styles.sectionTitle}>Your Athletes</ThemedText>
        <View style={styles.athleteRow}>
          {household.athletes.map((athlete) => (
            <ThemedView key={athlete.participantId} type="backgroundElement" style={styles.athleteCard}>
              <Pressable
                accessibilityRole="button"
                accessibilityLabel={`View eligibility for ${athlete.name}`}
                onPress={() =>
                  router.push({
                    pathname: '/eligibility',
                    params: {
                      participantId: athlete.participantId,
                      participantName: athlete.name,
                      householdId: household.householdId ?? '',
                    },
                  })
                }>
                <ThemedView type="backgroundSelected" style={styles.athleteAvatar}>
                  <ThemedText type="smallBold">
                    {athlete.name
                      .split(' ')
                      .map((part) => part[0])
                      .join('')
                      .slice(0, 2)
                      .toUpperCase()}
                  </ThemedText>
                </ThemedView>
                <ThemedText type="smallBold">{athlete.name}</ThemedText>
                <ThemedText type="small" themeColor="textSecondary" numberOfLines={1}>
                  {athlete.teamNames.join(', ') || 'No team yet'}
                </ThemedText>
              </Pressable>
              <Pressable
                accessibilityRole="button"
                accessibilityLabel={`View coaches and staff for ${athlete.name}`}
                onPress={() =>
                  router.push({
                    pathname: '/participant-teams',
                    params: {
                      organizationId: household.organizationId ?? '',
                      participantId: athlete.participantId,
                      participantName: athlete.name,
                    },
                  })
                }
                style={styles.coachesAction}>
                <Ionicons name="people-outline" size={16} color={theme.textSecondary} />
                <ThemedText type="small" themeColor="textSecondary">Coaches</ThemedText>
              </Pressable>
            </ThemedView>
          ))}
        </View>

        <View style={styles.sectionHeader}>
          <ThemedText type="smallBold">Upcoming</ThemedText>
          <Pressable onPress={() => router.push('/parent/(tabs)/calendar')}>
            <ThemedText type="link" themeColor="textSecondary">View All</ThemedText>
          </Pressable>
        </View>
        {eventsQuery.isLoading && <LoadingState label="Loading schedule…" />}
        {eventsQuery.isError && <ErrorState message="Could not load your family's schedule." onRetry={() => eventsQuery.refetch()} />}
        {eventsQuery.data && upcomingEvents.length === 0 && (
          <ThemedText type="small" themeColor="textSecondary">Nothing scheduled yet.</ThemedText>
        )}
        <View style={styles.list}>
          {upcomingEvents.map((event) => <EventCard key={event.id} event={event} />)}
        </View>

        <View style={styles.sectionHeader}>
          <ThemedText type="smallBold">Balance</ThemedText>
          <Pressable onPress={() => router.push('/parent/(tabs)/payments')}>
            <ThemedText type="link" themeColor="textSecondary">View All</ThemedText>
          </Pressable>
        </View>
        {balanceQuery.isLoading && <LoadingState label="Loading balance…" />}
        {balanceQuery.isError && <ErrorState message="Could not load your balance." onRetry={() => balanceQuery.refetch()} />}
        {balanceQuery.data && (
          <ThemedView type="backgroundElement" style={styles.balanceCard}>
            <ThemedText type="small" themeColor="textSecondary">Total outstanding</ThemedText>
            <ThemedText type="title" style={styles.balanceAmount}>
              {formatMoneyMinorUnits(balanceQuery.data.totalOutstandingMinor, balanceQuery.data.currency)}
            </ThemedText>
          </ThemedView>
        )}

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
              <ThemedText type="small" themeColor="textSecondary" numberOfLines={2}>
                {announcement.body}
              </ThemedText>
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
  sectionTitle: {
    marginTop: Spacing.two,
    marginBottom: Spacing.two,
  },
  athleteRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: Spacing.two,
    marginBottom: Spacing.two,
  },
  athleteCard: {
    width: 152,
    borderRadius: Spacing.three,
    padding: Spacing.three,
    gap: Spacing.two,
  },
  athleteAvatar: {
    width: 36,
    height: 36,
    borderRadius: 18,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 2,
  },
  coachesAction: {
    minHeight: 34,
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.one,
    marginTop: Spacing.one,
  },
  sectionHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginTop: Spacing.three,
    marginBottom: Spacing.two,
  },
  list: { gap: Spacing.two },
  balanceCard: {
    borderRadius: Spacing.three,
    padding: Spacing.three,
    gap: 2,
  },
  balanceAmount: { fontSize: 28 },
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
