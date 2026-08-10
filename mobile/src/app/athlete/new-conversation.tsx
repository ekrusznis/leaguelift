import Ionicons from '@expo/vector-icons/Ionicons';
import { router } from 'expo-router';
import { useState } from 'react';
import { Pressable, ScrollView, StyleSheet, TextInput, View } from 'react-native';

import { Button } from '@/components/button';
import { EmptyState } from '@/components/empty-state';
import { ErrorState } from '@/components/error-state';
import { LoadingState } from '@/components/loading-state';
import { ScreenHeader } from '@/components/screen-header';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { useToast } from '@/components/toast';
import {
  useAthleteContacts,
  useAthleteMessagingTeams,
  useCreateAthleteConversation,
} from '@/features/messaging/api';
import type { AthleteMessagingTeamResponse } from '@/features/messaging/types';
import { Brand, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { generateIdempotencyKey } from '@/lib/idempotency';
import { ApiError } from '@/lib/apiClient';

type Step = 'team' | 'contacts' | 'compose';

/**
 * Athlete-only "start a conversation" flow (ADR-104) — no equivalent for Coach
 * (broadcasts only) or Parent (no self-initiated conversations). Team selection only
 * lists teams whose SafeSport review gate is enabled (athleteMessagingEnabled),
 * matching /me/messaging/athlete-teams's own per-team computed flag — this keeps the
 * app from ever needing to surface a raw 409 ATHLETE_MESSAGING_REVIEW_REQUIRED. The
 * 403 ATHLETE_MESSAGING_RESTRICTED (a guardian-set restriction) can still occur at the
 * contacts-fetch or create-conversation step and is handled explicitly, not as a
 * generic error.
 */
export default function NewAthleteConversationScreen() {
  const theme = useTheme();
  const toast = useToast();
  const [step, setStep] = useState<Step>('team');
  const [selectedTeam, setSelectedTeam] = useState<AthleteMessagingTeamResponse | null>(null);
  const [selectedContactIds, setSelectedContactIds] = useState<Set<string>>(new Set());
  const [title, setTitle] = useState('');
  const [message, setMessage] = useState('');

  const teamsQuery = useAthleteMessagingTeams(step === 'team');
  const contactsQuery = useAthleteContacts(selectedTeam?.organizationId ?? null, selectedTeam?.teamId ?? null);
  const createConversation = useCreateAthleteConversation();

  function toggleContact(userId: string) {
    setSelectedContactIds((prev) => {
      const next = new Set(prev);
      if (next.has(userId)) next.delete(userId);
      else next.add(userId);
      return next;
    });
  }

  async function submit() {
    if (!selectedTeam || selectedContactIds.size === 0 || !title.trim() || !message.trim()) return;
    try {
      const thread = await createConversation.mutateAsync({
        organizationId: selectedTeam.organizationId,
        teamId: selectedTeam.teamId,
        idempotencyKey: generateIdempotencyKey(),
        title: title.trim(),
        targetUserIds: [...selectedContactIds],
        initialMessageIdempotencyKey: generateIdempotencyKey(),
        initialMessage: message.trim(),
      });
      router.replace({ pathname: '/messages/[threadId]', params: { threadId: thread.id } });
    } catch (error) {
      if (error instanceof ApiError && error.body.code === 'ATHLETE_MESSAGING_RESTRICTED') {
        toast.show('A guardian restriction prevents this conversation from being created.', 'error');
      } else {
        toast.show('Could not start that conversation. Please try again.', 'error');
      }
    }
  }

  return (
    <ThemedView style={styles.container}>
      <ScreenHeader
        title={step === 'team' ? 'New Conversation' : step === 'contacts' ? selectedTeam?.teamName ?? 'Contacts' : 'Message'}
      />

      {step === 'team' && (
        <ScrollView contentContainerStyle={styles.list}>
          {teamsQuery.isLoading && <LoadingState label="Loading teams…" />}
          {teamsQuery.isError && <ErrorState message="Could not load your teams." onRetry={() => teamsQuery.refetch()} />}
          {teamsQuery.data && teamsQuery.data.length === 0 && (
            <EmptyState title="No teams available" description="You aren't an active athlete on any team yet." />
          )}
          {teamsQuery.data?.map((team) => (
            <Pressable
              key={team.teamId}
              disabled={!team.athleteMessagingEnabled}
              onPress={() => {
                setSelectedTeam(team);
                setStep('contacts');
              }}>
              <ThemedView type="backgroundElement" style={[styles.row, !team.athleteMessagingEnabled && styles.rowDisabled]}>
                <View style={styles.rowBody}>
                  <ThemedText type="smallBold">{team.teamName}</ThemedText>
                  {!team.athleteMessagingEnabled && (
                    <ThemedText type="small" themeColor="textSecondary">
                      Messaging pending organization review
                    </ThemedText>
                  )}
                </View>
                {team.athleteMessagingEnabled && <Ionicons name="chevron-forward" size={18} color={theme.textSecondary} />}
              </ThemedView>
            </Pressable>
          ))}
        </ScrollView>
      )}

      {step === 'contacts' && (
        <>
          <ScrollView contentContainerStyle={styles.list}>
            {contactsQuery.isLoading && <LoadingState label="Loading contacts…" />}
            {contactsQuery.isError && contactsQuery.error instanceof ApiError && contactsQuery.error.body.code === 'ATHLETE_MESSAGING_RESTRICTED' ? (
              <ErrorState message="A guardian communication restriction currently prevents peer messaging for this account." />
            ) : (
              contactsQuery.isError && <ErrorState message="Could not load contacts." onRetry={() => contactsQuery.refetch()} />
            )}
            {contactsQuery.data && contactsQuery.data.length === 0 && (
              <EmptyState title="No contacts available" description="No eligible teammates or staff to message on this team yet." />
            )}
            {contactsQuery.data?.map((contact) => {
              const selected = selectedContactIds.has(contact.userId);
              return (
                <Pressable key={contact.userId} onPress={() => toggleContact(contact.userId)}>
                  <ThemedView type="backgroundElement" style={styles.row}>
                    <View style={styles.rowBody}>
                      <ThemedText type="smallBold">{contact.displayName}</ThemedText>
                      <ThemedText type="small" themeColor="textSecondary">
                        {contact.recipientType}
                      </ThemedText>
                    </View>
                    <Ionicons
                      name={selected ? 'checkmark-circle' : 'ellipse-outline'}
                      size={22}
                      color={selected ? Brand.championshipGold : theme.textSecondary}
                    />
                  </ThemedView>
                </Pressable>
              );
            })}
          </ScrollView>
          <View style={styles.footer}>
            <Button variant="primary" disabled={selectedContactIds.size === 0} onPress={() => setStep('compose')}>
              Next ({selectedContactIds.size} selected)
            </Button>
          </View>
        </>
      )}

      {step === 'compose' && (
        <View style={styles.composeContainer}>
          <ScrollView contentContainerStyle={styles.list}>
            <ThemedText type="small" themeColor="textSecondary">
              Title
            </ThemedText>
            <TextInput
              value={title}
              onChangeText={setTitle}
              placeholder="Conversation title"
              placeholderTextColor={theme.textSecondary}
              style={[styles.input, { color: theme.text, backgroundColor: theme.backgroundElement }]}
            />
            <ThemedText type="small" themeColor="textSecondary" style={styles.messageLabel}>
              Message
            </ThemedText>
            <TextInput
              value={message}
              onChangeText={setMessage}
              placeholder="Type your message…"
              placeholderTextColor={theme.textSecondary}
              style={[styles.input, styles.messageInput, { color: theme.text, backgroundColor: theme.backgroundElement }]}
              multiline
            />
          </ScrollView>
          <View style={styles.footer}>
            <Button
              variant="primary"
              disabled={!title.trim() || !message.trim() || createConversation.isPending}
              onPress={submit}>
              Send
            </Button>
          </View>
        </View>
      )}
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  list: {
    paddingHorizontal: Spacing.four,
    paddingBottom: Spacing.six,
    gap: Spacing.two,
  },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.three,
    borderRadius: Spacing.three,
    padding: Spacing.three,
  },
  rowDisabled: {
    opacity: 0.5,
  },
  rowBody: {
    flex: 1,
    gap: 2,
  },
  footer: {
    paddingHorizontal: Spacing.four,
    paddingBottom: Spacing.four,
    paddingTop: Spacing.two,
  },
  composeContainer: {
    flex: 1,
  },
  input: {
    borderRadius: Spacing.two,
    paddingHorizontal: Spacing.three,
    paddingVertical: Spacing.two,
    marginTop: Spacing.one,
  },
  messageLabel: {
    marginTop: Spacing.three,
  },
  messageInput: {
    minHeight: 120,
    textAlignVertical: 'top',
  },
});
