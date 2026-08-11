import Ionicons from '@expo/vector-icons/Ionicons';
import { router, useLocalSearchParams } from 'expo-router';
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
import { useDashboardContext } from '@/features/dashboard/api';
import { useCreateBroadcastThread, useCreateConversation, useTeamContacts } from '@/features/messaging/api';
import type { MessageThreadAudience } from '@/features/messaging/types';
import { Brand, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { apiFetch } from '@/lib/apiClient';
import { generateIdempotencyKey } from '@/lib/idempotency';

type AudienceMode = 'ALL' | 'GUARDIANS' | 'SELECTED';
type Step = 'audience' | 'contacts' | 'compose';

const AUDIENCE_OPTIONS: { value: AudienceMode; label: string; description: string }[] = [
  { value: 'ALL', label: 'Full Team', description: 'Every staff member, guardian, and athlete on the team' },
  { value: 'GUARDIANS', label: 'Parents Only', description: "Every guardian on the team's roster" },
  { value: 'SELECTED', label: 'Select Specific People', description: 'Pick individual players and/or their parents' },
];

/** Team message compose — Full Team/Parents Only reuse the broadcast-thread endpoints (team-scoped instead of org-scoped, ADR-107); Select Specific People uses the conversation endpoint with real roster contacts. */
export default function MessageComposeScreen() {
  const { teamId } = useLocalSearchParams<{ teamId: string }>();
  const theme = useTheme();
  const toast = useToast();
  const dashboardContext = useDashboardContext(true);
  const organizationId = dashboardContext.data?.organizationId ?? null;

  const [step, setStep] = useState<Step>('audience');
  const [audience, setAudience] = useState<AudienceMode | null>(null);
  const [selectedContactIds, setSelectedContactIds] = useState<Set<string>>(new Set());
  const [title, setTitle] = useState('');
  const [message, setMessage] = useState('');

  const contactsQuery = useTeamContacts(organizationId, step === 'contacts' ? teamId : null);
  const createThread = useCreateBroadcastThread(organizationId);
  const createConversation = useCreateConversation(organizationId);

  function chooseAudience(mode: AudienceMode) {
    setAudience(mode);
    setStep(mode === 'SELECTED' ? 'contacts' : 'compose');
  }

  function toggleContact(userId: string) {
    setSelectedContactIds((prev) => {
      const next = new Set(prev);
      if (next.has(userId)) next.delete(userId);
      else next.add(userId);
      return next;
    });
  }

  async function submit() {
    if (!audience || !title.trim() || !message.trim()) return;
    try {
      if (audience === 'SELECTED') {
        if (selectedContactIds.size === 0) return;
        const thread = await createConversation.mutateAsync({
          teamId,
          idempotencyKey: generateIdempotencyKey(),
          title: title.trim(),
          targetUserIds: [...selectedContactIds],
          initialMessageIdempotencyKey: generateIdempotencyKey(),
          initialMessage: message.trim(),
        });
        router.replace({ pathname: '/messages/[threadId]', params: { threadId: thread.id } });
      } else {
        const thread = await createThread.mutateAsync({
          scopeType: 'TEAM',
          scopeId: teamId,
          idempotencyKey: generateIdempotencyKey(),
          title: title.trim(),
          audience: audience as MessageThreadAudience,
        });
        await apiFetch(`/organizations/${organizationId}/message-threads/${thread.id}/messages`, {
          method: 'POST',
          body: { idempotencyKey: generateIdempotencyKey(), body: message.trim() },
        });
        router.replace({ pathname: '/messages/[threadId]', params: { threadId: thread.id } });
      }
    } catch {
      toast.show('Could not send that message. Please try again.', 'error');
    }
  }

  const sending = createThread.isPending || createConversation.isPending;

  return (
    <ThemedView style={styles.container}>
      <ScreenHeader
        title={step === 'audience' ? 'New Message' : step === 'contacts' ? 'Select Recipients' : 'Message'}
      />

      {step === 'audience' && (
        <ScrollView contentContainerStyle={styles.list}>
          {AUDIENCE_OPTIONS.map((option) => (
            <Pressable key={option.value} onPress={() => chooseAudience(option.value)}>
              <ThemedView type="backgroundElement" style={styles.row}>
                <View style={styles.rowBody}>
                  <ThemedText type="smallBold">{option.label}</ThemedText>
                  <ThemedText type="small" themeColor="textSecondary">
                    {option.description}
                  </ThemedText>
                </View>
                <Ionicons name="chevron-forward" size={18} color={theme.textSecondary} />
              </ThemedView>
            </Pressable>
          ))}
        </ScrollView>
      )}

      {step === 'contacts' && (
        <>
          <ScrollView contentContainerStyle={styles.list}>
            {contactsQuery.isLoading && <LoadingState label="Loading roster…" />}
            {contactsQuery.isError && <ErrorState message="Could not load contacts." onRetry={() => contactsQuery.refetch()} />}
            {contactsQuery.data && contactsQuery.data.length === 0 && (
              <EmptyState title="No contacts available" description="No active players or guardians on this team yet." />
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
              placeholder="Message title"
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
            <Button variant="primary" disabled={!title.trim() || !message.trim() || sending} onPress={submit}>
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
