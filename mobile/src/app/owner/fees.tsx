import Ionicons from '@expo/vector-icons/Ionicons';
import { useMemo, useState, type ReactNode } from 'react';
import {
  Pressable,
  ScrollView,
  StyleSheet,
  TextInput,
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
import { useDashboardContext } from '@/features/dashboard/api';
import {
  flattenPages,
  useArchiveFeeTemplate,
  useCreateFeeTemplate,
  useInfiniteFeeTemplateSearch,
  useInfiniteOrganizationFeeSearch,
  type FeeAssignmentSearchSort,
  type FeeAssignmentStatus,
  type FeeTemplateSearchSort,
} from '@/features/fees/searchApi';
import { Brand, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { formatMoneyMinorUnits } from '@/lib/money';

type Section = 'collections' | 'templates';

const ASSIGNMENT_STATUS_LABELS: Record<FeeAssignmentStatus, string> = {
  OPEN: 'Open',
  PARTIALLY_PAID: 'Partially paid',
  PAID: 'Paid',
  WAIVED: 'Waived',
  CANCELLED: 'Cancelled',
};

export default function OwnerFeesScreen() {
  const dashboardContext = useDashboardContext(true);
  const organizationId = dashboardContext.data?.organizationId ?? null;
  const [section, setSection] = useState<Section>('collections');

  return (
    <ThemedView style={styles.container}>
      <ScreenHeader title="Fees & Collections" />
      <View style={styles.segment}>
        <SegmentButton
          label="Collections"
          selected={section === 'collections'}
          onPress={() => setSection('collections')}
        />
        <SegmentButton
          label="Fee Templates"
          selected={section === 'templates'}
          onPress={() => setSection('templates')}
        />
      </View>
      {section === 'collections' ? (
        <CollectionsSection organizationId={organizationId} />
      ) : (
        <TemplatesSection organizationId={organizationId} />
      )}
    </ThemedView>
  );
}

function CollectionsSection({ organizationId }: { organizationId: string | null }) {
  const theme = useTheme();
  const [query, setQuery] = useState('');
  const [status, setStatus] = useState<FeeAssignmentStatus | ''>('');
  const [overdueOnly, setOverdueOnly] = useState(false);
  const [sort, setSort] = useState<FeeAssignmentSearchSort>('DUE_DATE_ASC');
  const [filterOpen, setFilterOpen] = useState(false);
  const [sortOpen, setSortOpen] = useState(false);

  const result = useInfiniteOrganizationFeeSearch(organizationId, {
    q: query,
    status,
    overdueOnly,
    sort,
  });
  const items = useMemo(() => flattenPages(result.data?.pages), [result.data?.pages]);
  const total = result.data?.pages[0]?.totalElements ?? 0;
  const activeFilters = [
    ...(status ? [ASSIGNMENT_STATUS_LABELS[status]] : []),
    ...(overdueOnly ? ['Overdue only'] : []),
  ];
  const sortLabel =
    sort === 'DUE_DATE_ASC' ? 'Due soonest'
    : sort === 'DUE_DATE_DESC' ? 'Due latest'
    : sort === 'BALANCE_DESC' ? 'Balance high–low'
    : sort === 'BALANCE_ASC' ? 'Balance low–high'
    : sort === 'HOUSEHOLD_ASC' ? 'Household A–Z'
    : sort === 'DESCRIPTION_ASC' ? 'Fee A–Z'
    : sort === 'NEWEST' ? 'Newest'
    : 'Oldest';

  return (
    <>
      <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
        <ListControls
          query={query}
          onChangeQuery={setQuery}
          searchPlaceholder="Search household, athlete, or fee"
          resultCount={total}
          activeFilters={activeFilters}
          onRemoveFilter={(index) => {
            if (status && index === 0) setStatus('');
            else setOverdueOnly(false);
          }}
          onClearFilters={() => {
            setQuery('');
            setStatus('');
            setOverdueOnly(false);
            setSort('DUE_DATE_ASC');
          }}
          onPressFilter={() => setFilterOpen(true)}
          onPressSort={() => setSortOpen(true)}
          sortLabel={sortLabel}
        />

        {result.isLoading && <LoadingState label="Loading collections…" />}
        {result.isError && (
          <ErrorState message="Could not load collections." onRetry={() => result.refetch()} />
        )}
        {!result.isLoading && !result.isError && items.length === 0 && (
          <EmptyState
            title={query.trim() || activeFilters.length > 0 ? 'No results found' : 'Nothing to collect'}
            description={
              query.trim() || activeFilters.length > 0
                ? 'Try changing your search or filters.'
                : 'Fee assignments will appear here as households are charged.'
            }
          />
        )}

        {items.map((item) => (
          <ThemedView key={item.id} type="backgroundElement" style={styles.collectionCard}>
            <View style={styles.flexOne}>
              <ThemedText type="smallBold">{item.householdName}</ThemedText>
              <ThemedText type="small">{item.description}</ThemedText>
              <ThemedText type="small" themeColor="textSecondary">
                {item.participantName ? `${item.participantName} · ` : ''}
                {ASSIGNMENT_STATUS_LABELS[item.status as FeeAssignmentStatus] ?? item.status}
                {item.dueDate
                  ? ` · due ${new Date(`${item.dueDate}T12:00:00`).toLocaleDateString()}`
                  : ''}
              </ThemedText>
            </View>
            <View style={styles.moneyColumn}>
              <ThemedText type="smallBold">
                {formatMoneyMinorUnits(item.balanceMinor, item.currency)}
              </ThemedText>
              <ThemedText type="small" themeColor="textSecondary">balance</ThemedText>
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
        <ThemedText type="smallBold" style={styles.modalTitle}>Filter collections</ThemedText>
        <ScrollView style={styles.modalScroll}>
          <Option selected={!status} label="All statuses" onPress={() => setStatus('')} />
          {(Object.keys(ASSIGNMENT_STATUS_LABELS) as FeeAssignmentStatus[]).map((value) => (
            <Option
              key={value}
              selected={status === value}
              label={ASSIGNMENT_STATUS_LABELS[value]}
              onPress={() => setStatus(value)}
            />
          ))}
          <Option
            selected={overdueOnly}
            label="Overdue only"
            onPress={() => setOverdueOnly((value) => !value)}
          />
        </ScrollView>
        <Pressable style={styles.done} onPress={() => setFilterOpen(false)}>
          <ThemedText type="smallBold">Done</ThemedText>
        </Pressable>
      </Modal>

      <Modal visible={sortOpen} onClose={() => setSortOpen(false)}>
        <ThemedText type="smallBold" style={styles.modalTitle}>Sort collections</ThemedText>
        {([
          ['DUE_DATE_ASC', 'Due soonest'],
          ['DUE_DATE_DESC', 'Due latest'],
          ['BALANCE_DESC', 'Balance high–low'],
          ['BALANCE_ASC', 'Balance low–high'],
          ['HOUSEHOLD_ASC', 'Household A–Z'],
          ['DESCRIPTION_ASC', 'Fee A–Z'],
          ['NEWEST', 'Newest'],
          ['OLDEST', 'Oldest'],
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

function TemplatesSection({ organizationId }: { organizationId: string | null }) {
  const theme = useTheme();
  const toast = useToast();
  const [query, setQuery] = useState('');
  const [status, setStatus] = useState<'ACTIVE' | 'ARCHIVED' | ''>('ACTIVE');
  const [sort, setSort] = useState<FeeTemplateSearchSort>('NAME_ASC');
  const [filterOpen, setFilterOpen] = useState(false);
  const [sortOpen, setSortOpen] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);
  const [name, setName] = useState('');
  const [amountMajor, setAmountMajor] = useState('');
  const [description, setDescription] = useState('');

  const result = useInfiniteFeeTemplateSearch(organizationId, { q: query, status, sort });
  const create = useCreateFeeTemplate(organizationId);
  const archive = useArchiveFeeTemplate(organizationId);
  const items = useMemo(() => flattenPages(result.data?.pages), [result.data?.pages]);
  const total = result.data?.pages[0]?.totalElements ?? 0;
  const activeFilters = status !== 'ACTIVE' ? [status === 'ARCHIVED' ? 'Archived' : 'All statuses'] : [];
  const sortLabel =
    sort === 'NAME_ASC' ? 'Name A–Z'
    : sort === 'NAME_DESC' ? 'Name Z–A'
    : sort === 'AMOUNT_ASC' ? 'Amount low–high'
    : sort === 'AMOUNT_DESC' ? 'Amount high–low'
    : sort === 'NEWEST' ? 'Newest'
    : 'Oldest';

  async function createTemplate() {
    if (!name.trim()) return toast.show('Template name is required.', 'error');
    if (!amountMajor.trim()) return toast.show('Enter a fee amount.', 'error');
    try {
      await create.mutateAsync({
        name,
        description,
        amountMajor,
        currency: 'USD',
      });
      setName('');
      setAmountMajor('');
      setDescription('');
      setCreateOpen(false);
      toast.show('Fee template created.', 'success');
    } catch {
      toast.show('Could not create the fee template. Check the amount and try again.', 'error');
    }
  }

  return (
    <>
      <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
        <ListControls
          query={query}
          onChangeQuery={setQuery}
          searchPlaceholder="Search fee templates"
          resultCount={total}
          activeFilters={activeFilters}
          onRemoveFilter={() => setStatus('ACTIVE')}
          onClearFilters={() => {
            setQuery('');
            setStatus('ACTIVE');
            setSort('NAME_ASC');
          }}
          onPressFilter={() => setFilterOpen(true)}
          onPressSort={() => setSortOpen(true)}
          sortLabel={sortLabel}
        />

        <Button onPress={() => setCreateOpen(true)}>Add Fee Template</Button>

        {result.isLoading && <LoadingState label="Loading fee templates…" />}
        {result.isError && (
          <ErrorState message="Could not load fee templates." onRetry={() => result.refetch()} />
        )}
        {!result.isLoading && !result.isError && items.length === 0 && (
          <EmptyState
            title={query.trim() || activeFilters.length > 0 ? 'No results found' : 'No fee templates yet'}
            description={
              query.trim() || activeFilters.length > 0
                ? 'Try changing your search or status filter.'
                : 'Create reusable fee templates for registration, uniforms, travel, and other charges.'
            }
          />
        )}

        {items.map((item) => (
          <ThemedView key={item.id} type="backgroundElement" style={styles.templateCard}>
            <View style={styles.flexOne}>
              <View style={styles.row}>
                <ThemedText type="smallBold">{item.name}</ThemedText>
                {item.status === 'ARCHIVED' && (
                  <ThemedText type="small" themeColor="textSecondary">Archived</ThemedText>
                )}
              </View>
              <ThemedText type="small" themeColor="textSecondary">
                {formatMoneyMinorUnits(item.amountMinor, item.currency)}
                {item.description ? ` · ${item.description}` : ''}
              </ThemedText>
            </View>
            {item.status === 'ACTIVE' && (
              <Pressable
                accessibilityRole="button"
                accessibilityLabel={`Archive ${item.name}`}
                disabled={archive.isPending}
                onPress={() =>
                  archive.mutate(item.id, {
                    onSuccess: () => toast.show('Fee template archived.', 'success'),
                    onError: () => toast.show('Could not archive the template.', 'error'),
                  })
                }>
                <Ionicons name="archive-outline" size={21} color={theme.textSecondary} />
              </Pressable>
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
        <ThemedText type="smallBold" style={styles.modalTitle}>Filter fee templates</ThemedText>
        <Option selected={!status} label="All statuses" onPress={() => { setStatus(''); setFilterOpen(false); }} />
        <Option selected={status === 'ACTIVE'} label="Active" onPress={() => { setStatus('ACTIVE'); setFilterOpen(false); }} />
        <Option selected={status === 'ARCHIVED'} label="Archived" onPress={() => { setStatus('ARCHIVED'); setFilterOpen(false); }} />
      </Modal>

      <Modal visible={sortOpen} onClose={() => setSortOpen(false)}>
        <ThemedText type="smallBold" style={styles.modalTitle}>Sort fee templates</ThemedText>
        {([
          ['NAME_ASC', 'Name A–Z'],
          ['NAME_DESC', 'Name Z–A'],
          ['AMOUNT_ASC', 'Amount low–high'],
          ['AMOUNT_DESC', 'Amount high–low'],
          ['NEWEST', 'Newest'],
          ['OLDEST', 'Oldest'],
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

      <Modal visible={createOpen} onClose={() => setCreateOpen(false)}>
        <ThemedText type="smallBold" style={styles.modalTitle}>New fee template</ThemedText>
        <View style={styles.form}>
          <Field label="Name">
            <TextInput
              value={name}
              onChangeText={setName}
              placeholder="Spring Registration"
              placeholderTextColor={theme.textSecondary}
              style={[styles.input, { color: theme.text, backgroundColor: theme.backgroundElement }]}
            />
          </Field>
          <Field label="Amount (USD)">
            <TextInput
              value={amountMajor}
              onChangeText={setAmountMajor}
              keyboardType="decimal-pad"
              placeholder="150.00"
              placeholderTextColor={theme.textSecondary}
              style={[styles.input, { color: theme.text, backgroundColor: theme.backgroundElement }]}
            />
          </Field>
          <Field label="Description">
            <TextInput
              value={description}
              onChangeText={setDescription}
              placeholder="Optional"
              placeholderTextColor={theme.textSecondary}
              style={[styles.input, { color: theme.text, backgroundColor: theme.backgroundElement }]}
            />
          </Field>
          <Button disabled={create.isPending} onPress={createTemplate}>
            {create.isPending ? 'Creating…' : 'Create Template'}
          </Button>
        </View>
      </Modal>
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

function SegmentButton({
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
      style={[styles.segmentButton, selected && styles.segmentButtonSelected]}>
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
  segmentButtonSelected: { backgroundColor: Brand.championshipGold },
  segmentTextSelected: { color: Brand.navy },
  content: {
    paddingHorizontal: Spacing.four,
    paddingBottom: Spacing.six,
    gap: Spacing.three,
  },
  collectionCard: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.three,
    borderRadius: Spacing.three,
    padding: Spacing.three,
  },
  templateCard: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.three,
    borderRadius: Spacing.three,
    padding: Spacing.three,
  },
  flexOne: { flex: 1 },
  row: { flexDirection: 'row', flexWrap: 'wrap', alignItems: 'center', gap: Spacing.two },
  moneyColumn: { alignItems: 'flex-end' },
  modalTitle: { marginBottom: Spacing.two },
  modalScroll: { maxHeight: 430 },
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
});
