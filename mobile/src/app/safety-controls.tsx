import Ionicons from '@expo/vector-icons/Ionicons';
import { useState } from 'react';
import { KeyboardAvoidingView, Platform, Pressable, ScrollView, StyleSheet, TextInput, View } from 'react-native';

import { Button } from '@/components/button';
import { EmptyState } from '@/components/empty-state';
import { ErrorState } from '@/components/error-state';
import { LoadingState } from '@/components/loading-state';
import { Modal } from '@/components/modal';
import { ScreenHeader } from '@/components/screen-header';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { useToast } from '@/components/toast';
import {
  useCreateContactRestriction,
  useGuardianMessagingParticipants,
  useLiftContactRestriction,
  useMyContactRestrictions,
} from '@/features/messaging-safety/api';
import type { MessageContactRestrictionKind, MessageContactRestrictionResponse } from '@/features/messaging-safety/types';
import { Brand, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

const KIND_OPTIONS: { value: MessageContactRestrictionKind; label: string }[] = [
  { value: 'ADULT_TO_MINOR', label: 'Stop staff → athlete messages' },
  { value: 'ALL_MESSAGING', label: 'Stop all messaging for athlete' },
];

const KIND_LABEL: Record<MessageContactRestrictionKind, string> = {
  ADULT_TO_MINOR: 'Staff → athlete',
  ALL_MESSAGING: 'All messaging',
};

/**
 * Guardian communication controls (ADR-108) — a real feature that already existed on
 * web (frontend's GuardianMessageSafetyControls) but had no mobile equivalent. Lets a
 * guardian stop staff-to-athlete messages, or all messaging entirely, for one of their
 * own linked athletes — real backend endpoints, no new capability required.
 */
export default function SafetyControlsScreen() {
  const theme = useTheme();
  const toast = useToast();
  const participantsQuery = useGuardianMessagingParticipants();
  const restrictionsQuery = useMyContactRestrictions();
  const createRestriction = useCreateContactRestriction();
  const liftRestriction = useLiftContactRestriction();

  const [participantKey, setParticipantKey] = useState<string | null>(null);
  const [kind, setKind] = useState<MessageContactRestrictionKind>('ADULT_TO_MINOR');
  const [note, setNote] = useState('');
  const [liftTarget, setLiftTarget] = useState<MessageContactRestrictionResponse | null>(null);
  const [liftNote, setLiftNote] = useState('');

  const participants = participantsQuery.data ?? [];
  const selectedParticipant =
    participants.find((p) => `${p.organizationId}:${p.participantId}` === participantKey) ?? participants[0] ?? null;

  async function submit() {
    if (!selectedParticipant) return;
    try {
      await createRestriction.mutateAsync({
        organizationId: selectedParticipant.organizationId,
        participantId: selectedParticipant.participantId,
        kind,
        note: note.trim() || undefined,
      });
      setNote('');
      toast.show('Communication restriction recorded.', 'success');
    } catch {
      toast.show('The restriction could not be recorded. Please try again.', 'error');
    }
  }

  async function confirmLift() {
    if (!liftTarget || liftNote.trim().length < 3) return;
    try {
      await liftRestriction.mutateAsync({ restrictionId: liftTarget.id, note: liftNote.trim() });
      toast.show('Restriction lifted.', 'success');
    } catch {
      toast.show('Could not lift that restriction. Please try again.', 'error');
    } finally {
      setLiftTarget(null);
      setLiftNote('');
    }
  }

  return (
    <KeyboardAvoidingView style={styles.container} behavior={Platform.OS === 'ios' ? 'padding' : 'height'}>
      <ThemedView style={styles.container}>
        <ScreenHeader title="Messaging Safety" />
        <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
        <ThemedText type="small" themeColor="textSecondary">
          Record a request to stop staff messaging to your athlete, or all Rally26 messaging for that athlete. Requests are
          retained as safety history.
        </ThemedText>

        {participantsQuery.isLoading && <LoadingState label="Loading linked athletes…" />}
        {participantsQuery.isError && (
          <ErrorState message="Could not load your linked athletes." onRetry={() => participantsQuery.refetch()} />
        )}
        {participantsQuery.data && participants.length === 0 && (
          <EmptyState
            title="No linked athletes"
            description="Communication restrictions appear once your guardian account is linked to an athlete."
          />
        )}

        {participants.length > 0 && (
          <>
            <ThemedText type="small" themeColor="textSecondary" style={styles.label}>
              Athlete
            </ThemedText>
            <View style={styles.chipRow}>
              {participants.map((p) => {
                const key = `${p.organizationId}:${p.participantId}`;
                const selected = selectedParticipant && key === `${selectedParticipant.organizationId}:${selectedParticipant.participantId}`;
                return (
                  <Pressable key={key} onPress={() => setParticipantKey(key)} style={[styles.chip, selected && styles.chipSelected]}>
                    <ThemedText type="small" style={selected ? styles.chipTextSelected : undefined}>
                      {p.displayName}
                    </ThemedText>
                  </Pressable>
                );
              })}
            </View>

            <ThemedText type="small" themeColor="textSecondary" style={styles.label}>
              Restriction
            </ThemedText>
            <View style={styles.chipRow}>
              {KIND_OPTIONS.map((option) => {
                const selected = option.value === kind;
                return (
                  <Pressable key={option.value} onPress={() => setKind(option.value)} style={[styles.chip, selected && styles.chipSelected]}>
                    <ThemedText type="small" style={selected ? styles.chipTextSelected : undefined}>
                      {option.label}
                    </ThemedText>
                  </Pressable>
                );
              })}
            </View>

            <ThemedText type="small" themeColor="textSecondary" style={styles.label}>
              Note (optional)
            </ThemedText>
            <TextInput
              value={note}
              onChangeText={setNote}
              placeholder="Add context for this request…"
              placeholderTextColor={theme.textSecondary}
              style={[styles.input, { color: theme.text, backgroundColor: theme.backgroundElement }]}
              multiline
            />

            <Button variant="primary" disabled={createRestriction.isPending} onPress={submit} style={styles.submitButton}>
              Record Restriction
            </Button>
          </>
        )}

        <ThemedText type="smallBold" style={styles.sectionTitle}>
          Your Restrictions
        </ThemedText>
        {restrictionsQuery.isLoading && <LoadingState label="Loading restrictions…" />}
        {restrictionsQuery.data && restrictionsQuery.data.length === 0 && (
          <ThemedText type="small" themeColor="textSecondary">
            No restrictions on file.
          </ThemedText>
        )}
        <View style={styles.list}>
          {restrictionsQuery.data?.map((item) => (
            <ThemedView key={item.id} type="backgroundElement" style={styles.restrictionRow}>
              <View style={styles.restrictionBody}>
                <ThemedText type="smallBold">{item.participantDisplayName}</ThemedText>
                <ThemedText type="small" themeColor="textSecondary">
                  {KIND_LABEL[item.kind]} · {item.status}
                </ThemedText>
                {item.note && (
                  <ThemedText type="small" themeColor="textSecondary">
                    {item.note}
                  </ThemedText>
                )}
              </View>
              {item.status === 'ACTIVE' && (
                <Pressable onPress={() => setLiftTarget(item)} style={styles.liftButton}>
                  <Ionicons name="lock-open-outline" size={16} color={Brand.championshipGold} />
                  <ThemedText type="link">Lift</ThemedText>
                </Pressable>
              )}
            </ThemedView>
          ))}
        </View>
      </ScrollView>

      <Modal
        visible={!!liftTarget}
        onClose={() => {
          setLiftTarget(null);
          setLiftNote('');
        }}>
        <ThemedText type="smallBold" style={styles.modalTitle}>
          Lift restriction for {liftTarget?.participantDisplayName}
        </ThemedText>
        <ThemedText type="small" themeColor="textSecondary" style={styles.modalNote}>
          A short note explaining why is required.
        </ThemedText>
        <TextInput
          value={liftNote}
          onChangeText={setLiftNote}
          placeholder="Why are you lifting this restriction?"
          placeholderTextColor={theme.textSecondary}
          style={[styles.input, { color: theme.text, backgroundColor: theme.background }]}
          multiline
        />
        <Button
          variant="primary"
          disabled={liftNote.trim().length < 3 || liftRestriction.isPending}
          onPress={confirmLift}
          style={styles.modalSubmit}>
          Lift Restriction
        </Button>
      </Modal>
      </ThemedView>
    </KeyboardAvoidingView>
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
  label: {
    marginTop: Spacing.three,
    marginBottom: Spacing.one,
  },
  chipRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: Spacing.two,
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
  input: {
    borderRadius: Spacing.two,
    paddingHorizontal: Spacing.three,
    paddingVertical: Spacing.two,
    marginTop: Spacing.one,
    minHeight: 70,
    textAlignVertical: 'top',
  },
  submitButton: {
    marginTop: Spacing.four,
  },
  sectionTitle: {
    marginTop: Spacing.five,
    marginBottom: Spacing.one,
  },
  list: {
    gap: Spacing.two,
  },
  restrictionRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    borderRadius: Spacing.three,
    padding: Spacing.three,
    gap: Spacing.two,
  },
  restrictionBody: {
    flex: 1,
    gap: 2,
  },
  liftButton: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.one,
  },
  modalTitle: {
    marginBottom: Spacing.one,
  },
  modalNote: {
    marginBottom: Spacing.two,
  },
  modalSubmit: {
    marginTop: Spacing.three,
  },
});
