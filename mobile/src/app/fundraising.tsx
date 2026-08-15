import Ionicons from '@expo/vector-icons/Ionicons';
import { router, useLocalSearchParams } from 'expo-router';
import { useMemo, useState } from 'react';
import {
  Pressable,
  RefreshControl,
  ScrollView,
  Share,
  StyleSheet,
  Switch,
  useWindowDimensions,
  View,
} from 'react-native';

import { Button } from '@/components/button';
import { EmptyState } from '@/components/empty-state';
import { ErrorState } from '@/components/error-state';
import { ListControls } from '@/components/list-controls';
import { ListFooter } from '@/components/list-footer';
import { LoadingState } from '@/components/loading-state';
import { Modal } from '@/components/modal';
import { ScreenHeader } from '@/components/screen-header';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { useToast } from '@/components/toast';
import { Brand, Spacing } from '@/constants/theme';
import {
  useApproveCampaign,
  useFundraisingSettings,
  useRequestCampaignActivation,
  useReturnCampaignToDraft,
  useUpdateCampaignStatus,
  useUpdateFundraisingSettings,
} from '@/features/fundraising/api';
import {
  flattenCampaignPages,
  useCampaignSearchPage,
  useInfiniteCampaignSearch,
  type CampaignSearchSort,
} from '@/features/fundraising/searchApi';
import type {
  Campaign,
  CampaignStatus,
  CampaignType,
  FundraisingPersona,
} from '@/features/fundraising/types';
import { useTheme } from '@/hooks/use-theme';
import { env } from '@/lib/env';
import { formatMoneyMinorUnits } from '@/lib/money';

const STATUS: Record<CampaignStatus, string> = {
  DRAFT: 'Draft',
  PENDING_APPROVAL: 'Awaiting owner approval',
  SCHEDULED: 'Scheduled',
  ACTIVE: 'Active',
  ENDED: 'Ended',
  CLOSED: 'Closed',
  COMPLETED: 'Closed (legacy)',
  ARCHIVED: 'Archived',
};

const CAMPAIGN_TYPE_LABELS: Record<CampaignType, string> = {
  ORGANIZATION_GENERAL: 'Organization general',
  TEAM_GENERAL: 'Team general',
  TRAVEL: 'Travel',
  TOURNAMENT_FEES: 'Tournament fees',
  UNIFORMS: 'Uniforms',
  EQUIPMENT: 'Equipment',
  FACILITY_IMPROVEMENTS: 'Facility improvements',
  SCHOLARSHIPS: 'Scholarships',
  SPECIAL_EVENTS: 'Special events',
  APPAREL_BASED: 'Apparel',
  SPONSOR_SUPPORTED: 'Sponsor-supported',
};

const FILTER_STATUSES: CampaignStatus[] = [
  'DRAFT',
  'PENDING_APPROVAL',
  'SCHEDULED',
  'ACTIVE',
  'ENDED',
  'CLOSED',
  'ARCHIVED',
];

const FILTER_TYPES = Object.keys(CAMPAIGN_TYPE_LABELS) as CampaignType[];

export default function FundraisingScreen() {
  const {
    organizationId = '',
    persona = 'parent',
    defaultTeamId = '',
    defaultTeamName = '',
  } = useLocalSearchParams<{
    organizationId: string;
    persona: FundraisingPersona;
    defaultTeamId?: string;
    defaultTeamName?: string;
  }>();
  const { width } = useWindowDimensions();
  const theme = useTheme();
  const toast = useToast();
  const [query, setQuery] = useState('');
  const [status, setStatus] = useState<CampaignStatus | ''>('');
  const [campaignType, setCampaignType] = useState<CampaignType | ''>('');
  const [sort, setSort] = useState<CampaignSearchSort>('NEWEST');
  const [filterOpen, setFilterOpen] = useState(false);
  const [sortOpen, setSortOpen] = useState(false);

  const campaigns = useInfiniteCampaignSearch(organizationId || null, {
    q: query,
    status,
    campaignType,
    sort,
  });
  const pendingCampaigns = useCampaignSearchPage(
    organizationId || null,
    { status: 'PENDING_APPROVAL', sort: 'NEWEST' },
    100,
  );
  const settings = useFundraisingSettings(organizationId || null);
  const updateSettings = useUpdateFundraisingSettings(organizationId || null);
  const requestActivation = useRequestCampaignActivation(organizationId || null);
  const approve = useApproveCampaign(organizationId || null);
  const returnToDraft = useReturnCampaignToDraft(organizationId || null);
  const updateStatus = useUpdateCampaignStatus(organizationId || null);

  const isOwner = persona === 'owner';
  const wide = width >= 760;
  const items = useMemo(() => flattenCampaignPages(campaigns.data?.pages), [campaigns.data?.pages]);
  const total = campaigns.data?.pages[0]?.totalElements ?? 0;
  const pending = isOwner ? (pendingCampaigns.data?.items ?? []) : [];
  const busy =
    requestActivation.isPending ||
    approve.isPending ||
    returnToDraft.isPending ||
    updateStatus.isPending;

  const activeFilters = useMemo(() => {
    const values: { key: 'status' | 'campaignType'; label: string }[] = [];
    if (status) values.push({ key: 'status', label: STATUS[status] });
    if (campaignType) values.push({ key: 'campaignType', label: CAMPAIGN_TYPE_LABELS[campaignType] });
    return values;
  }, [campaignType, status]);

  const sortLabel =
    sort === 'NEWEST' ? 'Newest'
    : sort === 'NAME_ASC' ? 'Name A–Z'
    : sort === 'START_DATE_ASC' ? 'Start date'
    : sort === 'END_DATE_ASC' ? 'End date'
    : sort === 'RAISED_DESC' ? 'Most raised'
    : 'Largest goal';

  if (!organizationId) return <ErrorState message="No organization is selected." />;

  async function run(label: string, action: () => Promise<unknown>) {
    try {
      await action();
      toast.show(label, 'success');
    } catch {
      toast.show('That fundraiser action could not be completed.', 'error');
    }
  }

  function openDetail(campaign: Campaign) {
    router.push({
      pathname: '/fundraising-detail' as any,
      params: {
        organizationId,
        campaignId: campaign.id,
        persona,
        defaultTeamId,
        defaultTeamName,
      },
    });
  }

  return (
    <ThemedView style={styles.container}>
      <ScreenHeader
        title="Fundraising"
        right={
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="Create fundraiser"
            hitSlop={8}
            onPress={() =>
              router.push({
                pathname: '/fundraising-form' as any,
                params: { organizationId, persona, mode: 'create', defaultTeamId, defaultTeamName },
              })
            }>
            <Ionicons name="add-circle-outline" size={25} color={Brand.championshipGold} />
          </Pressable>
        }
      />

      <ScrollView
        keyboardShouldPersistTaps="handled"
        refreshControl={
          <RefreshControl
            refreshing={campaigns.isRefetching && !campaigns.isFetchingNextPage}
            onRefresh={() => {
              campaigns.refetch();
              if (isOwner) pendingCampaigns.refetch();
            }}
          />
        }
        contentContainerStyle={[styles.content, wide && styles.contentWide]}>
        <View style={styles.heroRow}>
          <View style={styles.flexOne}>
            <ThemedText type="subtitle">Fundraising</ThemedText>
            <ThemedText type="small" themeColor="textSecondary" style={styles.topNote}>
              Create, share, approve, and track Rally26 fundraisers from the app.
            </ThemedText>
          </View>
          <Button
            style={styles.heroButton}
            onPress={() =>
              router.push({
                pathname: '/fundraising-form' as any,
                params: { organizationId, persona, mode: 'create', defaultTeamId, defaultTeamName },
              })
            }>
            Create
          </Button>
        </View>

        {isOwner && settings.data && (
          <ThemedView type="backgroundElement" style={styles.policyCard}>
            <View style={styles.policyCopy}>
              <ThemedText type="smallBold">Require owner approval</ThemedText>
              <ThemedText type="small" themeColor="textSecondary">
                When on, coach/parent/admin fundraisers must be approved by the owner before going active.
              </ThemedText>
            </View>
            <Switch
              accessibilityLabel="Require owner approval for fundraisers"
              value={settings.data.requireOwnerApproval}
              disabled={updateSettings.isPending}
              onValueChange={(value) =>
                updateSettings.mutate(value, {
                  onSuccess: () =>
                    toast.show(value ? 'Owner approval required.' : 'Creator activation enabled.', 'success'),
                  onError: () => toast.show('Could not update fundraising approval settings.', 'error'),
                })
              }
              trackColor={{ true: Brand.victoryGreen }}
            />
          </ThemedView>
        )}

        {isOwner && pending.length > 0 && (
          <View style={styles.section}>
            <ThemedText type="smallBold">
              Needs your approval ({pendingCampaigns.data?.totalElements ?? pending.length})
            </ThemedText>
            {pending.map((campaign) => (
              <ThemedView key={campaign.id} type="backgroundElement" style={styles.approvalCard}>
                <Pressable style={styles.flexOne} onPress={() => openDetail(campaign)}>
                  <ThemedText type="smallBold">{campaign.name}</ThemedText>
                  <ThemedText type="small" themeColor="textSecondary">
                    {moneyProgress(campaign)}
                  </ThemedText>
                </Pressable>
                <View style={styles.inlineActions}>
                  <Button
                    variant="secondary"
                    disabled={busy}
                    onPress={() =>
                      run('Returned to draft.', () => returnToDraft.mutateAsync(campaign.id))
                    }>
                    Return
                  </Button>
                  <Button
                    disabled={busy}
                    onPress={() =>
                      run('Fundraiser approved.', () => approve.mutateAsync(campaign.id))
                    }>
                    Approve
                  </Button>
                </View>
              </ThemedView>
            ))}
          </View>
        )}

        <View style={styles.controls}>
          <ListControls
            query={query}
            onChangeQuery={setQuery}
            searchPlaceholder="Search fundraisers"
            resultCount={total}
            activeFilters={activeFilters.map((item) => item.label)}
            onRemoveFilter={(index) => {
              const filter = activeFilters[index];
              if (filter?.key === 'status') setStatus('');
              if (filter?.key === 'campaignType') setCampaignType('');
            }}
            onClearFilters={() => {
              setQuery('');
              setStatus('');
              setCampaignType('');
              setSort('NEWEST');
            }}
            onPressFilter={() => setFilterOpen(true)}
            onPressSort={() => setSortOpen(true)}
            sortLabel={sortLabel}
          />
        </View>

        {campaigns.isLoading && <LoadingState label="Loading fundraisers…" />}
        {campaigns.isError && (
          <ErrorState message="Could not load fundraisers." onRetry={() => campaigns.refetch()} />
        )}

        {!campaigns.isLoading && !campaigns.isError && items.length === 0 && (
          <EmptyState
            title={query.trim() || activeFilters.length > 0 ? 'No results found' : 'No fundraisers yet'}
            description={
              query.trim() || activeFilters.length > 0
                ? 'Try changing your search or filters.'
                : 'Start from a Rally26 template and share the public QR/link when it is ready.'
            }
          />
        )}

        {items.length > 0 && (
          <View style={[styles.cards, wide && styles.cardsWide]}>
            {items.map((campaign) => (
              <CampaignCard
                key={campaign.id}
                campaign={campaign}
                wide={wide}
                busy={busy}
                onOpen={() => openDetail(campaign)}
                onAction={(statusAction) => {
                  if (statusAction === 'activate') {
                    run('Fundraiser submitted.', () => requestActivation.mutateAsync(campaign.id));
                  }
                  if (statusAction === 'approve') {
                    run('Fundraiser approved.', () => approve.mutateAsync(campaign.id));
                  }
                  if (statusAction === 'return') {
                    run('Returned to draft.', () => returnToDraft.mutateAsync(campaign.id));
                  }
                  if (statusAction === 'close') {
                    run('Fundraiser closed.', () =>
                      updateStatus.mutateAsync({ campaignId: campaign.id, status: 'CLOSED' }),
                    );
                  }
                  if (statusAction === 'archive') {
                    run('Fundraiser archived.', () =>
                      updateStatus.mutateAsync({ campaignId: campaign.id, status: 'ARCHIVED' }),
                    );
                  }
                }}
              />
            ))}
          </View>
        )}

        <ListFooter
          loadedCount={items.length}
          totalCount={total}
          hasMore={!!campaigns.hasNextPage}
          loadingMore={campaigns.isFetchingNextPage}
          onLoadMore={() => campaigns.fetchNextPage()}
        />
      </ScrollView>

      <Modal visible={filterOpen} onClose={() => setFilterOpen(false)}>
        <ThemedText type="smallBold" style={styles.modalTitle}>Filter fundraisers</ThemedText>
        <ScrollView style={styles.modalScroll} showsVerticalScrollIndicator={false}>
        <ThemedText type="smallBold" style={styles.filterHeading}>Status</ThemedText>
        <FilterOption
          selected={!status}
          label="All statuses"
          onPress={() => setStatus('')}
        />
        {FILTER_STATUSES.map((value) => (
          <FilterOption
            key={value}
            selected={status === value}
            label={STATUS[value]}
            onPress={() => setStatus(value)}
          />
        ))}
        <ThemedText type="smallBold" style={styles.filterHeading}>Type</ThemedText>
        <FilterOption
          selected={!campaignType}
          label="All fundraiser types"
          onPress={() => setCampaignType('')}
        />
        {FILTER_TYPES.map((value) => (
          <FilterOption
            key={value}
            selected={campaignType === value}
            label={CAMPAIGN_TYPE_LABELS[value]}
            onPress={() => setCampaignType(value)}
          />
        ))}
        </ScrollView>
        <Pressable accessibilityRole="button" style={styles.done} onPress={() => setFilterOpen(false)}>
          <ThemedText type="smallBold">Done</ThemedText>
        </Pressable>
      </Modal>

      <Modal visible={sortOpen} onClose={() => setSortOpen(false)}>
        <ThemedText type="smallBold" style={styles.modalTitle}>Sort fundraisers</ThemedText>
        {([
          ['NEWEST', 'Newest'],
          ['NAME_ASC', 'Name A–Z'],
          ['START_DATE_ASC', 'Start date'],
          ['END_DATE_ASC', 'End date'],
          ['RAISED_DESC', 'Most raised'],
          ['GOAL_DESC', 'Largest goal'],
        ] as const).map(([value, label]) => (
          <FilterOption
            key={value}
            selected={sort === value}
            label={label}
            onPress={() => {
              setSort(value);
              setSortOpen(false);
            }}
          />
        ))}
      </Modal>
    </ThemedView>
  );

  function FilterOption({
    selected,
    label,
    onPress,
  }: {
    selected: boolean;
    label: string;
    onPress: () => void;
  }) {
    return (
      <Pressable accessibilityRole="button" onPress={onPress} style={styles.option}>
        <ThemedText type={selected ? 'smallBold' : 'default'}>{label}</ThemedText>
        {selected && <Ionicons name="checkmark" size={18} color={theme.text} />}
      </Pressable>
    );
  }
}

function CampaignCard({
  campaign,
  wide,
  busy,
  onOpen,
  onAction,
}: {
  campaign: Campaign;
  wide: boolean;
  busy: boolean;
  onOpen: () => void;
  onAction: (action: 'activate' | 'approve' | 'return' | 'close' | 'archive') => void;
}) {
  const permissions = campaign.permissions;
  const progress =
    campaign.goalAmountMinor > 0
      ? Math.min(100, Math.round((campaign.raisedMinor / campaign.goalAmountMinor) * 100))
      : 0;

  return (
    <ThemedView type="backgroundElement" style={[styles.campaignCard, wide && styles.campaignCardWide]}>
      <Pressable accessibilityRole="button" onPress={onOpen} style={styles.cardPressable}>
        <View style={styles.cardTop}>
          <View style={styles.flexOne}>
            <ThemedText type="smallBold" numberOfLines={2}>{campaign.name}</ThemedText>
            <ThemedText type="small" themeColor="textSecondary">{STATUS[campaign.status]}</ThemedText>
          </View>
          <Ionicons name="chevron-forward" size={20} color={Brand.slateGray} />
        </View>
        <View style={styles.progressTrack}>
          <View style={[styles.progressFill, { width: `${progress}%` }]} />
        </View>
        <ThemedText type="small" themeColor="textSecondary">
          {moneyProgress(campaign)} · {progress}%
        </ThemedText>
        {(campaign.startDate || campaign.endDate) && (
          <ThemedText type="small" themeColor="textSecondary">{dateRange(campaign)}</ThemedText>
        )}
      </Pressable>
      <View style={styles.cardActions}>
        {permissions?.canRequestActivation && <SmallAction label="Submit / activate" onPress={() => onAction('activate')} disabled={busy} />}
        {permissions?.canApprove && <SmallAction label="Approve" onPress={() => onAction('approve')} disabled={busy} />}
        {permissions?.canReturnToDraft && <SmallAction label="Return" onPress={() => onAction('return')} disabled={busy} />}
        {permissions?.canClose && <SmallAction label="Close" onPress={() => onAction('close')} disabled={busy} />}
        {permissions?.canArchive && <SmallAction label="Archive" onPress={() => onAction('archive')} disabled={busy} />}
      </View>
    </ThemedView>
  );
}

function SmallAction({ label, onPress, disabled }: { label: string; onPress: () => void; disabled: boolean }) {
  return (
    <Pressable
      accessibilityRole="button"
      disabled={disabled}
      onPress={onPress}
      style={styles.smallAction}>
      <ThemedText type="smallBold">{label}</ThemedText>
    </Pressable>
  );
}

function moneyProgress(campaign: Campaign) {
  return `${formatMoneyMinorUnits(campaign.raisedMinor, campaign.currency)} of ${formatMoneyMinorUnits(campaign.goalAmountMinor, campaign.currency)}`;
}

function dateRange(campaign: Campaign) {
  const start = campaign.startDate
    ? new Date(`${campaign.startDate}T12:00:00`).toLocaleDateString()
    : 'Now';
  const end = campaign.endDate
    ? new Date(`${campaign.endDate}T12:00:00`).toLocaleDateString()
    : 'No end date';
  return `${start} – ${end}`;
}

export async function shareCampaign(slug: string, name: string) {
  const url = `${env.frontendBaseUrl}/campaigns/${slug}`;
  await Share.share({ message: `${name}\n${url}`, url });
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  content: {
    paddingHorizontal: Spacing.four,
    paddingBottom: Spacing.six,
    width: '100%',
    alignSelf: 'center',
  },
  contentWide: { maxWidth: 980 },
  heroRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.three,
    marginBottom: Spacing.four,
  },
  heroButton: { minWidth: 110 },
  topNote: { marginTop: Spacing.one, maxWidth: 560 },
  flexOne: { flex: 1 },
  policyCard: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.three,
    borderRadius: Spacing.three,
    padding: Spacing.three,
    marginBottom: Spacing.four,
  },
  policyCopy: { flex: 1, gap: Spacing.one },
  section: { gap: Spacing.two, marginBottom: Spacing.four },
  approvalCard: { borderRadius: Spacing.three, padding: Spacing.three, gap: Spacing.three },
  inlineActions: { flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.two },
  controls: { marginBottom: Spacing.three },
  cards: { gap: Spacing.three },
  cardsWide: { flexDirection: 'row', flexWrap: 'wrap' },
  campaignCard: { borderRadius: Spacing.three, padding: Spacing.three, gap: Spacing.three },
  campaignCardWide: { width: '48.5%' },
  cardPressable: { gap: Spacing.two },
  cardTop: { flexDirection: 'row', alignItems: 'center', gap: Spacing.two },
  progressTrack: { height: 8, borderRadius: 4, overflow: 'hidden', backgroundColor: '#31455A' },
  progressFill: { height: 8, borderRadius: 4, backgroundColor: Brand.victoryGreen },
  cardActions: { flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.one },
  smallAction: {
    minHeight: 38,
    justifyContent: 'center',
    paddingHorizontal: Spacing.two,
    borderRadius: Spacing.two,
    borderWidth: 1,
    borderColor: Brand.slateGray,
  },
  modalTitle: { marginBottom: Spacing.two },
  modalScroll: { maxHeight: 430 },
  filterHeading: { marginTop: Spacing.two, marginBottom: Spacing.one },
  option: {
    minHeight: 44,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: Spacing.two,
  },
  done: {
    minHeight: 46,
    alignItems: 'center',
    justifyContent: 'center',
    marginTop: Spacing.three,
  },
});
