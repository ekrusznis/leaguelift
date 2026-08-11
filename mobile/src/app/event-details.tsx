import Ionicons from '@expo/vector-icons/Ionicons';
import { router, useLocalSearchParams } from 'expo-router';
import { Linking, ScrollView, Share, StyleSheet, View } from 'react-native';

import { Button } from '@/components/button';
import { ErrorState } from '@/components/error-state';
import { LoadingState } from '@/components/loading-state';
import { ScreenHeader } from '@/components/screen-header';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { useToast } from '@/components/toast';
import { useContexts, useDashboardContext } from '@/features/dashboard/api';
import { useEvent, useEventDirections, useEventRsvps, useSubmitRsvp } from '@/features/events/api';
import type { EventType, SubmittableRsvpStatus } from '@/features/events/types';
import { useMyAthletes } from '@/features/household/api';
import { Brand, Spacing } from '@/constants/theme';
import { formatEventDateHeader, formatEventTimeRange } from '@/lib/eventFormat';

const EVENT_ICON: Record<EventType, keyof typeof Ionicons.glyphMap> = {
  PRACTICE: 'calendar',
  COMPETITION: 'trophy',
  TOURNAMENT: 'trophy',
  MEETING: 'people',
  OTHER: 'ellipse',
};

const RSVP_OPTIONS: { value: SubmittableRsvpStatus; label: string }[] = [
  { value: 'ATTENDING', label: 'Attending' },
  { value: 'MAYBE', label: 'Maybe' },
  { value: 'NOT_ATTENDING', label: "Can't Go" },
];

/**
 * Pushed from EventCard taps, shared by every persona (ADR-103) — organizationId
 * comes straight from dashboard-context rather than a persona-specific context like
 * CoachContext, since a parent has no team-selection concept.
 */
export default function EventDetailsScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const dashboardContext = useDashboardContext(true);
  const organizationId = dashboardContext.data?.organizationId ?? null;
  const householdId = dashboardContext.data?.householdId ?? null;
  const toast = useToast();
  const eventQuery = useEvent(organizationId, id ?? null);
  const rsvpsQuery = useEventRsvps(organizationId, id ?? null);
  const directionsQuery = useEventDirections(organizationId, id ?? null);
  const athletesQuery = useMyAthletes(organizationId, householdId);
  const contextsQuery = useContexts(true);
  const submitRsvp = useSubmitRsvp(organizationId, id ?? '');
  const canEdit = dashboardContext.data?.role === 'COACH' || dashboardContext.data?.role === 'OWNER';

  async function onShare() {
    if (!eventQuery.data) return;
    try {
      await Share.share({
        message: `${eventQuery.data.displayTitle}\n${formatEventDateHeader(eventQuery.data)} · ${formatEventTimeRange(eventQuery.data)}${
          eventQuery.data.venueName ? `\n${eventQuery.data.venueName}` : ''
        }`,
      });
    } catch {
      toast.show('Could not open the share sheet.', 'error');
    }
  }

  function onRsvp(participantId: string, response: SubmittableRsvpStatus) {
    submitRsvp.mutate(
      { participantId, response },
      {
        onSuccess: () => toast.show('RSVP saved.', 'success'),
        onError: () => toast.show('Could not save that RSVP. Please try again.', 'error'),
      },
    );
  }

  if (eventQuery.isLoading) {
    return (
      <ThemedView style={styles.container}>
        <ScreenHeader title="Event" />
        <LoadingState label="Loading event…" />
      </ThemedView>
    );
  }

  if (eventQuery.isError || !eventQuery.data) {
    return (
      <ThemedView style={styles.container}>
        <ScreenHeader title="Event" />
        <ErrorState message="Could not load this event." onRetry={() => eventQuery.refetch()} />
      </ThemedView>
    );
  }

  const event = eventQuery.data;
  const summary = rsvpsQuery.data?.summary;
  const selfAthleteContext = contextsQuery.data?.find((c) => c.contextType === 'ATHLETE') ?? null;
  const athletes: { participantId: string; name: string }[] = [
    ...(householdId ? (athletesQuery.data ?? []) : []),
    ...(selfAthleteContext?.resourceId ? [{ participantId: selfAthleteContext.resourceId, name: 'You' }] : []),
  ];

  return (
    <ThemedView style={styles.container}>
      <ScreenHeader title={event.displayTitle} />
      <ScrollView contentContainerStyle={styles.content}>
        <ThemedView type="backgroundElement" style={styles.iconHero}>
          <Ionicons name={EVENT_ICON[event.eventType]} size={48} color={Brand.championshipGold} />
        </ThemedView>

        <ThemedText type="title" style={styles.title}>
          {event.displayTitle}
        </ThemedText>

        <View style={styles.detailRow}>
          <Ionicons name="calendar-outline" size={18} color={Brand.slateGray} />
          <ThemedText themeColor="textSecondary">
            {formatEventDateHeader(event)} · {formatEventTimeRange(event)}
          </ThemedText>
        </View>
        {event.venueName && (
          <View style={styles.detailRow}>
            <Ionicons name="location-outline" size={18} color={Brand.slateGray} />
            <View>
              <ThemedText themeColor="textSecondary">{event.venueName}</ThemedText>
              {event.address && (
                <ThemedText type="small" themeColor="textSecondary">
                  {event.address}
                </ThemedText>
              )}
            </View>
          </View>
        )}
        {event.description && (
          <View style={styles.detailRow}>
            <Ionicons name="information-circle-outline" size={18} color={Brand.slateGray} />
            <ThemedText themeColor="textSecondary" style={styles.description}>
              {event.description}
            </ThemedText>
          </View>
        )}

        {athletes.length > 0 && (
          <>
            <ThemedText type="smallBold" style={styles.goingHeading}>
              Your RSVP
            </ThemedText>
            {athletes.map((athlete) => {
              const existing = rsvpsQuery.data?.responses.find((r) => r.participantId === athlete.participantId);
              return (
                <View key={athlete.participantId} style={styles.rsvpRow}>
                  <ThemedText type="small" style={styles.rsvpName}>
                    {athlete.name}
                  </ThemedText>
                  <View style={styles.rsvpOptions}>
                    {RSVP_OPTIONS.map((option) => {
                      const selected = existing?.response === option.value;
                      return (
                        <Button
                          key={option.value}
                          variant={selected ? 'primary' : 'secondary'}
                          onPress={() => onRsvp(athlete.participantId, option.value)}
                          disabled={submitRsvp.isPending}
                          style={styles.rsvpButton}>
                          {option.label}
                        </Button>
                      );
                    })}
                  </View>
                </View>
              );
            })}
          </>
        )}

        {rsvpsQuery.isLoading && <LoadingState label="Loading responses…" />}
        {rsvpsQuery.isError && <ErrorState message="Could not load RSVP responses." onRetry={() => rsvpsQuery.refetch()} />}
        {summary && (
          <>
            <ThemedText type="smallBold" style={styles.goingHeading}>
              Responses
            </ThemedText>
            <View style={styles.summaryRow}>
              <SummaryPill label="Attending" count={summary.attending} color={Brand.victoryGreen} />
              <SummaryPill label="Maybe" count={summary.maybe} color={Brand.championshipGold} />
              <SummaryPill label="Not Attending" count={summary.notAttending} color={Brand.errorRed} />
              <SummaryPill label="No Response" count={summary.noResponse} color={Brand.slateGray} />
            </View>
          </>
        )}

        <View style={styles.actions}>
          {canEdit && (
            <Button
              variant="secondary"
              onPress={() => router.push({ pathname: '/event-form', params: { mode: 'edit', id: event.id } })}
              style={styles.actionButton}>
              Edit
            </Button>
          )}
          {directionsQuery.data?.url && (
            <Button
              variant="secondary"
              onPress={() => Linking.openURL(directionsQuery.data.url!)}
              style={styles.actionButton}>
              Directions
            </Button>
          )}
          <Button variant="primary" onPress={onShare} style={styles.actionButton}>
            Share
          </Button>
        </View>
      </ScrollView>
    </ThemedView>
  );
}

function SummaryPill({ label, count, color }: { label: string; count: number; color: string }) {
  return (
    <ThemedView type="backgroundElement" style={[styles.pill, { borderColor: color }]}>
      <ThemedText type="smallBold">{count}</ThemedText>
      <ThemedText type="small" themeColor="textSecondary">
        {label}
      </ThemedText>
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
  iconHero: {
    width: 96,
    height: 96,
    borderRadius: Spacing.four,
    alignItems: 'center',
    justifyContent: 'center',
    alignSelf: 'flex-start',
    marginTop: Spacing.two,
  },
  title: {
    marginTop: Spacing.two,
    marginBottom: Spacing.two,
  },
  detailRow: {
    flexDirection: 'row',
    gap: Spacing.two,
    alignItems: 'flex-start',
    paddingVertical: 4,
  },
  description: {
    flex: 1,
  },
  goingHeading: {
    marginTop: Spacing.four,
    marginBottom: Spacing.two,
  },
  rsvpRow: {
    marginBottom: Spacing.three,
    gap: Spacing.one,
  },
  rsvpName: {
    marginBottom: 2,
  },
  rsvpOptions: {
    flexDirection: 'row',
    gap: Spacing.two,
  },
  rsvpButton: {
    flex: 1,
  },
  summaryRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: Spacing.two,
  },
  pill: {
    borderRadius: Spacing.two,
    borderWidth: 1,
    paddingHorizontal: Spacing.three,
    paddingVertical: Spacing.two,
    alignItems: 'center',
    minWidth: 80,
  },
  actions: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: Spacing.three,
    marginTop: Spacing.five,
  },
  actionButton: {
    flex: 1,
    minWidth: 100,
  },
});
