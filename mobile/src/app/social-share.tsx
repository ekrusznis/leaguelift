import { router, useLocalSearchParams } from 'expo-router';
import { useEffect, useRef, useState } from 'react';
import { Linking, Pressable, ScrollView, Share, StyleSheet, TextInput, View } from 'react-native';

import { Button } from '@/components/button';
import { ErrorState } from '@/components/error-state';
import { LoadingState } from '@/components/loading-state';
import { ScreenHeader } from '@/components/screen-header';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { useToast } from '@/components/toast';
import { Brand, Spacing } from '@/constants/theme';
import {
  useCreateSocialDraft,
  usePublishSocialDraft,
  useSocialCatalog,
  useUpdateSocialDraftCaption,
} from '@/features/social/api';
import type { SocialDraftSourceType, SocialProvider } from '@/features/social/types';
import { useTheme } from '@/hooks/use-theme';
import { ApiError } from '@/lib/apiClient';

const PROVIDER_LABEL: Record<SocialProvider, string> = {
  INSTAGRAM: 'Instagram',
  FACEBOOK: 'Facebook',
  X: 'X',
};

/** Mirrors backend/src/main/kotlin/com/rally26/social/application/SocialPublishingAdapter.kt's doc comment — there's deliberately no Instagram publishing adapter (it always needs real media), so Instagram always falls back to native share here. */
const DIRECT_PUBLISH_PROVIDERS: SocialProvider[] = ['FACEBOOK', 'X'];

/**
 * Reusable Share Composer (brief §6) — reached from any content type's own "Share to
 * social" entry point (Fundraising today; Events/Sponsorships/Media/Swag Shop once
 * their drafts are wired — see SocialDraftService's doc comment). Always offers native
 * share as a fallback, never a dead end, per the mobile no-broken-UI rule.
 */
export default function SocialShareScreen() {
  const { organizationId = '', sourceType = '', sourceId = '', title = '' } = useLocalSearchParams<{
    organizationId: string;
    sourceType: SocialDraftSourceType;
    sourceId: string;
    title?: string;
  }>();
  const theme = useTheme();
  const toast = useToast();

  const [captionText, setCaptionText] = useState('');
  const [savedCaption, setSavedCaption] = useState('');
  const [selectedProvider, setSelectedProvider] = useState<SocialProvider | null>(null);

  const createDraft = useCreateSocialDraft();
  const updateCaption = useUpdateSocialDraftCaption();
  const publishDraft = usePublishSocialDraft();
  const socialCatalog = useSocialCatalog(true);

  const startedRef = useRef(false);
  useEffect(() => {
    if (startedRef.current || !organizationId || !sourceType || !sourceId) return;
    startedRef.current = true;
    createDraft.mutate(
      { organizationId, sourceType, sourceId },
      {
        onSuccess: (draft) => {
          setCaptionText(draft.caption);
          setSavedCaption(draft.caption);
          setSelectedProvider(draft.allowedProviders[0] ?? null);
        },
      },
    );
  }, [organizationId, sourceType, sourceId]);

  const draft = createDraft.data;
  const captionDirty = captionText !== savedCaption;
  const connectionForProvider = (provider: SocialProvider) =>
    socialCatalog.data?.find((item) => item.provider === provider)?.connection ?? null;

  async function nativeShare() {
    if (!draft) return;
    try {
      await Share.share({ message: `${captionText}`, url: draft.publicUrl });
    } catch {
      toast.show('Could not open sharing.', 'error');
    }
  }

  async function publish() {
    if (!draft || !selectedProvider) return;
    try {
      if (captionDirty) {
        await updateCaption.mutateAsync({ draftId: draft.id, caption: captionText });
        setSavedCaption(captionText);
      }
      const result = await publishDraft.mutateAsync({ draftId: draft.id, provider: selectedProvider });
      if (result.status === 'PUBLISHED') {
        toast.show(`Posted to ${PROVIDER_LABEL[selectedProvider]}.`, 'success');
        router.back();
      } else {
        toast.show(result.failureMessageSafe ?? 'This post could not be published.', 'error');
      }
    } catch (error) {
      const message = error instanceof ApiError ? error.body.message : 'This post could not be published.';
      toast.show(message, 'error');
    }
  }

  if (createDraft.isPending || (!draft && !createDraft.isError)) {
    return (
      <>
        <ScreenHeader title="Share" />
        <LoadingState label="Preparing post…" />
      </>
    );
  }

  if (createDraft.isError || !draft) {
    const message = createDraft.error instanceof ApiError ? createDraft.error.body.message : 'Could not prepare this post to share.';
    return (
      <>
        <ScreenHeader title="Share" />
        <ErrorState message={message} onRetry={() => router.back()} />
      </>
    );
  }

  const connection = selectedProvider ? connectionForProvider(selectedProvider) : null;
  const isConnected = connection?.status === 'CONNECTED';
  const supportsDirectPublish = selectedProvider ? DIRECT_PUBLISH_PROVIDERS.includes(selectedProvider) : false;
  const canPublishDirectly = supportsDirectPublish && isConnected;
  const publishing = updateCaption.isPending || publishDraft.isPending;

  return (
    <ThemedView style={styles.container}>
      <ScreenHeader title={title ? `Share ${title}` : 'Share'} />
      <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
        <View style={styles.section}>
          <ThemedText type="smallBold">Post to</ThemedText>
          <View style={styles.providerRow}>
            {draft.allowedProviders.map((provider) => {
              const selected = selectedProvider === provider;
              return (
                <Pressable
                  key={provider}
                  accessibilityRole="button"
                  onPress={() => setSelectedProvider(provider)}
                  style={[styles.providerChip, selected && { backgroundColor: Brand.championshipGold }]}>
                  <ThemedText type="small" style={selected ? styles.providerChipTextSelected : undefined}>
                    {PROVIDER_LABEL[provider] ?? provider}
                  </ThemedText>
                </Pressable>
              );
            })}
          </View>
          {selectedProvider && !supportsDirectPublish && (
            <ThemedText type="small" themeColor="textSecondary">
              Direct publishing isn&apos;t available for {PROVIDER_LABEL[selectedProvider]} yet — use &ldquo;Share another
              way&rdquo; below to post from your phone instead.
            </ThemedText>
          )}
          {selectedProvider && supportsDirectPublish && !isConnected && (
            <View style={styles.connectNotice}>
              <ThemedText type="small" themeColor="textSecondary" style={styles.flexOne}>
                Connect {PROVIDER_LABEL[selectedProvider]} in Settings to publish directly.
              </ThemedText>
              <Button variant="secondary" onPress={() => router.push('/settings' as any)}>
                Go to Settings
              </Button>
            </View>
          )}
        </View>

        <View style={styles.section}>
          <ThemedText type="smallBold">Caption</ThemedText>
          <TextInput
            value={captionText}
            onChangeText={setCaptionText}
            multiline
            maxLength={2000}
            textAlignVertical="top"
            placeholder="Write a caption…"
            placeholderTextColor={theme.textSecondary}
            style={[styles.captionInput, { color: theme.text, backgroundColor: theme.backgroundElement }]}
          />
          {selectedProvider === 'X' && (
            <ThemedText type="small" themeColor="textSecondary">
              X posts may be shortened to fit 280 characters — the link always stays intact.
            </ThemedText>
          )}
        </View>

        <ThemedView type="backgroundElement" style={styles.linkCard}>
          <ThemedText type="small" themeColor="textSecondary">Linking to</ThemedText>
          <Pressable onPress={() => Linking.openURL(draft.publicUrl)}>
            <ThemedText type="small" style={{ color: Brand.championshipGold }}>{draft.publicUrl}</ThemedText>
          </Pressable>
        </ThemedView>

        <View style={styles.actions}>
          <Button onPress={publish} disabled={!canPublishDirectly || publishing}>
            {publishing ? 'Posting…' : selectedProvider ? `Post to ${PROVIDER_LABEL[selectedProvider]}` : 'Post'}
          </Button>
          <Button variant="secondary" onPress={nativeShare}>
            Share another way
          </Button>
        </View>
      </ScrollView>
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  content: {
    paddingHorizontal: Spacing.four,
    paddingBottom: Spacing.six,
    gap: Spacing.four,
  },
  section: { gap: Spacing.two },
  providerRow: { flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.two },
  providerChip: {
    paddingHorizontal: Spacing.three,
    paddingVertical: Spacing.one,
    borderRadius: Spacing.four,
    backgroundColor: '#102B46',
  },
  providerChipTextSelected: { color: '#0B1F33' },
  connectNotice: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
  },
  flexOne: { flex: 1 },
  captionInput: {
    minHeight: 140,
    borderRadius: Spacing.two,
    padding: Spacing.three,
  },
  linkCard: {
    borderRadius: Spacing.three,
    padding: Spacing.three,
    gap: Spacing.one,
  },
  actions: { gap: Spacing.two },
});
