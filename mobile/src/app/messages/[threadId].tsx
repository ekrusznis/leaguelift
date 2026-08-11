import { useLocalSearchParams } from 'expo-router';
import { useEffect, useState } from 'react';
import { FlatList, KeyboardAvoidingView, Platform, Pressable, StyleSheet, TextInput, View } from 'react-native';
import Ionicons from '@expo/vector-icons/Ionicons';

import { ErrorState } from '@/components/error-state';
import { LoadingState } from '@/components/loading-state';
import { ScreenHeader } from '@/components/screen-header';
import { ThemedText } from '@/components/themed-text';
import { useToast } from '@/components/toast';
import { useAuth } from '@/features/auth/AuthContext';
import { useMarkMessageRead, useMyMessageThreads, useSendReply, useThreadMessages } from '@/features/messaging/api';
import type { MyBroadcastMessageResponse } from '@/features/messaging/types';
import { Brand, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

/** Real thread detail — matches Team Chat in docs/design/mobile_sample_design.png, wired to /me/message-threads/{id}/messages (ADR-102). */
export default function ThreadScreen() {
  const { threadId } = useLocalSearchParams<{ threadId: string }>();
  const theme = useTheme();
  const toast = useToast();
  const { user } = useAuth();
  const threadsQuery = useMyMessageThreads();
  const messagesQuery = useThreadMessages(threadId ?? null);
  const sendReply = useSendReply(threadId ?? '');
  const markRead = useMarkMessageRead();
  const [draft, setDraft] = useState('');

  const threadSummary = threadsQuery.data?.items.find((t) => t.thread.id === threadId);

  useEffect(() => {
    const unread = messagesQuery.data?.items.filter((item) => !item.readAt) ?? [];
    unread.forEach((item) => markRead.mutate(item.message.id));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [messagesQuery.data]);

  async function send() {
    const trimmed = draft.trim();
    if (!trimmed) return;
    setDraft('');
    try {
      await sendReply.mutateAsync(trimmed);
    } catch {
      toast.show('Could not send that message. Please try again.', 'error');
    }
  }

  return (
    <View style={styles.container}>
      <ScreenHeader title={threadSummary?.thread.title ?? 'Conversation'} />

      {messagesQuery.isLoading && <LoadingState label="Loading conversation…" />}
      {messagesQuery.isError && <ErrorState message="Could not load this conversation." onRetry={() => messagesQuery.refetch()} />}

      <KeyboardAvoidingView style={styles.flex} behavior={Platform.OS === 'ios' ? 'padding' : 'height'}>
        <FlatList
          data={messagesQuery.data?.items ?? []}
          keyExtractor={(item) => item.message.id}
          contentContainerStyle={styles.list}
          renderItem={({ item }) => <MessageBubble item={item} isSelf={item.message.senderUserId === user?.id} />}
        />

        {threadSummary?.canReply !== false && (
          <View style={styles.inputRow}>
            <TextInput
              value={draft}
              onChangeText={setDraft}
              placeholder="Type a message…"
              placeholderTextColor={theme.textSecondary}
              style={[styles.input, { color: theme.text, backgroundColor: theme.backgroundElement }]}
              multiline
            />
            <Pressable onPress={send} hitSlop={8} disabled={sendReply.isPending} style={styles.sendButton}>
              <Ionicons name="send" size={20} color={Brand.pureWhite} />
            </Pressable>
          </View>
        )}
      </KeyboardAvoidingView>
    </View>
  );
}

function MessageBubble({ item, isSelf }: { item: MyBroadcastMessageResponse; isSelf: boolean }) {
  const time = new Date(item.message.sentAt).toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit' });
  return (
    <View style={[styles.bubbleRow, isSelf && styles.bubbleRowSelf]}>
      {!isSelf && (
        <ThemedText type="small" themeColor="textSecondary" style={styles.authorLabel}>
          {item.message.senderDisplayName}
        </ThemedText>
      )}
      <View style={[styles.bubble, isSelf ? styles.bubbleSelf : styles.bubbleOther]}>
        <ThemedText style={isSelf ? styles.bubbleTextSelf : undefined}>{item.message.body}</ThemedText>
      </View>
      <ThemedText type="small" themeColor="textSecondary" style={[styles.bubbleMeta, isSelf && styles.bubbleRowSelf]}>
        {time}
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
    maxWidth: '80%',
    alignSelf: 'flex-start',
    gap: 2,
  },
  bubbleRowSelf: {
    alignSelf: 'flex-end',
    alignItems: 'flex-end',
  },
  authorLabel: {
    marginLeft: Spacing.two,
  },
  bubble: {
    borderRadius: Spacing.three,
    paddingHorizontal: Spacing.three,
    paddingVertical: Spacing.two,
  },
  bubbleOther: {
    backgroundColor: '#102B46',
  },
  bubbleSelf: {
    backgroundColor: Brand.championshipGold,
  },
  bubbleTextSelf: {
    color: '#0B1F33',
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
  sendButton: {
    width: 40,
    height: 40,
    borderRadius: 20,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: Brand.championshipGold,
  },
});
