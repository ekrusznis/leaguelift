import { useState } from 'react';
import { Pressable, ScrollView, StyleSheet, View } from 'react-native';

import { Button } from '@/components/button';
import { ConfirmDialog } from '@/components/confirm-dialog';
import { ErrorState } from '@/components/error-state';
import { LoadingState } from '@/components/loading-state';
import { Modal } from '@/components/modal';
import { ScreenHeader } from '@/components/screen-header';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { useToast } from '@/components/toast';
import { useAuth } from '@/features/auth/AuthContext';
import {
  useNotificationPreferences,
  useUpdateNotificationTopic,
  useUpdateSmsConsent,
  useUpdateUserPreferences,
  useUserPreferences,
} from '@/features/settings/api';
import type {
  AppearancePreference,
  NotificationPreferenceState,
  NotificationTopic,
  NotificationTopicPreference,
} from '@/features/settings/types';
import { Brand, Spacing } from '@/constants/theme';

const APPEARANCE_OPTIONS: { value: AppearancePreference; label: string }[] = [
  { value: 'SYSTEM', label: 'System' },
  { value: 'LIGHT', label: 'Light' },
  { value: 'DARK', label: 'Dark' },
];

const TOPIC_LABELS: Record<NotificationTopic, string> = {
  EVENTS_SCHEDULE: 'Events & schedule',
  RSVP: 'RSVP',
  FEES_PAYMENTS: 'Fees & payments',
  DOCUMENTS_ELIGIBILITY: 'Documents & eligibility',
  ANNOUNCEMENTS: 'Announcements',
  FUNDRAISING: 'Fundraising',
  SWAG_SHOP_ORDERS: 'Swag Shop & orders',
  MESSAGES: 'Messages',
  SUPPORT: 'Support',
};

const STATE_LABEL: Record<NotificationPreferenceState, string> = {
  DEFAULT: 'Default',
  ENABLED: 'On',
  DISABLED: 'Off',
};

/** Real /me/preferences + /me/notification-preferences (ADR-102), same endpoints as frontend/src/features/settings. */
export default function SettingsScreen() {
  const { user, logout } = useAuth();
  const toast = useToast();
  const [logoutConfirmOpen, setLogoutConfirmOpen] = useState(false);
  const [editingTopic, setEditingTopic] = useState<NotificationTopicPreference | null>(null);

  const preferences = useUserPreferences();
  const updatePreferences = useUpdateUserPreferences();
  const notifications = useNotificationPreferences();
  const updateTopic = useUpdateNotificationTopic();
  const updateSmsConsent = useUpdateSmsConsent();

  function selectAppearance(appearance: AppearancePreference) {
    if (appearance === preferences.data?.appearance) return;
    updatePreferences.mutate(
      { appearance },
      {
        onSuccess: () => toast.show('Appearance saved.', 'success'),
        onError: () => toast.show('Could not save appearance.', 'error'),
      },
    );
  }

  function saveTopicChannel(channel: 'inApp' | 'email' | 'sms', state: NotificationPreferenceState) {
    if (!editingTopic) return;
    const next = { ...editingTopic, [channel]: state };
    setEditingTopic(next);
    updateTopic.mutate(
      {
        topic: editingTopic.topic,
        request: { inApp: next.inApp, email: next.email, sms: next.sms },
      },
      { onError: () => toast.show('Could not save that notification preference.', 'error') },
    );
  }

  return (
    <ThemedView style={styles.container}>
      <ScreenHeader title="Settings" />

      <ScrollView contentContainerStyle={styles.scrollContent}>
      <View style={styles.profileRow}>
        <ThemedView type="backgroundSelected" style={styles.avatar}>
          <ThemedText type="smallBold">{(user?.displayName ?? '?').slice(0, 2).toUpperCase()}</ThemedText>
        </ThemedView>
        <View>
          <ThemedText type="smallBold">{user?.displayName}</ThemedText>
          <ThemedText type="small" themeColor="textSecondary">
            {user?.email}
          </ThemedText>
        </View>
      </View>

      <View style={styles.section}>
        <ThemedText type="smallBold">Appearance</ThemedText>
        {preferences.isPending && <LoadingState label="Loading…" />}
        {preferences.isError && <ErrorState message="Could not load appearance." onRetry={() => preferences.refetch()} />}
        {preferences.data && (
          <View style={styles.optionRow}>
            {APPEARANCE_OPTIONS.map((option) => {
              const selected = preferences.data.appearance === option.value;
              return (
                <Pressable
                  key={option.value}
                  onPress={() => selectAppearance(option.value)}
                  style={[styles.optionChip, selected && { backgroundColor: Brand.championshipGold }]}>
                  <ThemedText type="small" style={selected ? styles.optionChipTextSelected : undefined}>
                    {option.label}
                  </ThemedText>
                </Pressable>
              );
            })}
          </View>
        )}
      </View>

      <View style={styles.section}>
        <ThemedText type="smallBold">Notifications</ThemedText>
        {notifications.isPending && <LoadingState label="Loading…" />}
        {notifications.isError && <ErrorState message="Could not load notification preferences." onRetry={() => notifications.refetch()} />}
        {notifications.data && (
          <>
            <Pressable
              style={styles.smsRow}
              onPress={() => updateSmsConsent.mutate({ consented: !notifications.data!.smsConsent })}>
              <ThemedText style={styles.smsLabel}>Allow optional SMS notifications</ThemedText>
              <View style={[styles.toggle, notifications.data.smsConsent && styles.toggleOn]}>
                <View style={[styles.toggleKnob, notifications.data.smsConsent && styles.toggleKnobOn]} />
              </View>
            </Pressable>
            {notifications.data.topics.map((topic) => (
              <Pressable key={topic.topic} onPress={() => setEditingTopic(topic)}>
                <ThemedView type="backgroundElement" style={styles.topicRow}>
                  <ThemedText style={styles.topicLabel}>{TOPIC_LABELS[topic.topic]}</ThemedText>
                  <ThemedText type="small" themeColor="textSecondary">
                    {STATE_LABEL[topic.inApp]} · {STATE_LABEL[topic.email]} · {STATE_LABEL[topic.sms]}
                  </ThemedText>
                </ThemedView>
              </Pressable>
            ))}
          </>
        )}
      </View>

      <View style={styles.section}>
        <Button variant="secondary" onPress={() => setLogoutConfirmOpen(true)}>
          Log Out
        </Button>
      </View>
      </ScrollView>

      <Modal visible={!!editingTopic} onClose={() => setEditingTopic(null)}>
        {editingTopic && (
          <>
            <ThemedText type="smallBold" style={styles.pickerTitle}>
              {TOPIC_LABELS[editingTopic.topic]}
            </ThemedText>
            {(['inApp', 'email', 'sms'] as const).map((channel) => (
              <View key={channel} style={styles.channelRow}>
                <ThemedText type="small" themeColor="textSecondary" style={styles.channelLabel}>
                  {channel === 'inApp' ? 'In-app' : channel === 'email' ? 'Email' : 'SMS'}
                </ThemedText>
                <View style={styles.optionRow}>
                  {(['DEFAULT', 'ENABLED', 'DISABLED'] as const).map((state) => {
                    const selected = editingTopic[channel] === state;
                    return (
                      <Pressable
                        key={state}
                        onPress={() => saveTopicChannel(channel, state)}
                        style={[styles.optionChip, selected && { backgroundColor: Brand.championshipGold }]}>
                        <ThemedText type="small" style={selected ? styles.optionChipTextSelected : undefined}>
                          {STATE_LABEL[state]}
                        </ThemedText>
                      </Pressable>
                    );
                  })}
                </View>
              </View>
            ))}
          </>
        )}
      </Modal>

      <ConfirmDialog
        visible={logoutConfirmOpen}
        title="Log out?"
        message="You'll need to sign in again to use Rally26."
        confirmLabel="Log Out"
        destructive
        onConfirm={() => {
          setLogoutConfirmOpen(false);
          void logout();
        }}
        onCancel={() => setLogoutConfirmOpen(false)}
      />
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  scrollContent: {
    paddingBottom: Spacing.six,
  },
  profileRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.three,
    paddingHorizontal: Spacing.four,
    paddingBottom: Spacing.four,
  },
  avatar: {
    width: 48,
    height: 48,
    borderRadius: 24,
    alignItems: 'center',
    justifyContent: 'center',
  },
  section: {
    paddingHorizontal: Spacing.four,
    marginBottom: Spacing.five,
    gap: Spacing.two,
  },
  optionRow: {
    flexDirection: 'row',
    gap: Spacing.two,
    flexWrap: 'wrap',
  },
  optionChip: {
    paddingHorizontal: Spacing.three,
    paddingVertical: Spacing.one,
    borderRadius: Spacing.four,
    backgroundColor: '#102B46',
  },
  optionChipTextSelected: {
    color: '#0B1F33',
  },
  smsRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingVertical: Spacing.two,
  },
  smsLabel: {
    flex: 1,
    marginRight: Spacing.two,
  },
  toggle: {
    width: 44,
    height: 26,
    borderRadius: 13,
    backgroundColor: '#173B5C',
    padding: 3,
  },
  toggleOn: {
    backgroundColor: Brand.victoryGreen,
  },
  toggleKnob: {
    width: 20,
    height: 20,
    borderRadius: 10,
    backgroundColor: Brand.pureWhite,
  },
  toggleKnobOn: {
    marginLeft: 18,
  },
  topicRow: {
    borderRadius: Spacing.three,
    padding: Spacing.three,
    gap: 2,
  },
  topicLabel: {
    marginBottom: 2,
  },
  pickerTitle: {
    marginBottom: Spacing.three,
  },
  channelRow: {
    marginBottom: Spacing.three,
    gap: Spacing.one,
  },
  channelLabel: {
    marginBottom: 2,
  },
});
