import Ionicons from '@expo/vector-icons/Ionicons';
import { router } from 'expo-router';
import { Pressable, StyleSheet } from 'react-native';

import type { EventResponse, EventType } from '@/features/events/types';
import { Brand, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { formatEventTimeRange } from '@/lib/eventFormat';

import { ThemedText } from './themed-text';
import { ThemedView } from './themed-view';

const EVENT_ICON: Record<EventType, keyof typeof Ionicons.glyphMap> = {
  PRACTICE: 'calendar',
  COMPETITION: 'trophy',
  TOURNAMENT: 'trophy',
  MEETING: 'people',
  OTHER: 'ellipse',
};

/** Shared between the Dashboard "Upcoming" list and Calendar's agenda list (ADR-102, real EventResponse). */
export function EventCard({ event }: { event: EventResponse }) {
  const theme = useTheme();
  return (
    <Pressable onPress={() => router.push({ pathname: '/event-details', params: { id: event.id } })}>
      {({ pressed }) => (
        <ThemedView type="backgroundElement" style={[styles.row, pressed && { opacity: 0.7 }]}>
          <ThemedView type="backgroundSelected" style={styles.iconBadge}>
            <Ionicons name={EVENT_ICON[event.eventType]} size={20} color={Brand.championshipGold} />
          </ThemedView>
          <ThemedView style={styles.body}>
            <ThemedText type="smallBold">{event.displayTitle}</ThemedText>
            <ThemedText type="small" themeColor="textSecondary">
              {formatEventTimeRange(event)}
            </ThemedText>
            {event.venueName && (
              <ThemedText type="small" themeColor="textSecondary">
                {event.venueName}
              </ThemedText>
            )}
          </ThemedView>
          <Ionicons name="chevron-forward" size={18} color={theme.textSecondary} />
        </ThemedView>
      )}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.three,
    borderRadius: Spacing.three,
    padding: Spacing.three,
  },
  iconBadge: {
    width: 40,
    height: 40,
    borderRadius: Spacing.two,
    alignItems: 'center',
    justifyContent: 'center',
  },
  body: {
    flex: 1,
    gap: 2,
    backgroundColor: 'transparent',
  },
});
