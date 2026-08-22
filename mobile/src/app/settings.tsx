import * as WebBrowser from 'expo-web-browser';
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
import { useDashboardContext } from '@/features/dashboard/api';
import {
  useCancelAccountDeletion,
  useNotificationPreferences,
  usePendingAccountDeletion,
  useRequestAccountDeletion,
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
import { useDisconnectSocialConnection, useSocialCatalog, useStartSocialAuthorization } from '@/features/social/api';
import type { SocialCatalogItem } from '@/features/social/types';
import { Brand, Spacing } from '@/constants/theme';

const SOCIAL_DISPLAY_NAME: Record<string, string> = {
  INSTAGRAM: 'Instagram',
  FACEBOOK: 'Facebook',
  X: 'X',
};

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
  const [deleteConfirmOpen, setDeleteConfirmOpen] = useState(false);
  const [connectingProvider, setConnectingProvider] = useState<string | null>(null);
  const [disconnectTarget, setDisconnectTarget] = useState<SocialCatalogItem | null>(null);

  const preferences = useUserPreferences();
  const updatePreferences = useUpdateUserPreferences();
  const notifications = useNotificationPreferences();
  const updateTopic = useUpdateNotificationTopic();
  const updateSmsConsent = useUpdateSmsConsent();
  const pendingDeletion = usePendingAccountDeletion();
  const requestDeletion = useRequestAccountDeletion();
  const cancelDeletion = useCancelAccountDeletion();

  // Rally26 serves minors — athlete accounts don't get connected-social-account
  // controls at all (brief §10), not just a rejected request. The backend also
  // enforces this (IntegrationOAuthService.requireNotAthlete), this is purely so an
  // athlete never sees a control that would fail.
  const dashboardContext = useDashboardContext(true);
  const isAthlete = dashboardContext.data?.role === 'ATHLETE';
  const socialCatalog = useSocialCatalog(!isAthlete);
  const startSocialAuthorization = useStartSocialAuthorization();
  const disconnectSocialConnection = useDisconnectSocialConnection();

  async function connectSocialProvider(provider: SocialCatalogItem['provider']) {
    setConnectingProvider(provider);
    try {
      const result = await startSocialAuthorization.mutateAsync(provider);
      await WebBrowser.openBrowserAsync(result.authorizationUrl);
      // openBrowserAsync only resolves once the user dismisses the browser (after
      // finishing or abandoning the flow on the provider's own site) — refetching
      // here, not on a timer, is what picks up the finished connection.
      await socialCatalog.refetch();
    } catch {
      toast.show(`Could not start connecting ${SOCIAL_DISPLAY_NAME[provider] ?? provider}.`, 'error');
    } finally {
      setConnectingProvider(null);
    }
  }

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
        <ThemedText type="small" themeColor="textSecondary">
          Default means something different per channel: in-app stays on, email follows your household&rsquo;s email-reminder setting, and SMS is always off until
          you grant consent below and set that topic to On.
        </ThemedText>
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

      {!isAthlete && (
        <View style={styles.section}>
          <ThemedText type="smallBold">Connected Social Accounts</ThemedText>
          <ThemedText type="small" themeColor="textSecondary">
            Share Rally26 events, fundraisers, approved media, sponsors, and team gear using your connected accounts.
          </ThemedText>
          {socialCatalog.isPending && <LoadingState label="Loading…" />}
          {socialCatalog.isError && <ErrorState message="Could not load connected accounts." onRetry={() => socialCatalog.refetch()} />}
          {socialCatalog.data?.map((item) => {
            const connected = item.connection?.status === 'CONNECTED';
            const degraded = item.connection?.status === 'DEGRADED';
            return (
              <ThemedView key={item.provider} type="backgroundElement" style={styles.topicRow}>
                <View style={styles.socialRow}>
                  <View style={styles.socialInfo}>
                    <ThemedText style={styles.topicLabel}>{item.displayName}</ThemedText>
                    <ThemedText type="small" themeColor="textSecondary">
                      {connected
                        ? (item.connection?.externalAccountName ?? 'Connected')
                        : degraded
                          ? 'Needs attention — reconnect'
                          : 'Not connected'}
                    </ThemedText>
                  </View>
                  {connected || degraded ? (
                    <Button
                      variant="secondary"
                      onPress={() => setDisconnectTarget(item)}
                      disabled={disconnectSocialConnection.isPending}>
                      Manage
                    </Button>
                  ) : (
                    <Button
                      variant="secondary"
                      onPress={() => void connectSocialProvider(item.provider)}
                      disabled={connectingProvider === item.provider}>
                      {connectingProvider === item.provider ? 'Connecting…' : 'Connect'}
                    </Button>
                  )}
                </View>
              </ThemedView>
            );
          })}
        </View>
      )}

      <View style={styles.section}>
        <ThemedText type="smallBold" style={{ color: Brand.errorRed }}>
          Danger zone
        </ThemedText>
        {pendingDeletion.data ? (
          <>
            <ThemedText type="small" themeColor="textSecondary">
              Your account is scheduled for deletion on {new Date(pendingDeletion.data.scheduledFor).toLocaleDateString()}. Export
              anything you need before then — after that date it can&rsquo;t be undone.
            </ThemedText>
            <Button variant="secondary" onPress={() => void cancelDeletion.mutateAsync()} disabled={cancelDeletion.isPending}>
              {cancelDeletion.isPending ? 'Canceling…' : 'Cancel deletion'}
            </Button>
          </>
        ) : (
          <Button variant="secondary" onPress={() => setDeleteConfirmOpen(true)}>
            Delete my account
          </Button>
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
                  {channel === 'inApp' ? 'In-app' : channel === 'email' ? 'Email (Default follows household setting)' : 'SMS (Default is always off)'}
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

      <ConfirmDialog
        visible={!!disconnectTarget}
        title={`Disconnect ${disconnectTarget ? (SOCIAL_DISPLAY_NAME[disconnectTarget.provider] ?? disconnectTarget.provider) : ''}?`}
        message="Rally26 will no longer be able to share directly to this account. You can reconnect it at any time."
        confirmLabel="Disconnect"
        destructive
        onConfirm={() => {
          const target = disconnectTarget;
          setDisconnectTarget(null);
          if (!target?.connection) return;
          disconnectSocialConnection.mutate(target.connection.id, {
            onError: () => toast.show('Could not disconnect that account.', 'error'),
          });
        }}
        onCancel={() => setDisconnectTarget(null)}
      />

      <ConfirmDialog
        visible={deleteConfirmOpen}
        title="Delete your account?"
        message="You'll have 7 days to export any data you need. After that, your account is permanently deleted and you're removed from every team and organization you belong to. This can't be undone. If you own an organization, transfer ownership or close it first."
        confirmLabel="Delete my account"
        destructive
        onConfirm={() => {
          setDeleteConfirmOpen(false);
          requestDeletion.mutate(undefined, {
            onError: () =>
              toast.show('Could not start account deletion. If you own an organization, transfer ownership or close it first.', 'error'),
          });
        }}
        onCancel={() => setDeleteConfirmOpen(false)}
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
  socialRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: Spacing.three,
  },
  socialInfo: {
    flex: 1,
    gap: 2,
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
