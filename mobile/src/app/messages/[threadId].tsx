import { useLocalSearchParams } from 'expo-router';
import { useEffect, useMemo, useState } from 'react';
import { FlatList, KeyboardAvoidingView, Platform, Pressable, RefreshControl, StyleSheet, TextInput, View } from 'react-native';
import Ionicons from '@expo/vector-icons/Ionicons';

import { ErrorState } from '@/components/error-state';
import { LoadingState } from '@/components/loading-state';
import { ScreenHeader } from '@/components/screen-header';
import { ThemedText } from '@/components/themed-text';
import { useToast } from '@/components/toast';
import { useAuth } from '@/features/auth/AuthContext';
import { useMarkMessageRead, useMyMessageThreads, useSendReply, useThreadMessages } from '@/features/messaging/api';
import { dateSeparatorLabel, isDifferentDay, shouldGroupWithPrevious } from '@/features/messaging/dateFormat';
import type { MyBroadcastMessageResponse, MyMessageThreadResponse } from '@/features/messaging/types';
import { Brand, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

/** A locally-tracked send attempt, rendered inline until the real message arrives via refetch. */
interface PendingMessage {
  localId: string;
  body: string;
  status: 'sending' | 'failed';
}

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
  const [pending, setPending] = useState<PendingMessage[]>([]);

  const threadSummary = threadsQuery.data?.items.find((t) => t.thread.id === threadId);
  const messages = useMemo(() => messagesQuery.data?.items ?? [], [messagesQuery.data]);

  useEffect(() => {
    const unread = messages.filter((item) => !item.readAt);
    unread.forEach((item) => markRead.mutate(item.message.id));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [messagesQuery.data]);

  async function send() {
    const trimmed = draft.trim();
    if (!trimmed) return;
    const localId = `${Date.now()}-${Math.random()}`;
    setDraft('');
    setPending((current) => [...current, { localId, body: trimmed, status: 'sending' }]);
    try {
      await sendReply.mutateAsync(trimmed);
      setPending((current) => current.filter((p) => p.localId !== localId));
      await messagesQuery.refetch();
    } catch {
      setPending((current) => current.map((p) => (p.localId === localId ? { ...p, status: 'failed' } : p)));
      toast.show('Could not send that message. Tap to retry.', 'error');
    }
  }

  async function retry(item: PendingMessage) {
    setPending((current) => current.map((p) => (p.localId === item.localId ? { ...p, status: 'sending' } : p)));
    try {
      await sendReply.mutateAsync(item.body);
      setPending((current) => current.filter((p) => p.localId !== item.localId));
      await messagesQuery.refetch();
    } catch {
      setPending((current) => current.map((p) => (p.localId === item.localId ? { ...p, status: 'failed' } : p)));
      toast.show('Still could not send that message.', 'error');
    }
  }

  const rows = useMemo(() => buildRows(messages, pending, user?.id), [messages, pending, user?.id]);
  const readOnlyReason = readOnlyBanner(threadSummary);

  return (
    <View style={styles.container}>
      <ScreenHeader title={threadSummary?.thread.title ?? 'Conversation'} />

      {messagesQuery.isLoading && <LoadingState label="Loading conversation…" />}
      {messagesQuery.isError && <ErrorState message="Could not load this conversation." onRetry={() => messagesQuery.refetch()} />}

      <KeyboardAvoidingView style={styles.flex} behavior={Platform.OS === 'ios' ? 'padding' : 'height'}>
        <FlatList
          data={rows}
          keyExtractor={(row) => (row.kind === 'separator' ? `sep-${row.label}` : row.kind === 'pending' ? row.item.localId : row.item.message.id)}
          contentContainerStyle={styles.list}
          refreshControl={<RefreshControl refreshing={messagesQuery.isFetching && !messagesQuery.isLoading} onRefresh={() => messagesQuery.refetch()} />}
          renderItem={({ item: row }) => {
            if (row.kind === 'separator') {
              return (
                <View style={styles.separatorRow}>
                  <ThemedText type="small" themeColor="textSecondary" style={styles.separatorText}>
                    {row.label}
                  </ThemedText>
                </View>
              );
            }
            if (row.kind === 'pending') {
              return <PendingBubble item={row.item} onRetry={() => retry(row.item)} />;
            }
            return <MessageBubble item={row.item} isSelf={row.item.message.senderUserId === user?.id} showSender={row.showSender} />;
          }}
        />

        {readOnlyReason && (
          <View style={[styles.readOnlyBanner, { backgroundColor: theme.backgroundElement }]}>
            <Ionicons name="information-circle-outline" size={16} color={theme.textSecondary} />
            <ThemedText type="small" themeColor="textSecondary" style={styles.readOnlyText}>
              {readOnlyReason}
            </ThemedText>
          </View>
        )}

        {!readOnlyReason && threadSummary?.canReply !== false && (
          <View style={styles.inputRow}>
            <TextInput
              value={draft}
              onChangeText={setDraft}
              placeholder="Type a message…"
              placeholderTextColor={theme.textSecondary}
              style={[styles.input, { color: theme.text, backgroundColor: theme.backgroundElement }]}
              multiline
              accessibilityLabel="Message"
            />
            <Pressable
              onPress={send}
              hitSlop={8}
              disabled={sendReply.isPending || !draft.trim()}
              accessibilityRole="button"
              accessibilityLabel="Send message"
              style={[styles.sendButton, (sendReply.isPending || !draft.trim()) && styles.sendButtonDisabled]}
            >
              <Ionicons name="send" size={20} color={Brand.pureWhite} />
            </Pressable>
          </View>
        )}
      </KeyboardAvoidingView>
    </View>
  );
}

/** Explains why the composer isn't shown, instead of just omitting it with no context. */
function readOnlyBanner(thread: MyMessageThreadResponse | undefined): string | null {
  if (!thread) return null;
  if (thread.thread.safetyLockedAt) {
    return 'This conversation is temporarily locked for a safety review. New messages are paused.';
  }
  if (thread.thread.threadType === 'BROADCAST') {
    return 'Announcements only — replies aren’t enabled for this conversation.';
  }
  if (!thread.canReply) {
    if (thread.accessReason === 'GUARDIAN_VISIBILITY') {
      return 'Guardian visibility — you can read this conversation, but replying is only available to selected participants.';
    }
    return 'You have read-only access to this conversation.';
  }
  return null;
}

type Row =
  | { kind: 'separator'; label: string }
  | { kind: 'message'; item: MyBroadcastMessageResponse; showSender: boolean }
  | { kind: 'pending'; item: PendingMessage };

function buildRows(messages: MyBroadcastMessageResponse[], pending: PendingMessage[], currentUserId: string | undefined): Row[] {
  const rows: Row[] = [];
  let previous: MyBroadcastMessageResponse | undefined;
  for (const item of messages) {
    if (!previous || isDifferentDay(previous.message.sentAt, item.message.sentAt)) {
      rows.push({ kind: 'separator', label: dateSeparatorLabel(item.message.sentAt) });
    }
    const showSender = !shouldGroupWithPrevious(
      { senderUserId: item.message.senderUserId, sentAt: item.message.sentAt },
      previous ? { senderUserId: previous.message.senderUserId, sentAt: previous.message.sentAt } : undefined,
    );
    rows.push({ kind: 'message', item, showSender });
    previous = item;
  }
  // A pending "sending" placeholder whose body already landed as a real message (e.g. a
  // poll/refetch raced ahead of this send's own promise resolving) would otherwise show
  // as a visual duplicate — drop it here rather than via a setState-in-effect.
  const arrived = (item: PendingMessage) =>
    item.status === 'sending' && messages.some((m) => m.message.senderUserId === currentUserId && m.message.body === item.body);
  for (const item of pending) {
    if (arrived(item)) continue;
    rows.push({ kind: 'pending', item });
  }
  return rows;
}

/** Sent (mine, orange) vs. received (blue) bubbles, phone-messaging-style. Read receipts only make sense on my own sent messages — recipientCount excludes the sender (a guardian/coach can never target themselves, see ConversationMemberPolicy/AthleteConversationPolicy), so it's a real per-recipient count, not a guess. */
function ReadReceipt({ message }: { message: MyBroadcastMessageResponse['message'] }) {
  if (message.recipientCount === 0) return null;
  const allRead = message.readRecipientCount >= message.recipientCount;
  return (
    <ThemedText type="small" style={[styles.readReceipt, allRead && styles.readReceiptRead]}>
      {allRead ? '✓✓' : '✓'}
    </ThemedText>
  );
}

function MessageBubble({
  item,
  isSelf,
  showSender,
}: {
  item: MyBroadcastMessageResponse;
  isSelf: boolean;
  showSender: boolean;
}) {
  const time = new Date(item.message.sentAt).toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit' });
  return (
    <View style={[styles.bubbleRow, isSelf && styles.bubbleRowSelf, !showSender && styles.bubbleRowGrouped]}>
      {!isSelf && showSender && (
        <ThemedText type="small" themeColor="textSecondary" style={styles.authorLabel}>
          {item.message.senderDisplayName}
        </ThemedText>
      )}
      <View style={[styles.bubble, isSelf ? styles.bubbleSelf : styles.bubbleOther]}>
        <ThemedText style={isSelf ? styles.bubbleTextSelf : styles.bubbleTextOther}>{item.message.body}</ThemedText>
      </View>
      <View style={[styles.bubbleMetaRow, isSelf && styles.bubbleRowSelf]}>
        <ThemedText type="small" themeColor="textSecondary" style={styles.bubbleMeta}>
          {time}
        </ThemedText>
        {isSelf && <ReadReceipt message={item.message} />}
      </View>
    </View>
  );
}

function PendingBubble({ item, onRetry }: { item: PendingMessage; onRetry: () => void }) {
  const failed = item.status === 'failed';
  return (
    <Pressable onPress={failed ? onRetry : undefined} style={[styles.bubbleRow, styles.bubbleRowSelf]}>
      <View style={[styles.bubble, styles.bubbleSelf, failed && styles.bubbleFailed]}>
        <ThemedText style={styles.bubbleTextSelf}>{item.body}</ThemedText>
      </View>
      <View style={[styles.pendingMeta, styles.bubbleRowSelf]}>
        {item.status === 'sending' && (
          <ThemedText type="small" themeColor="textSecondary">
            Sending…
          </ThemedText>
        )}
        {failed && (
          <>
            <Ionicons name="alert-circle" size={14} color={Brand.errorRed} />
            <ThemedText type="small" style={styles.failedText}>
              Not sent — tap to retry
            </ThemedText>
          </>
        )}
      </View>
    </Pressable>
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
    gap: Spacing.two,
  },
  separatorRow: {
    alignItems: 'center',
    paddingVertical: Spacing.two,
  },
  separatorText: {
    letterSpacing: 1,
  },
  bubbleRow: {
    maxWidth: '80%',
    alignSelf: 'flex-start',
    gap: 2,
    marginTop: Spacing.two,
  },
  bubbleRowGrouped: {
    marginTop: 2,
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
  bubbleSelf: {
    backgroundColor: Brand.championshipGold,
  },
  bubbleOther: {
    backgroundColor: Brand.infoBlue,
  },
  bubbleFailed: {
    opacity: 0.6,
  },
  bubbleTextSelf: {
    color: Brand.navy,
  },
  bubbleTextOther: {
    color: Brand.pureWhite,
  },
  bubbleMetaRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
    marginHorizontal: Spacing.two,
  },
  bubbleMeta: {
    marginHorizontal: 0,
  },
  readReceipt: {
    color: Brand.slateGray,
    fontWeight: '700',
  },
  readReceiptRead: {
    color: Brand.victoryGreen,
  },
  pendingMeta: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
    marginHorizontal: Spacing.two,
  },
  failedText: {
    color: Brand.errorRed,
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
  sendButtonDisabled: {
    opacity: 0.5,
  },
});
