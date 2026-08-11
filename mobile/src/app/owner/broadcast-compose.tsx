import { router } from 'expo-router';
import { useState } from 'react';
import { KeyboardAvoidingView, Platform, Pressable, ScrollView, StyleSheet, TextInput, View } from 'react-native';
import Ionicons from '@expo/vector-icons/Ionicons';

import { Button } from '@/components/button';
import { ScreenHeader } from '@/components/screen-header';
import { ThemedText } from '@/components/themed-text';
import { useToast } from '@/components/toast';
import { useDashboardContext } from '@/features/dashboard/api';
import { useCreateBroadcastThread } from '@/features/messaging/api';
import type { MessageThreadAudience } from '@/features/messaging/types';
import { Brand, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { generateIdempotencyKey } from '@/lib/idempotency';

const AUDIENCES: { value: MessageThreadAudience; label: string }[] = [
  { value: 'ALL', label: 'Everyone' },
  { value: 'STAFF', label: 'Staff' },
  { value: 'GUARDIANS', label: 'Guardians' },
  { value: 'ATHLETES', label: 'Athletes' },
];

/** Org-scoped broadcast thread create — the thread itself has no body; the first message is sent from the follow-on detail screen (ADR-105). */
export default function OwnerBroadcastComposeScreen() {
  const theme = useTheme();
  const toast = useToast();
  const dashboardContext = useDashboardContext(true);
  const organizationId = dashboardContext.data?.organizationId ?? null;
  const createThread = useCreateBroadcastThread(organizationId);

  const [audience, setAudience] = useState<MessageThreadAudience>('ALL');
  const [title, setTitle] = useState('');

  async function create() {
    if (!title.trim()) return;
    try {
      const thread = await createThread.mutateAsync({
        scopeType: 'ORGANIZATION',
        idempotencyKey: generateIdempotencyKey(),
        title: title.trim(),
        audience,
      });
      router.replace({ pathname: '/owner/broadcast-detail', params: { threadId: thread.id } });
    } catch {
      toast.show('Could not create that broadcast. Please try again.', 'error');
    }
  }

  return (
    <KeyboardAvoidingView style={styles.container} behavior={Platform.OS === 'ios' ? 'padding' : 'height'}>
      <ScreenHeader title="New Broadcast" />
      <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
        <ThemedText type="small" themeColor="textSecondary">
          Audience
        </ThemedText>
        <View style={styles.audienceRow}>
          {AUDIENCES.map((option) => {
            const selected = option.value === audience;
            return (
              <Pressable key={option.value} onPress={() => setAudience(option.value)} style={styles.audienceOption}>
                <Ionicons
                  name={selected ? 'radio-button-on' : 'radio-button-off'}
                  size={18}
                  color={selected ? Brand.championshipGold : theme.textSecondary}
                />
                <ThemedText type="small">{option.label}</ThemedText>
              </Pressable>
            );
          })}
        </View>

        <ThemedText type="small" themeColor="textSecondary" style={styles.label}>
          Title
        </ThemedText>
        <TextInput
          value={title}
          onChangeText={setTitle}
          placeholder="Broadcast title"
          placeholderTextColor={theme.textSecondary}
          style={[styles.input, { color: theme.text, backgroundColor: theme.backgroundElement }]}
        />
      </ScrollView>
      <View style={styles.footer}>
        <Button variant="primary" disabled={!title.trim() || createThread.isPending} onPress={create}>
          Next
        </Button>
      </View>
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
  },
  label: {
    marginTop: Spacing.three,
  },
  audienceRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: Spacing.three,
    marginTop: Spacing.one,
  },
  audienceOption: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.one,
  },
  input: {
    borderRadius: Spacing.two,
    paddingHorizontal: Spacing.three,
    paddingVertical: Spacing.two,
    marginTop: Spacing.one,
  },
  footer: {
    paddingHorizontal: Spacing.four,
    paddingBottom: Spacing.four,
    paddingTop: Spacing.two,
  },
});
