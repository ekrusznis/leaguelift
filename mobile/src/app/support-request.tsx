import * as Crypto from 'expo-crypto';
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
import { useCreateSupportCase, useMySupportCases } from '@/features/support/api';
import { SUPPORT_CASE_CATEGORIES, type SupportCase, type SupportCaseCategory } from '@/features/support/types';
import { Brand, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

const STATUS_LABELS: Record<SupportCase['status'], string> = {
  OPEN: 'Open',
  IN_PROGRESS: 'In progress',
  WAITING_ON_CUSTOMER: 'Waiting on you',
  RESOLVED: 'Resolved',
  CLOSED: 'Closed',
};

/**
 * Support ticket creation + "my recent cases" (Phase 37.11, ADR-119) — mirrors
 * frontend/src/features/support/SupportRequestPage.tsx. Simplified relative to web:
 * organizationId auto-attaches from the current dashboard context (if any) rather than
 * an explicit multi-organization picker, since mobile has no equivalent org-switcher UI
 * yet and a user's dashboard context is already the org they're acting in.
 */
export default function SupportRequestScreen() {
  const theme = useTheme();
  const toast = useToast();
  const dashboardContext = useDashboardContext(true);
  const organizationId = dashboardContext.data?.organizationId ?? null;
  const createCase = useCreateSupportCase();
  const casesQuery = useMySupportCases(true);

  const [category, setCategory] = useState<SupportCaseCategory>('OTHER');
  const [subject, setSubject] = useState('');
  const [description, setDescription] = useState('');
  const [idempotencyKey, setIdempotencyKey] = useState(() => Crypto.randomUUID());
  const [submitting, setSubmitting] = useState(false);

  async function onSubmit() {
    if (!subject.trim() || !description.trim()) {
      toast.show('Enter a subject and description.', 'error');
      return;
    }
    setSubmitting(true);
    try {
      await createCase.mutateAsync({ idempotencyKey, organizationId, category, subject: subject.trim(), description: description.trim() });
      setSubject('');
      setDescription('');
      setIdempotencyKey(Crypto.randomUUID());
      toast.show('Support case submitted.', 'success');
    } catch {
      toast.show('Could not submit that. Please try again.', 'error');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <KeyboardAvoidingView style={styles.container} behavior={Platform.OS === 'ios' ? 'padding' : 'height'}>
      <ThemedView style={styles.container}>
        <ScreenHeader title="Contact Support" />
        <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
          <ThemedText type="smallBold">What&rsquo;s this about?</ThemedText>
          <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.categoryRow}>
            {SUPPORT_CASE_CATEGORIES.map((item) => {
              const active = item.value === category;
              return (
                <Pressable key={item.value} onPress={() => setCategory(item.value)}>
                  <ThemedView
                    type={active ? undefined : 'backgroundElement'}
                    style={[styles.chip, active && { backgroundColor: Brand.championshipGold }]}>
                    <ThemedText type="small" themeColor={active ? undefined : 'textSecondary'}>
                      {item.label}
                    </ThemedText>
                  </ThemedView>
                </Pressable>
              );
            })}
          </ScrollView>

          <ThemedText type="smallBold">Subject</ThemedText>
          <TextInput
            value={subject}
            onChangeText={setSubject}
            placeholder="A short summary"
            placeholderTextColor={theme.textSecondary}
            style={[styles.input, { color: theme.text, backgroundColor: theme.backgroundElement }]}
          />

          <ThemedText type="smallBold">Description</ThemedText>
          <TextInput
            value={description}
            onChangeText={setDescription}
            placeholder="What's going on?"
            placeholderTextColor={theme.textSecondary}
            multiline
            numberOfLines={5}
            style={[styles.input, styles.textArea, { color: theme.text, backgroundColor: theme.backgroundElement }]}
          />

          <Button onPress={onSubmit} disabled={submitting} style={styles.submitButton}>
            {submitting ? 'Submitting…' : 'Submit'}
          </Button>

          <View style={styles.recentSection}>
            <ThemedText type="smallBold">My recent cases</ThemedText>
            {casesQuery.isLoading && <LoadingState label="Loading your cases…" />}
            {casesQuery.isError && <ErrorState message="Could not load your cases." onRetry={() => casesQuery.refetch()} />}
            {casesQuery.data && casesQuery.data.items.length === 0 && (
              <ThemedText type="small" themeColor="textSecondary">
                You haven&rsquo;t submitted any support cases yet.
              </ThemedText>
            )}
            {casesQuery.data?.items.slice(0, 5).map((item) => (
              <ThemedView key={item.id} type="backgroundElement" style={styles.caseRow}>
                <ThemedText type="small" style={styles.caseSubject}>
                  {item.subject}
                </ThemedText>
                <ThemedText type="small" themeColor="textSecondary">
                  {STATUS_LABELS[item.status]}
                </ThemedText>
              </ThemedView>
            ))}
          </View>
        </ScrollView>
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
  categoryRow: {
    gap: Spacing.two,
    paddingBottom: Spacing.two,
  },
  chip: {
    paddingHorizontal: Spacing.three,
    paddingVertical: Spacing.one,
    borderRadius: Spacing.four,
  },
  input: {
    minHeight: 48,
    borderRadius: Spacing.two,
    paddingHorizontal: Spacing.three,
    paddingVertical: Spacing.two,
  },
  textArea: {
    minHeight: 100,
    textAlignVertical: 'top',
  },
  submitButton: {
    marginTop: Spacing.two,
    marginBottom: Spacing.four,
  },
  recentSection: {
    gap: Spacing.two,
  },
  caseRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    borderRadius: Spacing.three,
    padding: Spacing.three,
    gap: Spacing.two,
  },
  caseSubject: {
    flex: 1,
  },
});
