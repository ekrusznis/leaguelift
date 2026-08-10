import Ionicons from '@expo/vector-icons/Ionicons';
import { useMemo, useState } from 'react';
import { Pressable, ScrollView, StyleSheet, View } from 'react-native';

import { EmptyState } from '@/components/empty-state';
import { ErrorState } from '@/components/error-state';
import { EventCard } from '@/components/event-card';
import { LoadingState } from '@/components/loading-state';
import { PlatformStatusSpacer } from '@/components/platform-status-spacer';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { useAthleteSelf } from '@/features/athlete/AthleteSelfContext';
import { useParticipantEvents } from '@/features/events/api';
import type { EventResponse } from '@/features/events/types';
import { Brand, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { buildMonthGrid, formatMonthLabel, formatWeekdayHeader, toIsoDate } from '@/lib/calendarGrid';
import { eventIsoDate, formatEventDateHeader } from '@/lib/eventFormat';

/** Athlete's own schedule (Calendar tab) — real GET /participants/{participantId}/events (ADR-104), same month-grid math as Coach/Parent. */
export default function AthleteCalendarScreen() {
  const theme = useTheme();
  const athleteSelf = useAthleteSelf();
  const eventsQuery = useParticipantEvents(athleteSelf.organizationId, athleteSelf.participantId);

  const now = new Date();
  const [year, setYear] = useState(now.getFullYear());
  const [month0, setMonth0] = useState(now.getMonth());
  const [selectedIso, setSelectedIso] = useState(toIsoDate(now));

  const grid = useMemo(() => buildMonthGrid(year, month0), [year, month0]);

  const eventsByDate = useMemo(() => {
    const map = new Map<string, EventResponse[]>();
    for (const event of eventsQuery.data ?? []) {
      const iso = eventIsoDate(event);
      map.set(iso, [...(map.get(iso) ?? []), event]);
    }
    return map;
  }, [eventsQuery.data]);

  const datesWithEvents = useMemo(() => [...eventsByDate.keys()].sort((a, b) => (a < b ? -1 : 1)), [eventsByDate]);

  function changeMonth(delta: number) {
    const next = new Date(year, month0 + delta, 1);
    setYear(next.getFullYear());
    setMonth0(next.getMonth());
  }

  if (athleteSelf.isLoading) {
    return (
      <ThemedView style={styles.container}>
        <PlatformStatusSpacer />
        <LoadingState label="Loading…" />
      </ThemedView>
    );
  }

  if (!athleteSelf.participantId) {
    return (
      <ThemedView style={styles.container}>
        <PlatformStatusSpacer />
        <EmptyState title="No linked athlete record" description="Your account isn't linked to a participant record yet — ask your organization to set this up." />
      </ThemedView>
    );
  }

  return (
    <ThemedView style={styles.container}>
      <PlatformStatusSpacer />
      <View style={styles.header}>
        <Pressable hitSlop={8} onPress={() => changeMonth(-1)}>
          <Ionicons name="chevron-back" size={22} color={theme.text} />
        </Pressable>
        <ThemedText type="smallBold">{formatMonthLabel(year, month0)}</ThemedText>
        <Pressable hitSlop={8} onPress={() => changeMonth(1)}>
          <Ionicons name="chevron-forward" size={22} color={theme.text} />
        </Pressable>
      </View>

      <View style={styles.weekdayRow}>
        {Array.from({ length: 7 }, (_, i) => (
          <ThemedText key={i} type="small" themeColor="textSecondary" style={styles.weekdayCell}>
            {formatWeekdayHeader(i)}
          </ThemedText>
        ))}
      </View>

      <View style={styles.grid}>
        {grid.map((cell) => {
          const selected = cell.isoDate === selectedIso;
          const hasEvent = eventsByDate.has(cell.isoDate);
          return (
            <Pressable
              key={cell.isoDate + cell.date.getTime()}
              onPress={() => setSelectedIso(cell.isoDate)}
              style={styles.dayCellTouchable}>
              <View style={[styles.dayCell, selected && { backgroundColor: Brand.championshipGold }]}>
                <ThemedText
                  type="small"
                  themeColor={!cell.isCurrentMonth ? 'textSecondary' : undefined}
                  style={[!cell.isCurrentMonth && styles.mutedDay, selected && styles.selectedDayText]}>
                  {cell.date.getDate()}
                </ThemedText>
                {hasEvent && !selected && <View style={styles.eventDot} />}
              </View>
            </Pressable>
          );
        })}
      </View>

      {eventsQuery.isLoading && <LoadingState label="Loading schedule…" />}
      {eventsQuery.isError && <ErrorState message="Could not load your schedule." onRetry={() => eventsQuery.refetch()} />}

      <ScrollView contentContainerStyle={styles.agenda}>
        {eventsQuery.data && datesWithEvents.length === 0 && (
          <EmptyState title="No events scheduled" description="Nothing on your schedule yet." />
        )}
        {datesWithEvents.map((isoDate) => {
          const events = eventsByDate.get(isoDate)!;
          return (
            <View key={isoDate} style={styles.agendaGroup}>
              <ThemedText type="smallBold" themeColor="textSecondary">
                {formatEventDateHeader(events[0])}
              </ThemedText>
              <View style={styles.agendaList}>
                {events.map((event) => (
                  <EventCard key={event.id} event={event} />
                ))}
              </View>
            </View>
          );
        })}
      </ScrollView>
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
  weekdayRow: {
    flexDirection: 'row',
    paddingHorizontal: Spacing.three,
  },
  weekdayCell: {
    flex: 1,
    textAlign: 'center',
  },
  grid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    paddingHorizontal: Spacing.three,
    marginBottom: Spacing.two,
  },
  dayCellTouchable: {
    width: `${100 / 7}%`,
    alignItems: 'center',
    paddingVertical: 4,
  },
  dayCell: {
    width: 32,
    height: 32,
    borderRadius: 16,
    alignItems: 'center',
    justifyContent: 'center',
  },
  mutedDay: {
    opacity: 0.4,
  },
  selectedDayText: {
    color: '#0B1F33',
    fontWeight: '700',
  },
  eventDot: {
    position: 'absolute',
    bottom: 2,
    width: 4,
    height: 4,
    borderRadius: 2,
    backgroundColor: Brand.championshipGold,
  },
  agenda: {
    paddingHorizontal: Spacing.four,
    paddingBottom: Spacing.six,
    gap: Spacing.four,
  },
  agendaGroup: {
    gap: Spacing.two,
  },
  agendaList: {
    gap: Spacing.two,
  },
});
