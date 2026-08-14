import Ionicons from '@expo/vector-icons/Ionicons';
import { router, useLocalSearchParams } from 'expo-router';
import { Image, Linking, Pressable, ScrollView, Share, StyleSheet, useWindowDimensions, View } from 'react-native';

import { Button } from '@/components/button';
import { ErrorState } from '@/components/error-state';
import { LoadingState } from '@/components/loading-state';
import { ScreenHeader } from '@/components/screen-header';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { useToast } from '@/components/toast';
import {
  useApproveCampaign,
  useCampaign,
  useCampaignShareLink,
  useContributions,
  useRequestCampaignActivation,
  useReturnCampaignToDraft,
  useUpdateCampaignStatus,
} from '@/features/fundraising/api';
import type { FundraisingPersona } from '@/features/fundraising/types';
import { Brand, Spacing } from '@/constants/theme';
import { env } from '@/lib/env';
import { formatMoneyMinorUnits } from '@/lib/money';

export default function FundraisingDetailScreen() {
  const { organizationId = '', campaignId = '', persona = 'parent', defaultTeamId = '', defaultTeamName = '' } = useLocalSearchParams<{
    organizationId: string;
    campaignId: string;
    persona?: FundraisingPersona;
    defaultTeamId?: string;
    defaultTeamName?: string;
  }>();
  const { width } = useWindowDimensions();
  const toast = useToast();
  const campaign = useCampaign(organizationId || null, campaignId || null);
  const contributions = useContributions(organizationId || null, campaignId || null);
  const shareLink = useCampaignShareLink(organizationId || null);
  const requestActivation = useRequestCampaignActivation(organizationId || null);
  const approve = useApproveCampaign(organizationId || null);
  const returnToDraft = useReturnCampaignToDraft(organizationId || null);
  const updateStatus = useUpdateCampaignStatus(organizationId || null);
  const wide = width >= 760;

  if (campaign.isLoading) return <><ScreenHeader title="Fundraiser" /><LoadingState label="Loading fundraiser…" /></>;
  if (campaign.isError || !campaign.data) return <><ScreenHeader title="Fundraiser" /><ErrorState message="Could not load this fundraiser." onRetry={() => campaign.refetch()} /></>;

  const item = campaign.data;
  const permissions = item.permissions;
  const publicUrl = `${env.frontendBaseUrl}/campaigns/${item.slug}`;
  const busy = requestActivation.isPending || approve.isPending || returnToDraft.isPending || updateStatus.isPending;
  const progress = item.goalAmountMinor > 0 ? Math.min(100, Math.round((item.raisedMinor / item.goalAmountMinor) * 100)) : 0;

  async function run(message: string, action: () => Promise<unknown>) {
    try { await action(); toast.show(message, 'success'); campaign.refetch(); }
    catch { toast.show('That fundraiser action could not be completed.', 'error'); }
  }

  async function share() {
    try { await Share.share({ message: `${item.name}\n${publicUrl}`, url: publicUrl }); }
    catch { toast.show('Could not open sharing.', 'error'); }
  }

  function showQr() {
    if (!shareLink.data && !shareLink.isPending) shareLink.mutate(publicUrl);
  }

  return (
    <ThemedView style={styles.container}>
       <ScreenHeader
         title="Fundraiser"
         right={permissions?.canEdit ? <Pressable hitSlop={8} onPress={() => router.push({ pathname: '/fundraising-form' as any, params: { organizationId, campaignId, persona, mode: 'edit', defaultTeamId, defaultTeamName } })}><Ionicons name="create-outline" size={23} color={Brand.championshipGold} /></Pressable> : undefined}
       />
      <ScrollView contentContainerStyle={[styles.content, wide && styles.contentWide]}>
        <View style={[styles.topGrid, wide && styles.topGridWide]}>
          <View style={styles.mainColumn}>
            <ThemedText type="subtitle">{item.name}</ThemedText>
            <ThemedText type="smallBold" style={styles.status}>{statusLabel(item.status)}</ThemedText>
            {item.description && <ThemedText style={styles.description}>{item.description}</ThemedText>}
            {(item.eventLocationName || item.eventAddress) && <ThemedView type="backgroundElement" style={styles.locationCard}>
              <ThemedText type="smallBold">In-person location</ThemedText>
              {item.eventLocationName && <ThemedText>{item.eventLocationName}</ThemedText>}
              {item.eventAddress && <ThemedText type="small" themeColor="textSecondary">{item.eventAddress}</ThemedText>}
            </ThemedView>}
            <View style={styles.progressTrack}><View style={[styles.progressFill, { width: `${progress}%` }]} /></View>
            <ThemedText type="smallBold">{formatMoneyMinorUnits(item.raisedMinor, item.currency)} raised</ThemedText>
            <ThemedText type="small" themeColor="textSecondary">Goal {formatMoneyMinorUnits(item.goalAmountMinor, item.currency)} · {progress}%</ThemedText>
            <ThemedText type="small" themeColor="textSecondary">{dateRange(item.startDate, item.endDate)}</ThemedText>
          </View>

          <ThemedView type="backgroundElement" style={styles.shareCard}>
            <ThemedText type="smallBold">QR & sharing</ThemedText>
            <ThemedText type="small" themeColor="textSecondary">Every fundraiser uses its permanent public Rally26 URL.</ThemedText>
            <Button onPress={showQr} disabled={shareLink.isPending}>{shareLink.data ? 'QR ready' : shareLink.isPending ? 'Generating…' : 'Show QR'}</Button>
            {shareLink.data && <Image accessibilityLabel="Fundraiser QR code" source={{ uri: shareLink.data.qrCodeDataUri }} style={styles.qr} />}
            <Button variant="secondary" onPress={share}>Share fundraiser</Button>
            <Button variant="secondary" onPress={() => Linking.openURL(publicUrl)}>Open public page</Button>
          </ThemedView>
        </View>

        <View style={styles.actions}>
          {permissions?.canRequestActivation && <Button disabled={busy} onPress={() => run('Fundraiser submitted.', () => requestActivation.mutateAsync(item.id))}>Submit / Activate</Button>}
          {permissions?.canApprove && <Button disabled={busy} onPress={() => run('Fundraiser approved.', () => approve.mutateAsync(item.id))}>Approve</Button>}
          {permissions?.canReturnToDraft && <Button variant="secondary" disabled={busy} onPress={() => run('Returned to draft.', () => returnToDraft.mutateAsync(item.id))}>Return to Draft</Button>}
          {permissions?.canClose && <Button variant="secondary" disabled={busy} onPress={() => run('Fundraiser closed.', () => updateStatus.mutateAsync({ campaignId: item.id, status: 'COMPLETED' }))}>Close Fundraiser</Button>}
          {permissions?.canArchive && <Button variant="secondary" disabled={busy} onPress={() => run('Fundraiser archived.', () => updateStatus.mutateAsync({ campaignId: item.id, status: 'ARCHIVED' }))}>Archive</Button>}
        </View>

        {permissions?.canEdit && <ThemedView type="backgroundElement" style={styles.gameCard}>
          <View style={styles.gameCopy}>
            <ThemedText type="smallBold">Free fundraising game</ThemedText>
            <ThemedText type="small" themeColor="textSecondary">Add Big Game Squares, a bracket/prediction challenge, trivia, or a free prize drawing. Entries stay separate from donations.</ThemedText>
          </View>
          <Button variant="secondary" onPress={() => router.push({ pathname: '/fundraising-game' as any, params: { organizationId, campaignId: item.id, slug: item.slug, persona } })}>Manage Game</Button>
        </ThemedView>}

        <View style={styles.sectionHeader}>
          <ThemedText type="smallBold">Contributions</ThemedText>
          <ThemedText type="small" themeColor="textSecondary">{contributions.data?.totalElements ?? 0}</ThemedText>
        </View>
        {contributions.isLoading && <LoadingState label="Loading contributions…" />}
        {contributions.isError && <ErrorState message="Could not load contributions." onRetry={() => contributions.refetch()} />}
        {contributions.data?.items.length === 0 && <ThemedView type="backgroundElement" style={styles.empty}><ThemedText type="small" themeColor="textSecondary">No contributions yet.</ThemedText></ThemedView>}
        {contributions.data?.items.map((contribution: any) => (
          <ThemedView key={contribution.id} type="backgroundElement" style={styles.contributionRow}>
            <View style={styles.flexOne}>
              <ThemedText type="smallBold">{contribution.isAnonymous ? 'Anonymous supporter' : (contribution.supporterName || 'Supporter')}</ThemedText>
              <ThemedText type="small" themeColor="textSecondary">{contribution.status} · {contribution.paymentSource}</ThemedText>
            </View>
            <ThemedText type="smallBold">{formatMoneyMinorUnits(contribution.amountMinor, contribution.currency)}</ThemedText>
          </ThemedView>
        ))}
      </ScrollView>
    </ThemedView>
  );
}

function statusLabel(status: string) {
  if (status === 'PENDING_APPROVAL') return 'Awaiting owner approval';
  if (status === 'COMPLETED') return 'Closed';
  return status.charAt(0) + status.slice(1).toLowerCase();
}
function dateRange(start: string | null, end: string | null) {
  const s = start ? new Date(`${start}T12:00:00`).toLocaleDateString() : 'Starts now';
  const e = end ? new Date(`${end}T12:00:00`).toLocaleDateString() : 'No end date';
  return `${s} – ${e}`;
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  content: { width: '100%', alignSelf: 'center', paddingHorizontal: Spacing.four, paddingBottom: Spacing.six, gap: Spacing.four },
  contentWide: { maxWidth: 980 },
  topGrid: { gap: Spacing.four },
  topGridWide: { flexDirection: 'row', alignItems: 'flex-start' },
  mainColumn: { flex: 1, gap: Spacing.two },
  status: { color: Brand.victoryGreen },
  description: { marginVertical: Spacing.one },
  locationCard: { borderRadius: Spacing.three, padding: Spacing.three, gap: Spacing.one },
  progressTrack: { height: 10, borderRadius: 5, overflow: 'hidden', backgroundColor: '#31455A', marginTop: Spacing.two },
  progressFill: { height: 10, backgroundColor: Brand.victoryGreen },
  shareCard: { borderRadius: Spacing.three, padding: Spacing.three, gap: Spacing.two, width: '100%', maxWidth: 330, alignSelf: 'center' },
  qr: { width: 210, height: 210, alignSelf: 'center' },
  actions: { flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.two },
  gameCard: { flexDirection: 'row', flexWrap: 'wrap', alignItems: 'center', gap: Spacing.three, borderRadius: Spacing.three, padding: Spacing.three },
  gameCopy: { flex: 1, minWidth: 220, gap: Spacing.one },
  sectionHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  empty: { borderRadius: Spacing.three, padding: Spacing.four, alignItems: 'center' },
  contributionRow: { flexDirection: 'row', alignItems: 'center', gap: Spacing.three, borderRadius: Spacing.two, padding: Spacing.three },
  flexOne: { flex: 1 },
});
