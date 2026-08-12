import DateTimePicker from '@react-native-community/datetimepicker';
import Ionicons from '@expo/vector-icons/Ionicons';
import { router, useLocalSearchParams } from 'expo-router';
import { useState } from 'react';
import { KeyboardAvoidingView, Platform, Pressable, ScrollView, StyleSheet, TextInput, View } from 'react-native';

import { Button } from '@/components/button';
import { ErrorState } from '@/components/error-state';
import { LoadingState } from '@/components/loading-state';
import { ScreenHeader } from '@/components/screen-header';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { useToast } from '@/components/toast';
import { useDashboardContext } from '@/features/dashboard/api';
import { useCreateEvent, useEvent, useEventTimezoneDefault, useUpdateEvent } from '@/features/events/api';
import type { EventResponse, EventType, EventVisibility } from '@/features/events/types';
import { Brand, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { apiFetch } from '@/lib/apiClient';

const EVENT_TYPES: { value: EventType; label: string }[] = [
  { value: 'PRACTICE', label: 'Practice' },
  { value: 'COMPETITION', label: 'Competition' },
  { value: 'TOURNAMENT', label: 'Tournament' },
  { value: 'MEETING', label: 'Meeting' },
  { value: 'OTHER', label: 'Other' },
];

const VISIBILITY_OPTIONS: { value: EventVisibility; label: string }[] = [
  { value: 'TEAM', label: 'Team' },
  { value: 'ORGANIZATION', label: 'Organization' },
  { value: 'PUBLIC', label: 'Public' },
];

type PickerTarget = { field: 'start' | 'end'; mode: 'date' | 'time' } | null;

function titleFallback(type: EventType, opponent: string | null) {
  return opponent ? `${type} vs ${opponent}` : type;
}

function defaultStart() {
  const d = new Date();
  d.setMinutes(0, 0, 0);
  d.setHours(d.getHours() + 1);
  return d;
}

function defaultEnd() {
  const d = new Date();
  d.setMinutes(0, 0, 0);
  d.setHours(d.getHours() + 2);
  return d;
}

/**
 * Create + edit, one screen (ADR-107). This outer component only resolves routing
 * params and (for edit) waits for the real event to load — EventFormFields below is
 * keyed to only mount once that data is ready (or never, for create), so its useState
 * initializers can read straight from the loaded event with no hydration effect
 * needed (React Compiler's purity lint flags setState-in-effect; deriving initial
 * state at mount time avoids the pattern entirely, same fix as CoachContext's
 * selectedTeamId).
 */
export default function EventFormScreen() {
  const { mode, id, teamId } = useLocalSearchParams<{ mode: 'create' | 'edit'; id?: string; teamId?: string }>();
  const isEdit = mode === 'edit';
  const dashboardContext = useDashboardContext(true);
  const organizationId = dashboardContext.data?.organizationId ?? null;

  const existingQuery = useEvent(isEdit ? organizationId : null, isEdit ? (id ?? null) : null);
  const timezoneQuery = useEventTimezoneDefault(!isEdit ? organizationId : null, !isEdit ? (teamId ?? null) : null);

  if (isEdit && existingQuery.isLoading) {
    return (
      <>
        <ScreenHeader title="Edit Event" />
        <LoadingState label="Loading event…" />
      </>
    );
  }

  if (isEdit && (existingQuery.isError || !existingQuery.data)) {
    return (
      <>
        <ScreenHeader title="Edit Event" />
        <ErrorState message="Could not load this event." onRetry={() => existingQuery.refetch()} />
      </>
    );
  }

  return (
    <EventFormFields
      isEdit={isEdit}
      organizationId={organizationId}
      eventId={id ?? null}
      teamId={teamId ?? null}
      existing={existingQuery.data ?? null}
      defaultTimezone={timezoneQuery.data?.timezone ?? null}
    />
  );
}

function EventFormFields({
  isEdit,
  organizationId,
  eventId,
  teamId,
  existing,
  defaultTimezone,
}: {
  isEdit: boolean;
  organizationId: string | null;
  eventId: string | null;
  teamId: string | null;
  existing: EventResponse | null;
  defaultTimezone: string | null;
}) {
  const theme = useTheme();
  const toast = useToast();
  const createEvent = useCreateEvent(organizationId);
  const updateEvent = useUpdateEvent(organizationId, eventId ?? '');

  const [eventType, setEventType] = useState<EventType>(existing?.eventType ?? 'PRACTICE');
  const [title, setTitle] = useState(
    existing && existing.displayTitle !== titleFallback(existing.eventType, existing.opponentName) ? existing.displayTitle : '',
  );
  const [opponentName, setOpponentName] = useState(existing?.opponentName ?? '');
  const [description, setDescription] = useState(existing?.description ?? '');
  const [venueName, setVenueName] = useState(existing?.venueName ?? '');
  const [address, setAddress] = useState(existing?.address ?? '');
  const [visibility, setVisibility] = useState<EventVisibility>(existing?.visibility ?? 'TEAM');
  const [startAt, setStartAt] = useState(() => (existing?.startAt ? new Date(existing.startAt) : defaultStart()));
  const [endAt, setEndAt] = useState(() => (existing?.endAt ? new Date(existing.endAt) : defaultEnd()));
  const [picker, setPicker] = useState<PickerTarget>(null);

  function onPickerChange(field: 'start' | 'end', selected: Date | undefined) {
    setPicker(null);
    if (!selected) return;
    const setter = field === 'start' ? setStartAt : setEndAt;
    setter(selected);
  }

  async function submit() {
    if (endAt <= startAt) {
      toast.show('End time must be after the start time.', 'error');
      return;
    }
    try {
      if (isEdit) {
        await updateEvent.mutateAsync({
          title: title.trim() || undefined,
          description: description.trim() || undefined,
          startAt: startAt.toISOString(),
          endAt: endAt.toISOString(),
          venueName: venueName.trim() || undefined,
          address: address.trim() || undefined,
          opponentName: opponentName.trim() || undefined,
        });
        toast.show('Event updated.', 'success');
        router.back();
      } else {
        const timezone = defaultTimezone ?? 'America/New_York';
        const created = await createEvent.mutateAsync({
          teamId: teamId || undefined,
          eventType,
          title: title.trim() || undefined,
          opponentName: opponentName.trim() || undefined,
          description: description.trim() || undefined,
          startAt: startAt.toISOString(),
          endAt: endAt.toISOString(),
          timezone,
          venueName: venueName.trim() || undefined,
          address: address.trim() || undefined,
          visibility,
        });
        // usePublishEvent's mutation is bound to the `id` route param, which doesn't
        // exist yet for a brand-new event — publish the just-created event's real id
        // directly instead of standing up a second hook for a one-shot call.
        await apiFetch(`/organizations/${organizationId}/events/${created.id}/publish`, { method: 'POST' });
        toast.show('Event created.', 'success');
        router.replace({ pathname: '/event-details', params: { id: created.id } });
      }
    } catch {
      toast.show(isEdit ? 'Could not update that event. Please try again.' : 'Could not create that event. Please try again.', 'error');
    }
  }

  const saving = createEvent.isPending || updateEvent.isPending;

  return (
    <KeyboardAvoidingView style={styles.container} behavior={Platform.OS === 'ios' ? 'padding' : 'height'}>
      <ScreenHeader title={isEdit ? 'Edit Event' : 'New Event'} />
      <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
        {!isEdit && (
          <>
            <ThemedText type="small" themeColor="textSecondary">
              Type
            </ThemedText>
            <View style={styles.chipRow}>
              {EVENT_TYPES.map((option) => {
                const selected = option.value === eventType;
                return (
                  <Pressable
                    key={option.value}
                    onPress={() => setEventType(option.value)}
                    style={[styles.chip, selected && styles.chipSelected]}>
                    <ThemedText type="small" style={selected ? styles.chipTextSelected : undefined}>
                      {option.label}
                    </ThemedText>
                  </Pressable>
                );
              })}
            </View>
          </>
        )}

        <Field label="Title (optional)">
          <TextInput
            value={title}
            onChangeText={setTitle}
            placeholder="Auto-generated if left blank"
            placeholderTextColor={theme.textSecondary}
            style={[styles.input, { color: theme.text, backgroundColor: theme.backgroundElement }]}
          />
        </Field>

        <Field label="Opponent (optional)">
          <TextInput
            value={opponentName}
            onChangeText={setOpponentName}
            placeholder="Opponent name"
            placeholderTextColor={theme.textSecondary}
            style={[styles.input, { color: theme.text, backgroundColor: theme.backgroundElement }]}
          />
        </Field>

        <ThemedText type="small" themeColor="textSecondary" style={styles.label}>
          Starts
        </ThemedText>
        <View style={styles.dateTimeRow}>
          <Pressable onPress={() => setPicker({ field: 'start', mode: 'date' })} style={styles.dateChip}>
            <Ionicons name="calendar-outline" size={16} color={theme.textSecondary} />
            <ThemedText type="small">{startAt.toLocaleDateString()}</ThemedText>
          </Pressable>
          <Pressable onPress={() => setPicker({ field: 'start', mode: 'time' })} style={styles.dateChip}>
            <Ionicons name="time-outline" size={16} color={theme.textSecondary} />
            <ThemedText type="small">{startAt.toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' })}</ThemedText>
          </Pressable>
        </View>

        <ThemedText type="small" themeColor="textSecondary" style={styles.label}>
          Ends
        </ThemedText>
        <View style={styles.dateTimeRow}>
          <Pressable onPress={() => setPicker({ field: 'end', mode: 'date' })} style={styles.dateChip}>
            <Ionicons name="calendar-outline" size={16} color={theme.textSecondary} />
            <ThemedText type="small">{endAt.toLocaleDateString()}</ThemedText>
          </Pressable>
          <Pressable onPress={() => setPicker({ field: 'end', mode: 'time' })} style={styles.dateChip}>
            <Ionicons name="time-outline" size={16} color={theme.textSecondary} />
            <ThemedText type="small">{endAt.toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' })}</ThemedText>
          </Pressable>
        </View>

        {picker && (
          <DateTimePicker
            value={picker.field === 'start' ? startAt : endAt}
            mode={picker.mode}
            display={Platform.OS === 'ios' ? 'spinner' : 'default'}
            onChange={(_event, selected) => onPickerChange(picker.field, selected)}
          />
        )}

        <Field label="Venue (optional)">
          <TextInput
            value={venueName}
            onChangeText={setVenueName}
            placeholder="Venue name"
            placeholderTextColor={theme.textSecondary}
            style={[styles.input, { color: theme.text, backgroundColor: theme.backgroundElement }]}
          />
        </Field>

        <Field label="Address (optional)">
          <TextInput
            value={address}
            onChangeText={setAddress}
            placeholder="Street address"
            placeholderTextColor={theme.textSecondary}
            style={[styles.input, { color: theme.text, backgroundColor: theme.backgroundElement }]}
          />
        </Field>

        <Field label="Description (optional)">
          <TextInput
            value={description}
            onChangeText={setDescription}
            placeholder="Notes for the team…"
            placeholderTextColor={theme.textSecondary}
            style={[styles.input, styles.multilineInput, { color: theme.text, backgroundColor: theme.backgroundElement }]}
            multiline
          />
        </Field>

        {!isEdit && (
          <>
            <ThemedText type="small" themeColor="textSecondary" style={styles.label}>
              Visibility
            </ThemedText>
            <View style={styles.chipRow}>
              {VISIBILITY_OPTIONS.map((option) => {
                const selected = option.value === visibility;
                return (
                  <Pressable
                    key={option.value}
                    onPress={() => setVisibility(option.value)}
                    style={[styles.chip, selected && styles.chipSelected]}>
                    <ThemedText type="small" style={selected ? styles.chipTextSelected : undefined}>
                      {option.label}
                    </ThemedText>
                  </Pressable>
                );
              })}
            </View>
          </>
        )}
      </ScrollView>
      <View style={styles.footer}>
        <Button variant="primary" disabled={saving} onPress={submit}>
          {isEdit ? 'Save Changes' : 'Create Event'}
        </Button>
      </View>
    </KeyboardAvoidingView>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <ThemedView style={styles.field}>
      <ThemedText type="small" themeColor="textSecondary" style={styles.label}>
        {label}
      </ThemedText>
      {children}
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
  },
  field: {
    marginTop: Spacing.three,
  },
  label: {
    marginTop: Spacing.three,
    marginBottom: Spacing.one,
  },
  input: {
    borderRadius: Spacing.two,
    paddingHorizontal: Spacing.three,
    paddingVertical: Spacing.two,
    marginTop: Spacing.one,
  },
  multilineInput: {
    minHeight: 90,
    textAlignVertical: 'top',
  },
  chipRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: Spacing.two,
    marginTop: Spacing.one,
  },
  chip: {
    paddingHorizontal: Spacing.three,
    paddingVertical: Spacing.one,
    borderRadius: Spacing.four,
    backgroundColor: '#102B46',
  },
  chipSelected: {
    backgroundColor: Brand.championshipGold,
  },
  chipTextSelected: {
    color: '#0B1F33',
  },
  dateTimeRow: {
    flexDirection: 'row',
    gap: Spacing.two,
  },
  dateChip: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.one,
    paddingHorizontal: Spacing.three,
    paddingVertical: Spacing.two,
    borderRadius: Spacing.two,
    backgroundColor: '#102B46',
  },
  footer: {
    paddingHorizontal: Spacing.four,
    paddingBottom: Spacing.four,
    paddingTop: Spacing.two,
  },
});
