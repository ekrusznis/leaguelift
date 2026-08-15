import Ionicons from '@expo/vector-icons/Ionicons';
import { useMemo, useState, type ReactNode } from 'react';
import {
  Pressable,
  ScrollView,
  StyleSheet,
  Switch,
  TextInput,
  View,
} from 'react-native';

import { Button } from '@/components/button';
import { ConfirmDialog } from '@/components/confirm-dialog';
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
import { useDashboardContext } from '@/features/dashboard/api';
import {
  flattenSponsorshipPages,
  useApproveSponsorship,
  useArchiveSponsorshipPackage,
  useCreateSponsorshipPackage,
  useInfiniteSponsorshipPackages,
  useInfiniteSponsorships,
  usePublishSponsorshipPackage,
  useRejectSponsorship,
  type PackageSort,
  type SponsorshipPackageStatus,
  type SponsorshipReviewStatus,
  type SponsorshipSort,
  type SponsorshipStatus,
} from '@/features/sponsorship/api';
import { Brand, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { formatMoneyMinorUnits } from '@/lib/money';

type Section = 'packages' | 'sponsors' | 'review';

export default function OwnerSponsorshipsScreen() {
  const dashboard = useDashboardContext(true);
  const organizationId = dashboard.data?.organizationId ?? null;
  const [section, setSection] = useState<Section>('packages');

  return (
    <ThemedView style={styles.container}>
      <ScreenHeader title="Sponsorships" />
      <View style={styles.segment}>
        <Segment label="Packages" selected={section === 'packages'} onPress={() => setSection('packages')} />
        <Segment label="Sponsors" selected={section === 'sponsors'} onPress={() => setSection('sponsors')} />
        <Segment label="Review" selected={section === 'review'} onPress={() => setSection('review')} />
      </View>
      {section === 'packages' && <Packages organizationId={organizationId} />}
      {section === 'sponsors' && <Sponsors organizationId={organizationId} reviewOnly={false} />}
      {section === 'review' && <Sponsors organizationId={organizationId} reviewOnly />}
    </ThemedView>
  );
}

function Packages({ organizationId }: { organizationId: string | null }) {
  const theme = useTheme();
  const toast = useToast();
  const [query, setQuery] = useState('');
  const [status, setStatus] = useState<SponsorshipPackageStatus | ''>('');
  const [exclusiveOnly, setExclusiveOnly] = useState(false);
  const [sort, setSort] = useState<PackageSort>('NEWEST');
  const [filterOpen, setFilterOpen] = useState(false);
  const [sortOpen, setSortOpen] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);
  const [archiveTarget, setArchiveTarget] = useState<{ id: string; name: string } | null>(null);

  const result = useInfiniteSponsorshipPackages(organizationId, {
    q: query,
    status,
    exclusive: exclusiveOnly ? true : undefined,
    sort,
  });
  const items = useMemo(() => flattenSponsorshipPages(result.data?.pages), [result.data?.pages]);
  const total = result.data?.pages[0]?.totalElements ?? 0;
  const publish = usePublishSponsorshipPackage(organizationId);
  const archive = useArchiveSponsorshipPackage(organizationId);

  const filters = [
    ...(status ? [status.charAt(0) + status.slice(1).toLowerCase()] : []),
    ...(exclusiveOnly ? ['Exclusive only'] : []),
  ];
  const sortLabel =
    sort === 'NEWEST' ? 'Newest'
    : sort === 'OLDEST' ? 'Oldest'
    : sort === 'NAME_ASC' ? 'Name A–Z'
    : sort === 'NAME_DESC' ? 'Name Z–A'
    : sort === 'PRICE_ASC' ? 'Price low–high'
    : sort === 'PRICE_DESC' ? 'Price high–low'
    : 'Most sponsors';

  return (
    <>
      <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
        <ListControls
          query={query}
          onChangeQuery={setQuery}
          searchPlaceholder="Search sponsorship packages"
          resultCount={total}
          activeFilters={filters}
          onRemoveFilter={(index) => {
            if (status && index === 0) setStatus('');
            else setExclusiveOnly(false);
          }}
          onClearFilters={() => {
            setQuery('');
            setStatus('');
            setExclusiveOnly(false);
            setSort('NEWEST');
          }}
          onPressFilter={() => setFilterOpen(true)}
          onPressSort={() => setSortOpen(true)}
          sortLabel={sortLabel}
        />
        <Button onPress={() => setCreateOpen(true)}>Add Sponsorship Package</Button>

        {result.isLoading && <LoadingState label="Loading sponsorship packages…" />}
        {result.isError && (
          <ErrorState message="Could not load sponsorship packages." onRetry={() => result.refetch()} />
        )}
        {!result.isLoading && !result.isError && items.length === 0 && (
          <EmptyState
            title={query.trim() || filters.length > 0 ? 'No results found' : 'No sponsorship packages yet'}
            description={
              query.trim() || filters.length > 0
                ? 'Try changing your search or filters.'
                : 'Create a package to start accepting sponsors.'
            }
          />
        )}

        {items.map((item) => (
          <ThemedView key={item.id} type="backgroundElement" style={styles.card}>
            <View style={styles.cardTop}>
              <View style={styles.flexOne}>
                <ThemedText type="smallBold">{item.name}</ThemedText>
                <ThemedText type="small" themeColor="textSecondary">
                  {formatMoneyMinorUnits(item.priceMinor, item.currency)} · {item.confirmedCount}
                  {item.maxQuantity ? ` of ${item.maxQuantity}` : ''} confirmed
                </ThemedText>
                <ThemedText type="small" themeColor="textSecondary">
                  {item.status}
                  {item.exclusive ? ' · Exclusive' : ''}
                  {item.soldOut ? ' · Sold out' : ''}
                </ThemedText>
              </View>
              <Ionicons
                name={item.status === 'PUBLISHED' ? 'radio-button-on' : 'radio-button-off'}
                size={18}
                color={item.status === 'PUBLISHED' ? Brand.victoryGreen : theme.textSecondary}
              />
            </View>
            <View style={styles.actions}>
              {item.status === 'DRAFT' && (
                <Button
                  onPress={() =>
                    publish.mutate(item.id, {
                      onSuccess: () => toast.show('Sponsorship package published.', 'success'),
                      onError: () => toast.show('Could not publish the package.', 'error'),
                    })
                  }
                  disabled={publish.isPending}>
                  Publish
                </Button>
              )}
              {item.status === 'PUBLISHED' && (
                <Button
                  variant="secondary"
                  onPress={() => setArchiveTarget({ id: item.id, name: item.name })}
                  disabled={archive.isPending}>
                  Archive
                </Button>
              )}
            </View>
          </ThemedView>
        ))}

        <ListFooter
          loadedCount={items.length}
          totalCount={total}
          hasMore={!!result.hasNextPage}
          loadingMore={result.isFetchingNextPage}
          onLoadMore={() => result.fetchNextPage()}
        />
      </ScrollView>

      <Modal visible={filterOpen} onClose={() => setFilterOpen(false)}>
        <ThemedText type="smallBold" style={styles.modalTitle}>Filter packages</ThemedText>
        <Option selected={!status} label="All statuses" onPress={() => setStatus('')} />
        <Option selected={status === 'DRAFT'} label="Draft" onPress={() => setStatus('DRAFT')} />
        <Option selected={status === 'PUBLISHED'} label="Published" onPress={() => setStatus('PUBLISHED')} />
        <Option selected={status === 'ARCHIVED'} label="Archived" onPress={() => setStatus('ARCHIVED')} />
        <Option
          selected={exclusiveOnly}
          label="Exclusive only"
          onPress={() => setExclusiveOnly((value) => !value)}
        />
        <Pressable style={styles.done} onPress={() => setFilterOpen(false)}>
          <ThemedText type="smallBold">Done</ThemedText>
        </Pressable>
      </Modal>

      <Modal visible={sortOpen} onClose={() => setSortOpen(false)}>
        <ThemedText type="smallBold" style={styles.modalTitle}>Sort packages</ThemedText>
        {([
          ['NEWEST', 'Newest'],
          ['OLDEST', 'Oldest'],
          ['NAME_ASC', 'Name A–Z'],
          ['NAME_DESC', 'Name Z–A'],
          ['PRICE_ASC', 'Price low–high'],
          ['PRICE_DESC', 'Price high–low'],
          ['SPONSORS_DESC', 'Most sponsors'],
        ] as const).map(([value, label]) => (
          <Option
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

      <CreatePackageModal
        visible={createOpen}
        organizationId={organizationId}
        onClose={() => setCreateOpen(false)}
      />

      <ConfirmDialog
        visible={!!archiveTarget}
        title="Archive sponsorship package?"
        message={`${archiveTarget?.name ?? 'This package'} will no longer be available for new sponsorship purchases. Existing sponsor records remain intact.`}
        confirmLabel="Archive"
        onCancel={() => setArchiveTarget(null)}
        onConfirm={() => {
          if (!archiveTarget) return;
          archive.mutate(archiveTarget.id, {
            onSuccess: () => {
              toast.show('Sponsorship package archived.', 'success');
              setArchiveTarget(null);
            },
            onError: () => toast.show('Could not archive the package.', 'error'),
          });
        }}
      />
    </>
  );

  function Option({
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

function CreatePackageModal({
  visible,
  organizationId,
  onClose,
}: {
  visible: boolean;
  organizationId: string | null;
  onClose: () => void;
}) {
  const theme = useTheme();
  const toast = useToast();
  const create = useCreateSponsorshipPackage(organizationId);
  const [name, setName] = useState('');
  const [priceMajor, setPriceMajor] = useState('');
  const [description, setDescription] = useState('');
  const [maxQuantity, setMaxQuantity] = useState('');
  const [exclusive, setExclusive] = useState(false);

  async function submit() {
    if (!name.trim()) return toast.show('Package name is required.', 'error');
    if (!priceMajor.trim()) return toast.show('Enter a package price.', 'error');
    if (maxQuantity.trim() && (!Number.isInteger(Number(maxQuantity)) || Number(maxQuantity) < 1)) {
      return toast.show('Max quantity must be a positive whole number.', 'error');
    }
    try {
      await create.mutateAsync({
        name,
        description,
        priceMajor,
        currency: 'USD',
        maxQuantity,
        exclusive,
      });
      setName('');
      setPriceMajor('');
      setDescription('');
      setMaxQuantity('');
      setExclusive(false);
      onClose();
      toast.show('Sponsorship package created.', 'success');
    } catch {
      toast.show('Could not create the package. Check the price and try again.', 'error');
    }
  }

  return (
    <Modal visible={visible} onClose={onClose}>
      <ThemedText type="smallBold" style={styles.modalTitle}>New sponsorship package</ThemedText>
      <ScrollView style={styles.modalScroll} keyboardShouldPersistTaps="handled">
        <View style={styles.form}>
          <Field label="Name">
            <TextInput
              value={name}
              onChangeText={setName}
              placeholder="Gold Sponsor"
              placeholderTextColor={theme.textSecondary}
              style={[styles.input, { color: theme.text, backgroundColor: theme.backgroundElement }]}
            />
          </Field>
          <Field label="Price (USD)">
            <TextInput
              value={priceMajor}
              onChangeText={setPriceMajor}
              keyboardType="decimal-pad"
              placeholder="500.00"
              placeholderTextColor={theme.textSecondary}
              style={[styles.input, { color: theme.text, backgroundColor: theme.backgroundElement }]}
            />
          </Field>
          <Field label="Max quantity (blank = unlimited)">
            <TextInput
              value={maxQuantity}
              onChangeText={setMaxQuantity}
              keyboardType="number-pad"
              placeholder="5"
              placeholderTextColor={theme.textSecondary}
              style={[styles.input, { color: theme.text, backgroundColor: theme.backgroundElement }]}
            />
          </Field>
          <Field label="Description">
            <TextInput
              value={description}
              onChangeText={setDescription}
              multiline
              placeholder="Optional"
              placeholderTextColor={theme.textSecondary}
              style={[styles.input, styles.multiline, { color: theme.text, backgroundColor: theme.backgroundElement }]}
            />
          </Field>
          <View style={styles.switchRow}>
            <View style={styles.flexOne}>
              <ThemedText type="smallBold">Exclusive package</ThemedText>
              <ThemedText type="small" themeColor="textSecondary">Only one confirmed sponsor can hold it.</ThemedText>
            </View>
            <Switch value={exclusive} onValueChange={setExclusive} />
          </View>
          <Button disabled={create.isPending} onPress={submit}>
            {create.isPending ? 'Creating…' : 'Create Package'}
          </Button>
        </View>
      </ScrollView>
    </Modal>
  );
}

function Sponsors({
  organizationId,
  reviewOnly,
}: {
  organizationId: string | null;
  reviewOnly: boolean;
}) {
  const theme = useTheme();
  const toast = useToast();
  const [query, setQuery] = useState('');
  const [status, setStatus] = useState<SponsorshipStatus | ''>('');
  const [reviewStatus, setReviewStatus] = useState<SponsorshipReviewStatus | ''>(
    reviewOnly ? 'PENDING_REVIEW' : '',
  );
  const [paymentSource, setPaymentSource] = useState<'STRIPE' | 'OFFLINE' | ''>('');
  const [sort, setSort] = useState<SponsorshipSort>(reviewOnly ? 'OLDEST' : 'NEWEST');
  const [filterOpen, setFilterOpen] = useState(false);
  const [sortOpen, setSortOpen] = useState(false);
  const [rejectTarget, setRejectTarget] = useState<{ id: string; name: string } | null>(null);

  const result = useInfiniteSponsorships(organizationId, {
    q: query,
    status,
    reviewStatus: reviewOnly ? 'PENDING_REVIEW' : reviewStatus,
    paymentSource,
    sort,
  });
  const items = useMemo(() => flattenSponsorshipPages(result.data?.pages), [result.data?.pages]);
  const total = result.data?.pages[0]?.totalElements ?? 0;
  const approve = useApproveSponsorship(organizationId);
  const reject = useRejectSponsorship(organizationId);

  const filters = [
    ...(status ? [status === 'CONFIRMED' ? 'Confirmed' : 'Refunded'] : []),
    ...(!reviewOnly && reviewStatus ? [reviewLabel(reviewStatus)] : []),
    ...(paymentSource ? [paymentSource === 'STRIPE' ? 'Online card' : 'Recorded offline'] : []),
  ];
  const sortLabel =
    sort === 'NEWEST' ? 'Newest'
    : sort === 'OLDEST' ? 'Oldest'
    : sort === 'SPONSOR_ASC' ? 'Sponsor A–Z'
    : sort === 'AMOUNT_ASC' ? 'Amount low–high'
    : sort === 'AMOUNT_DESC' ? 'Amount high–low'
    : sort === 'PACKAGE_ASC' ? 'Package A–Z'
    : 'Review status';

  return (
    <>
      <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
        <ListControls
          query={query}
          onChangeQuery={setQuery}
          searchPlaceholder="Search sponsor, company, package, email, or ID"
          resultCount={total}
          activeFilters={filters}
          onRemoveFilter={(index) => {
            let cursor = 0;
            if (status) {
              if (index === cursor) return setStatus('');
              cursor += 1;
            }
            if (!reviewOnly && reviewStatus) {
              if (index === cursor) return setReviewStatus('');
              cursor += 1;
            }
            if (paymentSource && index === cursor) setPaymentSource('');
          }}
          onClearFilters={() => {
            setQuery('');
            setStatus('');
            if (!reviewOnly) setReviewStatus('');
            setPaymentSource('');
            setSort(reviewOnly ? 'OLDEST' : 'NEWEST');
          }}
          onPressFilter={() => setFilterOpen(true)}
          onPressSort={() => setSortOpen(true)}
          sortLabel={sortLabel}
        />

        {result.isLoading && <LoadingState label={reviewOnly ? 'Loading pending review…' : 'Loading sponsors…'} />}
        {result.isError && (
          <ErrorState
            message={reviewOnly ? 'Could not load sponsorships awaiting review.' : 'Could not load sponsors.'}
            onRetry={() => result.refetch()}
          />
        )}
        {!result.isLoading && !result.isError && items.length === 0 && (
          <EmptyState
            title={query.trim() || filters.length > 0 ? 'No results found' : reviewOnly ? 'Nothing awaiting review' : 'No sponsors yet'}
            description={
              query.trim() || filters.length > 0
                ? 'Try changing your search or filters.'
                : reviewOnly
                  ? 'Confirmed sponsorships awaiting approval will appear here.'
                  : 'Confirmed sponsor records will appear here.'
            }
          />
        )}

        {items.map((item) => (
          <ThemedView key={item.id} type="backgroundElement" style={styles.card}>
            <View style={styles.cardTop}>
              <View style={styles.flexOne}>
                <ThemedText type="smallBold">{item.sponsorName}</ThemedText>
                <ThemedText type="small" themeColor="textSecondary">
                  {item.sponsorCompanyName ? `${item.sponsorCompanyName} · ` : ''}
                  {item.packageName}
                </ThemedText>
                {item.sponsorContactEmail && (
                  <ThemedText type="small" themeColor="textSecondary">
                    {item.sponsorContactEmail}
                  </ThemedText>
                )}
              </View>
              <View style={styles.amountColumn}>
                <ThemedText type="smallBold">
                  {formatMoneyMinorUnits(item.amountMinor, item.currency)}
                </ThemedText>
                <ThemedText type="small" themeColor="textSecondary">
                  {reviewLabel(item.reviewStatus)}
                </ThemedText>
              </View>
            </View>
            <ThemedText type="small" themeColor="textSecondary">
              {item.paymentSource === 'STRIPE' ? 'Online card' : 'Recorded offline'}
              {item.status === 'REFUNDED' ? ' · Refunded' : ''}
              {' · '}
              {new Date(item.confirmedAt ?? item.createdAt).toLocaleDateString()}
            </ThemedText>
            {item.reviewStatus === 'PENDING_REVIEW' && (
              <View style={styles.actions}>
                <Button
                  disabled={approve.isPending}
                  onPress={() =>
                    approve.mutate(item.id, {
                      onSuccess: () => toast.show('Sponsorship approved.', 'success'),
                      onError: () => toast.show('Could not approve the sponsorship.', 'error'),
                    })
                  }>
                  Approve
                </Button>
                <Button
                  variant="secondary"
                  disabled={reject.isPending}
                  onPress={() => setRejectTarget({ id: item.id, name: item.sponsorName })}>
                  Reject
                </Button>
              </View>
            )}
          </ThemedView>
        ))}

        <ListFooter
          loadedCount={items.length}
          totalCount={total}
          hasMore={!!result.hasNextPage}
          loadingMore={result.isFetchingNextPage}
          onLoadMore={() => result.fetchNextPage()}
        />
      </ScrollView>

      <Modal visible={filterOpen} onClose={() => setFilterOpen(false)}>
        <ThemedText type="smallBold" style={styles.modalTitle}>Filter sponsorships</ThemedText>
        <ScrollView style={styles.modalScroll}>
          {!reviewOnly && (
            <>
              <ThemedText type="smallBold" style={styles.groupTitle}>Review status</ThemedText>
              <Option selected={!reviewStatus} label="All review statuses" onPress={() => setReviewStatus('')} />
              {(['PENDING_REVIEW', 'APPROVED', 'REJECTED'] as SponsorshipReviewStatus[]).map((value) => (
                <Option key={value} selected={reviewStatus === value} label={reviewLabel(value)} onPress={() => setReviewStatus(value)} />
              ))}
            </>
          )}
          <ThemedText type="smallBold" style={styles.groupTitle}>Payment status</ThemedText>
          <Option selected={!status} label="All payment statuses" onPress={() => setStatus('')} />
          <Option selected={status === 'CONFIRMED'} label="Confirmed" onPress={() => setStatus('CONFIRMED')} />
          <Option selected={status === 'REFUNDED'} label="Refunded" onPress={() => setStatus('REFUNDED')} />

          <ThemedText type="smallBold" style={styles.groupTitle}>Payment source</ThemedText>
          <Option selected={!paymentSource} label="All payment sources" onPress={() => setPaymentSource('')} />
          <Option selected={paymentSource === 'STRIPE'} label="Online card" onPress={() => setPaymentSource('STRIPE')} />
          <Option selected={paymentSource === 'OFFLINE'} label="Recorded offline" onPress={() => setPaymentSource('OFFLINE')} />
        </ScrollView>
        <Pressable style={styles.done} onPress={() => setFilterOpen(false)}>
          <ThemedText type="smallBold">Done</ThemedText>
        </Pressable>
      </Modal>

      <Modal visible={sortOpen} onClose={() => setSortOpen(false)}>
        <ThemedText type="smallBold" style={styles.modalTitle}>Sort sponsorships</ThemedText>
        {([
          ['NEWEST', 'Newest'],
          ['OLDEST', 'Oldest'],
          ['SPONSOR_ASC', 'Sponsor A–Z'],
          ['AMOUNT_DESC', 'Amount high–low'],
          ['AMOUNT_ASC', 'Amount low–high'],
          ['PACKAGE_ASC', 'Package A–Z'],
          ['REVIEW_STATUS_ASC', 'Review status'],
        ] as const).map(([value, label]) => (
          <Option
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

      <ConfirmDialog
        visible={!!rejectTarget}
        title="Reject sponsorship?"
        message={`${rejectTarget?.name ?? 'This sponsor'} will be rejected. If this sponsorship was paid online, the existing backend workflow returns the payment through its original Stripe refund path.`}
        confirmLabel="Reject"
        destructive
        onCancel={() => setRejectTarget(null)}
        onConfirm={() => {
          if (!rejectTarget) return;
          reject.mutate(rejectTarget.id, {
            onSuccess: () => {
              toast.show('Sponsorship rejected.', 'success');
              setRejectTarget(null);
            },
            onError: () => toast.show('Could not reject the sponsorship.', 'error'),
          });
        }}
      />
    </>
  );

  function Option({
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

function reviewLabel(value: SponsorshipReviewStatus) {
  if (value === 'PENDING_REVIEW') return 'Pending review';
  if (value === 'APPROVED') return 'Approved';
  return 'Rejected';
}

function Segment({
  label,
  selected,
  onPress,
}: {
  label: string;
  selected: boolean;
  onPress: () => void;
}) {
  return (
    <Pressable
      accessibilityRole="tab"
      accessibilityState={{ selected }}
      onPress={onPress}
      style={[styles.segmentButton, selected && styles.segmentSelected]}>
      <ThemedText type="smallBold" style={selected ? styles.segmentTextSelected : undefined}>
        {label}
      </ThemedText>
    </Pressable>
  );
}

function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <View style={styles.field}>
      <ThemedText type="small" themeColor="textSecondary">{label}</ThemedText>
      {children}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  segment: {
    flexDirection: 'row',
    gap: Spacing.one,
    paddingHorizontal: Spacing.four,
    paddingBottom: Spacing.three,
  },
  segmentButton: {
    flex: 1,
    minHeight: 42,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: Spacing.two,
  },
  segmentSelected: { backgroundColor: Brand.championshipGold },
  segmentTextSelected: { color: Brand.navy },
  content: {
    paddingHorizontal: Spacing.four,
    paddingBottom: Spacing.six,
    gap: Spacing.three,
  },
  flexOne: { flex: 1 },
  card: {
    borderRadius: Spacing.three,
    padding: Spacing.three,
    gap: Spacing.two,
  },
  cardTop: { flexDirection: 'row', alignItems: 'flex-start', gap: Spacing.two },
  amountColumn: { alignItems: 'flex-end', gap: 2 },
  actions: { flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.two },
  modalTitle: { marginBottom: Spacing.two },
  modalScroll: { maxHeight: 440 },
  groupTitle: { marginTop: Spacing.two, marginBottom: Spacing.one },
  option: {
    minHeight: 46,
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
  form: { gap: Spacing.three },
  field: { gap: Spacing.one },
  input: {
    minHeight: 46,
    borderRadius: Spacing.two,
    paddingHorizontal: Spacing.three,
    paddingVertical: Spacing.two,
  },
  multiline: { minHeight: 84, textAlignVertical: 'top' },
  switchRow: { flexDirection: 'row', alignItems: 'center', gap: Spacing.three },
});
