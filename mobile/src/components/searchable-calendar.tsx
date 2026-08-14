import Ionicons from '@expo/vector-icons/Ionicons';
import { useMemo, useState } from 'react';
import { Pressable, ScrollView, StyleSheet, View } from 'react-native';

import { Brand, Spacing } from '@/constants/theme';
import {
  flattenEventPages,
  useInfiniteEventMonthSearch,
  type EventSearchScope,
  type EventSearchSort,
} from '@/features/events/searchApi';
import type { EventResponse, EventStatus, EventType } from '@/features/events/types';
import { useTheme } from '@/hooks/use-theme';
import { buildMonthGrid, formatMonthLabel, formatWeekdayHeader, toIsoDate } from '@/lib/calendarGrid';
import { eventIsoDate, formatEventDateHeader } from '@/lib/eventFormat';

import { EmptyState } from './empty-state';
import { ErrorState } from './error-state';
import { EventCard } from './event-card';
import { ListControls } from './list-controls';
import { ListFooter } from './list-footer';
import { LoadingState } from './loading-state';
import { Modal } from './modal';
import { PlatformStatusSpacer } from './platform-status-spacer';
import { ThemedText } from './themed-text';
import { ThemedView } from './themed-view';

const EVENT_TYPE_LABELS: Record<EventType, string> = {
  COMPETITION: 'Game / match',
  TOURNAMENT: 'Tournament',
  PRACTICE: 'Practice',
  MEETING: 'Meeting',
  OTHER: 'Other',
};

const STATUS_LABELS: Record<EventStatus, string> = {
  DRAFT: 'Draft',
  TENTATIVE: 'Tentative',
  SCHEDULED: 'Scheduled',
  DELAYED: 'Delayed',
  POSTPONED: 'Postponed',
  CANCELLED: 'Cancelled',
  COMPLETED: 'Completed',
};

export function SearchableCalendar({
  scope,
  emptyDescription,
  errorMessage,
  onCreate,
}: {
  scope: EventSearchScope;
  emptyDescription: string;
  errorMessage: string;
  onCreate?: () => void;
}) {
  const theme = useTheme();
  const now = new Date();
  const [year, setYear] = useState(now.getFullYear());
  const [month0, setMonth0] = useState(now.getMonth());
  const [selectedIso, setSelectedIso] = useState(toIsoDate(now));
  const [query, setQuery] = useState('');
  const [eventType, setEventType] = useState<EventType | ''>('');
  const [status, setStatus] = useState<EventStatus | ''>('');
  const [sort, setSort] = useState<EventSearchSort>('DATE_ASC');
  const [filterOpen, setFilterOpen] = useState(false);
  const [sortOpen, setSortOpen] = useState(false);

  const eventsQuery = useInfiniteEventMonthSearch(scope, year, month0, {
    q: query,
    eventType,
    status,
    sort,
  });
  const events = useMemo(() => flattenEventPages(eventsQuery.data?.pages), [eventsQuery.data?.pages]);
  const total = eventsQuery.data?.pages[0]?.totalElements ?? 0;
  const grid = useMemo(() => buildMonthGrid(year, month0), [year, month0]);

  const eventsByDate = useMemo(() => {
    const map = new Map<string, EventResponse[]>();
    for (const event of events) {
      const iso = eventIsoDate(event);
      map.set(iso, [...(map.get(iso) ?? []), event]);
    }
    return map;
  }, [events]);

  const datesWithEvents = useMemo(() => {
    const dates = [...eventsByDate.keys()].sort((a, b) => (a < b ? -1 : 1));
    return sort === 'DATE_DESC' ? dates.reverse() : dates;
  }, [eventsByDate, sort]);

  const activeFilters = useMemo(() => {
    const values: { key: 'eventType' | 'status'; label: string }[] = [];
    if (eventType) values.push({ key: 'eventType', label: EVENT_TYPE_LABELS[eventType] });
    if (status) values.push({ key: 'status', label: STATUS_LABELS[status] });
    return values;
  }, [eventType, status]);

  const sortLabel =
    sort === 'DATE_ASC' ? 'Soonest'
    : sort === 'DATE_DESC' ? 'Latest'
    : 'Title A–Z';

  function changeMonth(delta: number) {
    const next = new Date(year, month0 + delta, 1);
    setYear(next.getFullYear());
    setMonth0(next.getMonth());
    setSelectedIso(toIsoDate(next));
  }

  function clearControls() {
    setQuery('');
    setEventType('');
    setStatus('');
    setSort('DATE_ASC');
  }

  return (
    <ThemedView style={styles.container}>
      <PlatformStatusSpacer />
      <View style={styles.header}>
        <Pressable accessibilityRole="button" accessibilityLabel="Previous month" hitSlop={8} onPress={() => changeMonth(-1)}>
          <Ionicons name="chevron-back" size={22} color={theme.text} />
        </Pressable>
        <ThemedText type="smallBold">{formatMonthLabel(year, month0)}</ThemedText>
        <View style={styles.headerRight}>
          <Pressable accessibilityRole="button" accessibilityLabel="Next month" hitSlop={8} onPress={() => changeMonth(1)}>
            <Ionicons name="chevron-forward" size={22} color={theme.text} />
          </Pressable>
          {onCreate && (
            <Pressable accessibilityRole="button" accessibilityLabel="Create event" hitSlop={8} onPress={onCreate}>
              <Ionicons name="add-circle" size={26} color={Brand.championshipGold} />
            </Pressable>
          )}
        </View>
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
              accessibilityRole="button"
              accessibilityLabel={`${cell.date.toLocaleDateString()}${hasEvent ? ', has events' : ''}`}
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

      <View style={styles.controls}>
        <ListControls
          query={query}
          onChangeQuery={setQuery}
          searchPlaceholder={`Search ${formatMonthLabel(year, month0)} events`}
          resultCount={total}
          activeFilters={activeFilters.map((item) => item.label)}
          onRemoveFilter={(index) => {
            const filter = activeFilters[index];
            if (filter?.key === 'eventType') setEventType('');
            if (filter?.key === 'status') setStatus('');
          }}
          onClearFilters={clearControls}
          onPressFilter={() => setFilterOpen(true)}
          onPressSort={() => setSortOpen(true)}
          sortLabel={sortLabel}
        />
      </View>

      {eventsQuery.isLoading && <LoadingState label="Loading schedule…" />}
      {eventsQuery.isError && <ErrorState message={errorMessage} onRetry={() => eventsQuery.refetch()} />}

      {!eventsQuery.isLoading && !eventsQuery.isError && (
        <ScrollView contentContainerStyle={styles.agenda} keyboardShouldPersistTaps="handled">
          {events.length === 0 && (
            <EmptyState
              title={query.trim() || activeFilters.length > 0 ? 'No results found' : 'No events this month'}
              description={
                query.trim() || activeFilters.length > 0
                  ? 'Try changing your search or filters.'
                  : emptyDescription
              }
            />
          )}

          {datesWithEvents.map((isoDate) => {
            const dateEvents = eventsByDate.get(isoDate)!;
            return (
              <View key={isoDate} style={styles.agendaGroup}>
                <ThemedText type="smallBold" themeColor="textSecondary">
                  {formatEventDateHeader(dateEvents[0])}
                </ThemedText>
                <View style={styles.agendaList}>
                  {dateEvents.map((event) => <EventCard key={event.id} event={event} />)}
                </View>
              </View>
            );
          })}

          <ListFooter
            loadedCount={events.length}
            totalCount={total}
            hasMore={!!eventsQuery.hasNextPage}
            loadingMore={eventsQuery.isFetchingNextPage}
            onLoadMore={() => eventsQuery.fetchNextPage()}
          />
        </ScrollView>
      )}

      <Modal visible={filterOpen} onClose={() => setFilterOpen(false)}>
        <ThemedText type="smallBold" style={styles.modalTitle}>Filter events</ThemedText>
        <ScrollView style={styles.modalScroll} showsVerticalScrollIndicator={false}>
          <ThemedText type="smallBold" style={styles.filterHeading}>Type</ThemedText>
          <Option
            selected={!eventType}
            label="All event types"
            onPress={() => setEventType('')}
          />
          {(Object.keys(EVENT_TYPE_LABELS) as EventType[]).map((value) => (
            <Option
              key={value}
              selected={eventType === value}
              label={EVENT_TYPE_LABELS[value]}
              onPress={() => setEventType(value)}
            />
          ))}

          <ThemedText type="smallBold" style={styles.filterHeading}>Status</ThemedText>
          <Option selected={!status} label="All statuses" onPress={() => setStatus('')} />
          {(Object.keys(STATUS_LABELS) as EventStatus[]).map((value) => (
            <Option
              key={value}
              selected={status === value}
              label={STATUS_LABELS[value]}
              onPress={() => setStatus(value)}
            />
          ))}
        </ScrollView>

        <Pressable accessibilityRole="button" onPress={() => setFilterOpen(false)} style={styles.done}>
          <ThemedText type="smallBold">Done</ThemedText>
        </Pressable>
      </Modal>

      <Modal visible={sortOpen} onClose={() => setSortOpen(false)}>
        <ThemedText type="smallBold" style={styles.modalTitle}>Sort events</ThemedText>
        <Option selected={sort === 'DATE_ASC'} label="Soonest first" onPress={() => { setSort('DATE_ASC'); setSortOpen(false); }} />
        <Option selected={sort === 'DATE_DESC'} label="Latest first" onPress={() => { setSort('DATE_DESC'); setSortOpen(false); }} />
        <Option selected={sort === 'TITLE_ASC'} label="Title A–Z" onPress={() => { setSort('TITLE_ASC'); setSortOpen(false); }} />
      </Modal>
    </ThemedView>
  );

  function Option({ selected, label, onPress }: { selected: boolean; label: string; onPress: () => void }) {
    return (
      <Pressable accessibilityRole="button" onPress={onPress} style={styles.option}>
        <ThemedText type={selected ? 'smallBold' : 'default'}>{label}</ThemedText>
        {selected && <Ionicons name="checkmark" size={18} color={theme.text} />}
      </Pressable>
    );
  }
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  header: {
    minHeight: 48,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: Spacing.four,
    paddingVertical: Spacing.two,
  },
  headerRight: { flexDirection: 'row', alignItems: 'center', gap: Spacing.three },
  weekdayRow: { flexDirection: 'row', paddingHorizontal: Spacing.three },
  weekdayCell: { flex: 1, textAlign: 'center' },
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
  mutedDay: { opacity: 0.4 },
  selectedDayText: { color: '#0B1F33', fontWeight: '700' },
  eventDot: {
    position: 'absolute',
    bottom: 2,
    width: 4,
    height: 4,
    borderRadius: 2,
    backgroundColor: Brand.championshipGold,
  },
  controls: { paddingHorizontal: Spacing.four, paddingBottom: Spacing.three },
  agenda: {
    paddingHorizontal: Spacing.four,
    paddingBottom: Spacing.six,
    gap: Spacing.four,
  },
  agendaGroup: { gap: Spacing.two },
  agendaList: { gap: Spacing.two },
  modalTitle: { marginBottom: Spacing.two },
  modalScroll: { maxHeight: 430 },
  filterHeading: { marginTop: Spacing.two, marginBottom: Spacing.one },
  option: {
    minHeight: 44,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: Spacing.two,
  },
  done: {
    minHeight: 46,
    marginTop: Spacing.three,
    alignItems: 'center',
    justifyContent: 'center',
  },
});
