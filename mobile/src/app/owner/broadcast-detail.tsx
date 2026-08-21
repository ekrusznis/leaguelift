import { useLocalSearchParams } from 'expo-router';
import { useState } from 'react';
import { FlatList, KeyboardAvoidingView, Platform, Pressable, StyleSheet, TextInput, View } from 'react-native';
import Ionicons from '@expo/vector-icons/Ionicons';

import { ErrorState } from '@/components/error-state';
import { LoadingState } from '@/components/loading-state';
import { ScreenHeader } from '@/components/screen-header';
import { ThemedText } from '@/components/themed-text';
import { useToast } from '@/components/toast';
import { useDashboardContext } from '@/features/dashboard/api';
import { useManagedThreadMessages, useManagedThreads, useSendBroadcastMessage } from '@/features/messaging/api';
import type { BroadcastMessageResponse } from '@/features/messaging/types';
import { Brand, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

/**
 * Org-wide message thread detail (oversight). For a real `BROADCAST` thread, Owner can
 * send an update the same way as before. For a `CONVERSATION`/`ATHLETE_CONVERSATION`
 * thread this is deliberately read-only — Owner can see the real content for youth-safety
 * oversight (same `listMessagesForManagement` read path, gated on manager role not
 * thread membership), but does not get a composer to inject messages into a
 * conversation between other people; that's a materially different, bigger decision
 * than "can see the contents" and isn't built here.
 */
export default function OwnerBroadcastDetailScreen() {
  const { threadId } = useLocalSearchParams<{ threadId: string }>();
  const theme = useTheme();
  const toast = useToast();
  const dashboardContext = useDashboardContext(true);
  const organizationId = dashboardContext.data?.organizationId ?? null;
  const threadsQuery = useManagedThreads(organizationId);
  const messagesQuery = useManagedThreadMessages(organizationId, threadId ?? null);
  const sendMessage = useSendBroadcastMessage(organizationId, threadId ?? '');
  const [draft, setDraft] = useState('');

  const thread = threadsQuery.data?.items.find((t) => t.id === threadId);
  const isBroadcast = thread?.threadType === 'BROADCAST';

  async function send() {
    const trimmed = draft.trim();
    if (!trimmed) return;
    setDraft('');
    try {
      await sendMessage.mutateAsync(trimmed);
    } catch {
      toast.show('Could not send that message. Please try again.', 'error');
    }
  }

  return (
    <View style={styles.container}>
      <ScreenHeader title={thread?.title ?? 'Message thread'} />

      {messagesQuery.isLoading && <LoadingState label="Loading messages…" />}
      {messagesQuery.isError && <ErrorState message="Could not load this thread." onRetry={() => messagesQuery.refetch()} />}

      <KeyboardAvoidingView style={styles.flex} behavior={Platform.OS === 'ios' ? 'padding' : 'height'}>
        <FlatList
          data={messagesQuery.data?.items ?? []}
          keyExtractor={(item) => item.id}
          contentContainerStyle={styles.list}
          renderItem={({ item }) => <MessageBubble item={item} />}
        />

        {isBroadcast ? (
          <View style={styles.inputRow}>
            <TextInput
              value={draft}
              onChangeText={setDraft}
              placeholder="Send a message to this thread's audience…"
              placeholderTextColor={theme.textSecondary}
              style={[styles.input, { color: theme.text, backgroundColor: theme.backgroundElement }]}
              multiline
            />
            <Pressable onPress={send} hitSlop={8} disabled={sendMessage.isPending} style={styles.sendButton}>
              <Ionicons name="send" size={20} color={Brand.pureWhite} />
            </Pressable>
          </View>
        ) : (
          <View style={[styles.readOnlyBanner, { backgroundColor: theme.backgroundElement }]}>
            <Ionicons name="shield-checkmark-outline" size={16} color={theme.textSecondary} />
            <ThemedText type="small" themeColor="textSecondary" style={styles.readOnlyText}>
              Viewing for oversight — replying here isn’t available for a conversation between other people.
            </ThemedText>
          </View>
        )}
      </KeyboardAvoidingView>
    </View>
  );
}

function MessageBubble({ item }: { item: BroadcastMessageResponse }) {
  const time = new Date(item.sentAt).toLocaleString();
  return (
    <View style={styles.bubbleRow}>
      <ThemedText type="small" themeColor="textSecondary" style={styles.authorLabel}>
        {item.senderDisplayName}
      </ThemedText>
      <View style={styles.bubble}>
        <ThemedText>{item.body}</ThemedText>
      </View>
      <ThemedText type="small" themeColor="textSecondary" style={styles.bubbleMeta}>
        {time} · {item.recipientCount} recipients
      </ThemedText>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: Brand.navy,
  },
  flex: {
    flex: 1,
  },
  list: {
    paddingHorizontal: Spacing.four,
    paddingVertical: Spacing.three,
    gap: Spacing.three,
  },
  bubbleRow: {
    maxWidth: '90%',
    gap: 2,
  },
  authorLabel: {
    marginLeft: Spacing.two,
  },
  bubble: {
    borderRadius: Spacing.three,
    paddingHorizontal: Spacing.three,
    paddingVertical: Spacing.two,
    backgroundColor: '#102B46',
  },
  bubbleMeta: {
    marginHorizontal: Spacing.two,
  },
  inputRow: {
    flexDirection: 'row',
    alignItems: 'flex-end',
    gap: Spacing.two,
    paddingHorizontal: Spacing.four,
    paddingVertical: Spacing.two,
  },
  input: {
    flex: 1,
    borderRadius: Spacing.four,
    paddingHorizontal: Spacing.three,
    paddingVertical: Spacing.two,
    maxHeight: 100,
  },
  readOnlyBanner: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: Spacing.two,
    marginHorizontal: Spacing.four,
    marginBottom: Spacing.three,
    padding: Spacing.three,
    borderRadius: Spacing.three,
  },
  readOnlyText: {
    flex: 1,
  },
  sendButton: {
    width: 40,
    height: 40,
    borderRadius: 20,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: Brand.championshipGold,
  },
});
