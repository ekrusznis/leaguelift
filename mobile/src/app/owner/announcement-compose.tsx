import { router } from 'expo-router';
import { useState } from 'react';
import { KeyboardAvoidingView, Platform, Pressable, ScrollView, StyleSheet, TextInput, View } from 'react-native';
import Ionicons from '@expo/vector-icons/Ionicons';

import { Button } from '@/components/button';
import { ScreenHeader } from '@/components/screen-header';
import { ThemedText } from '@/components/themed-text';
import { useToast } from '@/components/toast';
import { useCreateAnnouncementDraft } from '@/features/announcements/api';
import type { AnnouncementAudience } from '@/features/announcements/types';
import { useDashboardContext } from '@/features/dashboard/api';
import { Brand, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { generateIdempotencyKey } from '@/lib/idempotency';

const AUDIENCES: { value: AnnouncementAudience; label: string }[] = [
  { value: 'ALL', label: 'Everyone' },
  { value: 'STAFF', label: 'Staff' },
  { value: 'GUARDIANS', label: 'Guardians' },
  { value: 'ATHLETES', label: 'Athletes' },
];

/** Org-scoped announcement compose — creates a DRAFT, publish happens from the list screen (ADR-105). Team/tournament scope deferred to a later slice. */
export default function OwnerAnnouncementComposeScreen() {
  const theme = useTheme();
  const toast = useToast();
  const dashboardContext = useDashboardContext(true);
  const organizationId = dashboardContext.data?.organizationId ?? null;
  const createDraft = useCreateAnnouncementDraft(organizationId);

  const [audience, setAudience] = useState<AnnouncementAudience>('ALL');
  const [title, setTitle] = useState('');
  const [body, setBody] = useState('');

  async function save() {
    if (!title.trim() || !body.trim()) return;
    try {
      await createDraft.mutateAsync({
        scopeType: 'ORGANIZATION',
        idempotencyKey: generateIdempotencyKey(),
        title: title.trim(),
        body: body.trim(),
        audience,
      });
      toast.show('Draft saved. Publish it from the list.', 'success');
      router.back();
    } catch {
      toast.show('Could not save that announcement. Please try again.', 'error');
    }
  }

  return (
    <KeyboardAvoidingView style={styles.container} behavior={Platform.OS === 'ios' ? 'padding' : 'height'}>
      <ScreenHeader title="New Announcement" />
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
          placeholder="Announcement title"
          placeholderTextColor={theme.textSecondary}
          style={[styles.input, { color: theme.text, backgroundColor: theme.backgroundElement }]}
        />

        <ThemedText type="small" themeColor="textSecondary" style={styles.label}>
          Body
        </ThemedText>
        <TextInput
          value={body}
          onChangeText={setBody}
          placeholder="Write your announcement…"
          placeholderTextColor={theme.textSecondary}
          style={[styles.input, styles.bodyInput, { color: theme.text, backgroundColor: theme.backgroundElement }]}
          multiline
        />
      </ScrollView>
      <View style={styles.footer}>
        <Button variant="primary" disabled={!title.trim() || !body.trim() || createDraft.isPending} onPress={save}>
          Save Draft
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
  bodyInput: {
    minHeight: 140,
    textAlignVertical: 'top',
  },
  footer: {
    paddingHorizontal: Spacing.four,
    paddingBottom: Spacing.four,
    paddingTop: Spacing.two,
  },
});
