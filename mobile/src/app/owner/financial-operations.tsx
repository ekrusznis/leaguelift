import Ionicons from '@expo/vector-icons/Ionicons';
import * as Crypto from 'expo-crypto';
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
import { useDashboardContext } from '@/features/dashboard/api';
import { useCampaigns } from '@/features/fundraising/api';
import type { Campaign } from '@/features/fundraising/types';
import {
  flattenFinancialOperationPages,
  useCreateOfflineContribution,
  useCreateOfflineOrder,
  useCreateOfflineSponsorship,
  useExecuteFinancialCorrection,
  useInfiniteFinancialCorrections,
  useInfiniteOfflineFinancialRecords,
  useInfiniteReconciliationIssues,
  useInfiniteReconciliationRuns,
  usePreviewFinancialCorrection,
  useRunReconciliation,
  useVerifyOfflineFinancialRecord,
  type FinancialCorrectionTargetType,
  type FinancialCorrectionType,
  type OfflineFinancialRecordType,
  type OfflinePaymentMethod,
  type OfflineVerificationStatus,
  type ReconciliationRunStatus,
  type ReconciliationSeverity,
} from '@/features/financial-operations/api';
import {
  useProductVariants,
  useStoreProducts,
  useStores,
  type ProductResponse,
} from '@/features/orders/api';
import {
  flattenSponsorshipPages,
  useInfiniteSponsorshipPackages,
  type SponsorshipPackage,
} from '@/features/sponsorship/api';
import { useTheme } from '@/hooks/use-theme';
import { formatMoneyMinorUnits, parseMajorAmountToMinorUnits } from '@/lib/money';

type Section = 'offline' | 'corrections' | 'reconciliation';

export default function OwnerFinancialOperationsScreen() {
  const dashboard = useDashboardContext(true);
  const organizationId = dashboard.data?.organizationId ?? null;
  const [section, setSection] = useState<Section>('offline');

  return (
    <ThemedView style={styles.container}>
      <ScreenHeader title="Financial Operations" />
      <View style={styles.segment}>
        <Segment label="Offline" selected={section === 'offline'} onPress={() => setSection('offline')} />
        <Segment label="Corrections" selected={section === 'corrections'} onPress={() => setSection('corrections')} />
        <Segment label="Reconcile" selected={section === 'reconciliation'} onPress={() => setSection('reconciliation')} />
      </View>
      {section === 'offline' && <OfflineRecords organizationId={organizationId} />}
      {section === 'corrections' && <Corrections organizationId={organizationId} />}
      {section === 'reconciliation' && <Reconciliation organizationId={organizationId} />}
    </ThemedView>
  );
}

function OfflineRecords({ organizationId }: { organizationId: string | null }) {
  const toast = useToast();
  const [query, setQuery] = useState('');
  const [status, setStatus] = useState<OfflineVerificationStatus | ''>('');
  const [recordType, setRecordType] = useState<OfflineFinancialRecordType | ''>('');
  const [paymentMethod, setPaymentMethod] = useState<OfflinePaymentMethod | ''>('');
  const [sort, setSort] = useState<'newest' | 'oldest'>('newest');
  const [filterOpen, setFilterOpen] = useState(false);
  const [sortOpen, setSortOpen] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);
  const result = useInfiniteOfflineFinancialRecords(organizationId, {
    q: query,
    verificationStatus: status,
    recordType,
    paymentMethod,
    sort,
  });
  const records = useMemo(
    () => flattenFinancialOperationPages(result.data?.pages),
    [result.data?.pages],
  );
  const total = result.data?.pages[0]?.totalElements ?? 0;
  const verify = useVerifyOfflineFinancialRecord(organizationId);
  const filters = [
    ...(status ? [humanize(status)] : []),
    ...(recordType ? [humanize(recordType)] : []),
    ...(paymentMethod ? [humanize(paymentMethod)] : []),
  ];

  return (
    <>
      <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
        <ThemedView type="backgroundElement" style={styles.notice}>
          <ThemedText type="smallBold">Recorded in Rally26 — not processed by Rally26</ThemedText>
          <ThemedText type="small" themeColor="textSecondary">
            Use this only for money already received outside Rally26. Verification records the
            transaction but does not create Stripe payment or payout activity.
          </ThemedText>
        </ThemedView>
        <ListControls
          query={query}
          onChangeQuery={setQuery}
          searchPlaceholder="Search payer, reference, details"
          resultCount={total}
          activeFilters={filters}
          onClearFilters={() => {
            setQuery('');
            setStatus('');
            setRecordType('');
            setPaymentMethod('');
            setSort('newest');
          }}
          onPressFilter={() => setFilterOpen(true)}
          onPressSort={() => setSortOpen(true)}
          sortLabel={sort === 'newest' ? 'Newest' : 'Oldest'}
        />
        <Button onPress={() => setCreateOpen(true)}>Record offline transaction</Button>
        {result.isLoading && <LoadingState label="Loading offline financial records…" />}
        {result.isError && (
          <ErrorState message="Could not load offline financial records." onRetry={() => result.refetch()} />
        )}
        {!result.isLoading && !result.isError && records.length === 0 && (
          <EmptyState
            title={query.trim() || filters.length ? 'No results found' : 'No offline financial records'}
            description={
              query.trim() || filters.length
                ? 'Try changing your search or filters.'
                : 'Transactions recorded outside Rally26 will appear here.'
            }
          />
        )}
        {records.map((record) => (
          <ThemedView key={record.id} type="backgroundElement" style={styles.card}>
            <View style={styles.cardTop}>
              <View style={styles.flexOne}>
                <ThemedText type="smallBold">{record.displayLabel}</ThemedText>
                <ThemedText type="small" themeColor="textSecondary">
                  {humanize(record.recordType)} · {humanize(record.paymentMethod)}
                </ThemedText>
                <ThemedText type="small" themeColor="textSecondary">
                  {record.payerName ?? 'No payer name'}
                  {record.paymentReference ? ` · ${record.paymentReference}` : ''}
                </ThemedText>
                <ThemedText type="small" themeColor="textSecondary">
                  {new Date(record.receivedAt).toLocaleDateString()} · {humanize(record.verificationStatus)}
                </ThemedText>
              </View>
              <ThemedText type="smallBold">
                {formatMoneyMinorUnits(record.amountMinor, record.currency)}
              </ThemedText>
            </View>
            {record.verificationStatus === 'PENDING_VERIFICATION' && (
              <Button
                variant="secondary"
                disabled={verify.isPending}
                onPress={() =>
                  verify.mutate(record.id, {
                    onSuccess: () => toast.show('Offline transaction verified.', 'success'),
                    onError: () => toast.show('Could not verify the transaction.', 'error'),
                  })
                }>
                Verify
              </Button>
            )}
          </ThemedView>
        ))}
        <ListFooter
          loadedCount={records.length}
          totalCount={total}
          hasMore={!!result.hasNextPage}
          loadingMore={result.isFetchingNextPage}
          onLoadMore={() => result.fetchNextPage()}
        />
      </ScrollView>
      <Modal visible={filterOpen} onClose={() => setFilterOpen(false)}>
        <ModalTitle>Filter offline records</ModalTitle>
        <ScrollView style={styles.modalScroll}>
          <ChoiceHeading>Status</ChoiceHeading>
          {(['', 'PENDING_VERIFICATION', 'VERIFIED', 'REVERSED'] as const).map((value) => (
            <Option
              key={value || 'all-status'}
              selected={status === value}
              label={value ? humanize(value) : 'All statuses'}
              onPress={() => setStatus(value)}
            />
          ))}
          <ChoiceHeading>Type</ChoiceHeading>
          {(['', 'CONTRIBUTION', 'SPONSORSHIP', 'ORDER'] as const).map((value) => (
            <Option
              key={value || 'all-type'}
              selected={recordType === value}
              label={value ? humanize(value) : 'All types'}
              onPress={() => setRecordType(value)}
            />
          ))}
          <ChoiceHeading>Payment method</ChoiceHeading>
          {(['', 'CASH', 'CHECK', 'ACH', 'EXTERNAL_CARD', 'VENMO', 'ZELLE', 'OTHER'] as const).map((value) => (
            <Option
              key={value || 'all-method'}
              selected={paymentMethod === value}
              label={value ? humanize(value) : 'All methods'}
              onPress={() => setPaymentMethod(value)}
            />
          ))}
        </ScrollView>
        <Done onPress={() => setFilterOpen(false)} />
      </Modal>
      <Modal visible={sortOpen} onClose={() => setSortOpen(false)}>
        <ModalTitle>Sort offline records</ModalTitle>
        <Option selected={sort === 'newest'} label="Newest first" onPress={() => { setSort('newest'); setSortOpen(false); }} />
        <Option selected={sort === 'oldest'} label="Oldest first" onPress={() => { setSort('oldest'); setSortOpen(false); }} />
      </Modal>
      <RecordOfflineModal
        visible={createOpen}
        organizationId={organizationId}
        onClose={() => setCreateOpen(false)}
      />
    </>
  );
}

function Corrections({ organizationId }: { organizationId: string | null }) {
  const [query, setQuery] = useState('');
  const [targetType, setTargetType] = useState<FinancialCorrectionTargetType | ''>('');
  const [correctionType, setCorrectionType] = useState<FinancialCorrectionType | ''>('');
  const [sort, setSort] = useState<'newest' | 'oldest'>('newest');
  const [filterOpen, setFilterOpen] = useState(false);
  const [sortOpen, setSortOpen] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);
  const result = useInfiniteFinancialCorrections(organizationId, {
    q: query,
    targetType,
    correctionType,
    sort,
  });
  const corrections = useMemo(
    () => flattenFinancialOperationPages(result.data?.pages),
    [result.data?.pages],
  );
  const total = result.data?.pages[0]?.totalElements ?? 0;
  const filters = [
    ...(targetType ? [humanize(targetType)] : []),
    ...(correctionType ? [humanize(correctionType)] : []),
  ];

  return (
    <>
      <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
        <ThemedView type="backgroundElement" style={styles.notice}>
          <ThemedText type="smallBold">Preview before money moves</ThemedText>
          <ThemedText type="small" themeColor="textSecondary">
            Refunds and reversals use the existing two-step Preview → Confirm workflow.
          </ThemedText>
        </ThemedView>
        <ListControls
          query={query}
          onChangeQuery={setQuery}
          searchPlaceholder="Search target, reason, reference"
          resultCount={total}
          activeFilters={filters}
          onClearFilters={() => {
            setQuery('');
            setTargetType('');
            setCorrectionType('');
            setSort('newest');
          }}
          onPressFilter={() => setFilterOpen(true)}
          onPressSort={() => setSortOpen(true)}
          sortLabel={sort === 'newest' ? 'Newest' : 'Oldest'}
        />
        <Button onPress={() => setCreateOpen(true)}>New correction</Button>
        {result.isLoading && <LoadingState label="Loading correction history…" />}
        {result.isError && (
          <ErrorState message="Could not load correction history." onRetry={() => result.refetch()} />
        )}
        {!result.isLoading && !result.isError && corrections.length === 0 && (
          <EmptyState
            title={query.trim() || filters.length ? 'No results found' : 'No financial corrections'}
            description={
              query.trim() || filters.length
                ? 'Try changing your search or filters.'
                : 'Completed refunds and reversals will appear here.'
            }
          />
        )}
        {corrections.map((item) => (
          <ThemedView key={item.id} type="backgroundElement" style={styles.card}>
            <View style={styles.cardTop}>
              <View style={styles.flexOne}>
                <ThemedText type="smallBold">
                  {humanize(item.correctionType)} · {humanize(item.targetType)}
                </ThemedText>
                <ThemedText type="small" themeColor="textSecondary">{item.targetId}</ThemedText>
                <ThemedText type="small" themeColor="textSecondary">{item.reason}</ThemedText>
                <ThemedText type="small" themeColor="textSecondary">
                  {new Date(item.createdAt).toLocaleString()}
                </ThemedText>
              </View>
              <ThemedText type="smallBold">
                {formatMoneyMinorUnits(item.amountMinor, item.currency)}
              </ThemedText>
            </View>
          </ThemedView>
        ))}
        <ListFooter
          loadedCount={corrections.length}
          totalCount={total}
          hasMore={!!result.hasNextPage}
          loadingMore={result.isFetchingNextPage}
          onLoadMore={() => result.fetchNextPage()}
        />
      </ScrollView>
      <Modal visible={filterOpen} onClose={() => setFilterOpen(false)}>
        <ModalTitle>Filter corrections</ModalTitle>
        <ScrollView style={styles.modalScroll}>
          <ChoiceHeading>Target</ChoiceHeading>
          {(['', 'CONTRIBUTION', 'SPONSORSHIP', 'ORDER', 'OFFLINE_FINANCIAL_RECORD'] as const).map((value) => (
            <Option
              key={value || 'all-target'}
              selected={targetType === value}
              label={value ? humanize(value) : 'All targets'}
              onPress={() => setTargetType(value)}
            />
          ))}
          <ChoiceHeading>Correction type</ChoiceHeading>
          {(['', 'REFUND', 'REVERSAL'] as const).map((value) => (
            <Option
              key={value || 'all-correction'}
              selected={correctionType === value}
              label={value ? humanize(value) : 'All correction types'}
              onPress={() => setCorrectionType(value)}
            />
          ))}
        </ScrollView>
        <Done onPress={() => setFilterOpen(false)} />
      </Modal>
      <Modal visible={sortOpen} onClose={() => setSortOpen(false)}>
        <ModalTitle>Sort corrections</ModalTitle>
        <Option selected={sort === 'newest'} label="Newest first" onPress={() => { setSort('newest'); setSortOpen(false); }} />
        <Option selected={sort === 'oldest'} label="Oldest first" onPress={() => { setSort('oldest'); setSortOpen(false); }} />
      </Modal>
      <CorrectionModal
        visible={createOpen}
        organizationId={organizationId}
        onClose={() => setCreateOpen(false)}
      />
    </>
  );
}

function Reconciliation({ organizationId }: { organizationId: string | null }) {
  const toast = useToast();
  const [runStatus, setRunStatus] = useState<ReconciliationRunStatus | ''>('');
  const [runSort, setRunSort] = useState<'newest' | 'oldest'>('newest');
  const [issueQuery, setIssueQuery] = useState('');
  const [issueSeverity, setIssueSeverity] = useState<ReconciliationSeverity | ''>('');
  const [issueFilterOpen, setIssueFilterOpen] = useState(false);
  const latestRuns = useInfiniteReconciliationRuns(organizationId, { sort: 'newest' });
  const runs = useInfiniteReconciliationRuns(organizationId, { status: runStatus, sort: runSort });
  const runItems = useMemo(
    () => flattenFinancialOperationPages(runs.data?.pages),
    [runs.data?.pages],
  );
  const latest = latestRuns.data?.pages[0]?.items[0] ?? null;
  const issues = useInfiniteReconciliationIssues(organizationId, latest?.id ?? null, {
    q: issueQuery,
    severity: issueSeverity,
    sort: 'newest',
  });
  const issueItems = useMemo(
    () => flattenFinancialOperationPages(issues.data?.pages),
    [issues.data?.pages],
  );
  const issueTotal = issues.data?.pages[0]?.totalElements ?? 0;
  const runTotal = runs.data?.pages[0]?.totalElements ?? 0;
  const run = useRunReconciliation(organizationId);

  return (
    <>
      <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
        <View style={styles.actionRow}>
          <Button
            disabled={run.isPending}
            onPress={() =>
              run.mutate(undefined, {
                onSuccess: (result) =>
                  toast.show(`Reconciliation completed with ${result.run.issueCount} issue${result.run.issueCount === 1 ? '' : 's'}.`, 'success'),
                onError: () => toast.show('Could not run reconciliation.', 'error'),
              })
            }>
            {run.isPending ? 'Running…' : 'Run reconciliation'}
          </Button>
        </View>
        <ThemedView type="backgroundElement" style={styles.notice}>
          <ThemedText type="smallBold">Latest run</ThemedText>
          {latest ? (
            <>
              <ThemedText type="small" themeColor="textSecondary">
                {humanize(latest.status)} · {latest.issueCount} issues
              </ThemedText>
              <ThemedText type="small" themeColor="textSecondary">
                High {latest.highCount} · Medium {latest.mediumCount} · Low {latest.lowCount}
              </ThemedText>
            </>
          ) : (
            <ThemedText type="small" themeColor="textSecondary">No reconciliation run yet.</ThemedText>
          )}
        </ThemedView>
        <ThemedText type="smallBold">Current issues</ThemedText>
        <ListControls
          query={issueQuery}
          onChangeQuery={setIssueQuery}
          searchPlaceholder="Search reconciliation issues"
          resultCount={issueTotal}
          activeFilters={issueSeverity ? [humanize(issueSeverity)] : []}
          onClearFilters={() => {
            setIssueQuery('');
            setIssueSeverity('');
          }}
          onPressFilter={() => setIssueFilterOpen(true)}
        />
        {issues.isLoading && latest && <LoadingState label="Loading reconciliation issues…" />}
        {issues.isError && (
          <ErrorState message="Could not load reconciliation issues." onRetry={() => issues.refetch()} />
        )}
        {latest && !issues.isLoading && !issues.isError && issueItems.length === 0 && (
          <EmptyState
            title={issueQuery.trim() || issueSeverity ? 'No matching issues' : 'No reconciliation issues'}
            description={
              issueQuery.trim() || issueSeverity
                ? 'Try changing your search or severity filter.'
                : 'The latest reconciliation run found no exceptions.'
            }
          />
        )}
        {issueItems.map((issue) => (
          <ThemedView key={issue.id} type="backgroundElement" style={styles.card}>
            <View style={styles.cardTop}>
              <View style={styles.flexOne}>
                <ThemedText type="smallBold">{issue.title}</ThemedText>
                <ThemedText type="small" themeColor="textSecondary">
                  {humanize(issue.severity)} · {humanize(issue.resourceType)}
                  {issue.resourceId ? ` · ${issue.resourceId}` : ''}
                </ThemedText>
                <ThemedText type="small" themeColor="textSecondary">{issue.detail}</ThemedText>
              </View>
              <Ionicons
                name="warning-outline"
                size={20}
                color={issue.severity === 'HIGH' ? Brand.errorRed : Brand.championshipGold}
              />
            </View>
          </ThemedView>
        ))}
        <ListFooter
          loadedCount={issueItems.length}
          totalCount={issueTotal}
          hasMore={!!issues.hasNextPage}
          loadingMore={issues.isFetchingNextPage}
          onLoadMore={() => issues.fetchNextPage()}
        />
        <View style={styles.sectionHeading}>
          <ThemedText type="smallBold">Run history</ThemedText>
          <View style={styles.inlineChoices}>
            {(['', 'COMPLETED', 'FAILED'] as const).map((value) => (
              <Pressable
                key={value || 'all'}
                onPress={() => setRunStatus(value)}
                style={[styles.chip, runStatus === value && styles.chipSelected]}>
                <ThemedText type={runStatus === value ? 'smallBold' : 'small'}>
                  {value ? humanize(value) : 'All'}
                </ThemedText>
              </Pressable>
            ))}
            <Pressable style={styles.chip} onPress={() => setRunSort((value) => value === 'newest' ? 'oldest' : 'newest')}>
              <ThemedText type="small">{runSort === 'newest' ? 'Newest' : 'Oldest'}</ThemedText>
            </Pressable>
          </View>
        </View>
        {runs.isLoading && <LoadingState label="Loading reconciliation history…" />}
        {runs.isError && (
          <ErrorState message="Could not load reconciliation history." onRetry={() => runs.refetch()} />
        )}
        {runItems.map((item) => (
          <ThemedView key={item.id} type="backgroundElement" style={styles.card}>
            <ThemedText type="smallBold">{humanize(item.status)}</ThemedText>
            <ThemedText type="small" themeColor="textSecondary">
              {new Date(item.startedAt).toLocaleString()} · {item.issueCount} issues
            </ThemedText>
            <ThemedText type="small" themeColor="textSecondary">
              High {item.highCount} · Medium {item.mediumCount} · Low {item.lowCount}
            </ThemedText>
          </ThemedView>
        ))}
        <ListFooter
          loadedCount={runItems.length}
          totalCount={runTotal}
          hasMore={!!runs.hasNextPage}
          loadingMore={runs.isFetchingNextPage}
          onLoadMore={() => runs.fetchNextPage()}
        />
      </ScrollView>
      <Modal visible={issueFilterOpen} onClose={() => setIssueFilterOpen(false)}>
        <ModalTitle>Filter reconciliation issues</ModalTitle>
        {(['', 'HIGH', 'MEDIUM', 'LOW'] as const).map((value) => (
          <Option
            key={value || 'all'}
            selected={issueSeverity === value}
            label={value ? humanize(value) : 'All severities'}
            onPress={() => setIssueSeverity(value)}
          />
        ))}
        <Done onPress={() => setIssueFilterOpen(false)} />
      </Modal>
    </>
  );
}

function RecordOfflineModal({
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
  const [mode, setMode] = useState<OfflineFinancialRecordType>('CONTRIBUTION');
  const [method, setMethod] = useState<OfflinePaymentMethod>('CHECK');
  const [reference, setReference] = useState('');
  const [receivedAt, setReceivedAt] = useState(() => new Date().toISOString());
  const [notes, setNotes] = useState('');
  const [markVerified, setMarkVerified] = useState(false);
  const [sendAcknowledgement, setSendAcknowledgement] = useState(false);

  const campaigns = useCampaigns(organizationId);
  const packageQuery = useInfiniteSponsorshipPackages(organizationId, {
    status: 'PUBLISHED',
    sort: 'NAME_ASC',
  });
  const packages = useMemo(
    () => flattenSponsorshipPages(packageQuery.data?.pages),
    [packageQuery.data?.pages],
  );
  const stores = useStores(organizationId);

  const [campaignId, setCampaignId] = useState('');
  const [amountMajor, setAmountMajor] = useState('');
  const [supporterName, setSupporterName] = useState('');
  const [supporterEmail, setSupporterEmail] = useState('');
  const [anonymous, setAnonymous] = useState(false);

  const [packageId, setPackageId] = useState('');
  const [sponsorName, setSponsorName] = useState('');
  const [sponsorEmail, setSponsorEmail] = useState('');
  const [sponsorPhone, setSponsorPhone] = useState('');
  const [sponsorCompany, setSponsorCompany] = useState('');

  const [storeId, setStoreId] = useState('');
  const products = useStoreProducts(organizationId, storeId || null);
  const manualProducts = (products.data?.items ?? []).filter(
    (product) => product.catalogSource === 'MANUAL' && product.status === 'ACTIVE',
  );
  const [orderLines, setOrderLines] = useState<OrderLineDraft[]>([newOrderLine()]);
  const [orderName, setOrderName] = useState('');
  const [orderEmail, setOrderEmail] = useState('');
  const [shippingLine1, setShippingLine1] = useState('');
  const [shippingLine2, setShippingLine2] = useState('');
  const [shippingCity, setShippingCity] = useState('');
  const [shippingState, setShippingState] = useState('');
  const [shippingPostal, setShippingPostal] = useState('');
  const [shippingCountry, setShippingCountry] = useState('US');

  const createContribution = useCreateOfflineContribution(organizationId);
  const createSponsorship = useCreateOfflineSponsorship(organizationId);
  const createOrder = useCreateOfflineOrder(organizationId);
  const pending = createContribution.isPending || createSponsorship.isPending || createOrder.isPending;
  const acknowledgementEmailAvailable = (
    mode === 'CONTRIBUTION' ? supporterEmail : mode === 'SPONSORSHIP' ? sponsorEmail : orderEmail
  ).trim().length > 0;

  function common() {
    return {
      paymentMethod: method,
      paymentReference: reference.trim() || null,
      receivedAt: new Date(receivedAt).toISOString(),
      internalNotes: notes.trim() || null,
      idempotencyKey: Crypto.randomUUID(),
      markVerified,
      sendAcknowledgement: sendAcknowledgement && acknowledgementEmailAvailable,
    };
  }

  async function submit() {
    if (Number.isNaN(new Date(receivedAt).getTime())) {
      return toast.show('Enter a valid received date/time.', 'error');
    }
    try {
      if (mode === 'CONTRIBUTION') {
        const amountMinor = parseMajorAmountToMinorUnits(amountMajor, 'USD');
        if (!campaignId || amountMinor === null || amountMinor <= 0) {
          return toast.show('Choose a campaign and enter a positive amount.', 'error');
        }
        await createContribution.mutateAsync({
          ...common(),
          campaignId,
          amountMinor,
          supporterName: anonymous ? null : supporterName.trim() || null,
          isAnonymous: anonymous,
          supporterEmail: supporterEmail.trim() || null,
        });
      } else if (mode === 'SPONSORSHIP') {
        if (!packageId || !sponsorName.trim()) {
          return toast.show('Choose a package and enter the sponsor name.', 'error');
        }
        await createSponsorship.mutateAsync({
          ...common(),
          packageId,
          sponsorName: sponsorName.trim(),
          sponsorContactEmail: sponsorEmail.trim() || null,
          sponsorPhone: sponsorPhone.trim() || null,
          sponsorCompanyName: sponsorCompany.trim() || null,
        });
      } else {
        const items = orderLines
          .filter((line) => line.variantId && line.quantity > 0)
          .map((line) => ({ productVariantId: line.variantId, quantity: line.quantity }));
        if (!storeId || items.length !== orderLines.length || items.length === 0) {
          return toast.show('Choose a store, variant, and quantity for each order line.', 'error');
        }
        await createOrder.mutateAsync({
          ...common(),
          storeId,
          items,
          supporterName: orderName.trim() || null,
          supporterEmail: orderEmail.trim() || null,
          shippingAddress:
            shippingLine1.trim() || shippingCity.trim() || shippingPostal.trim()
              ? {
                  name: orderName.trim() || null,
                  line1: shippingLine1.trim() || null,
                  line2: shippingLine2.trim() || null,
                  city: shippingCity.trim() || null,
                  state: shippingState.trim() || null,
                  postalCode: shippingPostal.trim() || null,
                  country: shippingCountry.trim() || null,
                }
              : null,
        });
      }
      toast.show(markVerified ? 'Offline transaction recorded and verified.' : 'Offline transaction recorded.', 'success');
      onClose();
    } catch {
      toast.show('Could not record the offline transaction.', 'error');
    }
  }

  return (
    <Modal visible={visible} onClose={onClose}>
      <ModalTitle>Record offline transaction</ModalTitle>
      <ScrollView style={styles.modalScroll} keyboardShouldPersistTaps="handled">
        <View style={styles.segment}>
          {(['CONTRIBUTION', 'SPONSORSHIP', 'ORDER'] as const).map((value) => (
            <Segment
              key={value}
              label={value === 'ORDER' ? 'Order' : humanize(value)}
              selected={mode === value}
              onPress={() => setMode(value)}
            />
          ))}
        </View>
        {mode === 'CONTRIBUTION' && (
          <View style={styles.form}>
            <ChoiceHeading>Campaign</ChoiceHeading>
            <ChoiceList<Campaign>
              items={(campaigns.data?.items ?? []).filter((item) => item.status !== 'ARCHIVED')}
              selectedId={campaignId}
              getId={(item) => item.id}
              getLabel={(item) => item.name}
              onSelect={(item) => setCampaignId(item.id)}
            />
            <Field label="Amount (USD)">
              <TextInput value={amountMajor} onChangeText={setAmountMajor} keyboardType="decimal-pad" style={inputStyle(theme)} placeholder="100.00" placeholderTextColor={theme.textSecondary} />
            </Field>
            <Field label="Supporter name">
              <TextInput value={supporterName} onChangeText={setSupporterName} editable={!anonymous} style={inputStyle(theme)} />
            </Field>
            <Field label="Supporter email">
              <TextInput value={supporterEmail} onChangeText={setSupporterEmail} keyboardType="email-address" autoCapitalize="none" style={inputStyle(theme)} />
            </Field>
            <Toggle label="Record publicly as anonymous" value={anonymous} onValueChange={setAnonymous} />
          </View>
        )}
        {mode === 'SPONSORSHIP' && (
          <View style={styles.form}>
            <ChoiceHeading>Sponsorship package</ChoiceHeading>
            <ChoiceList<SponsorshipPackage>
              items={packages.filter((item) => !item.soldOut)}
              selectedId={packageId}
              getId={(item) => item.id}
              getLabel={(item) => `${item.name} · ${formatMoneyMinorUnits(item.priceMinor, item.currency)}`}
              onSelect={(item) => setPackageId(item.id)}
            />
            <Field label="Sponsor name"><TextInput value={sponsorName} onChangeText={setSponsorName} style={inputStyle(theme)} /></Field>
            <Field label="Contact email"><TextInput value={sponsorEmail} onChangeText={setSponsorEmail} keyboardType="email-address" autoCapitalize="none" style={inputStyle(theme)} /></Field>
            <Field label="Phone"><TextInput value={sponsorPhone} onChangeText={setSponsorPhone} keyboardType="phone-pad" style={inputStyle(theme)} /></Field>
            <Field label="Company"><TextInput value={sponsorCompany} onChangeText={setSponsorCompany} style={inputStyle(theme)} /></Field>
          </View>
        )}
        {mode === 'ORDER' && (
          <View style={styles.form}>
            <ChoiceHeading>Swag Shop</ChoiceHeading>
            <ChoiceList
              items={(stores.data?.items ?? []).filter((item) => item.status === 'ACTIVE')}
              selectedId={storeId}
              getId={(item) => item.id}
              getLabel={(item) => item.name}
              onSelect={(item) => {
                setStoreId(item.id);
                setOrderLines([newOrderLine()]);
              }}
            />
            {orderLines.map((line, index) => (
              <OrderLineEditor
                key={line.key}
                organizationId={organizationId}
                line={line}
                index={index}
                products={manualProducts}
                onChange={(next) =>
                  setOrderLines((current) =>
                    current.map((item) => (item.key === line.key ? next : item)),
                  )
                }
                onRemove={
                  orderLines.length > 1
                    ? () => setOrderLines((current) => current.filter((item) => item.key !== line.key))
                    : undefined
                }
              />
            ))}
            <Button
              variant="secondary"
              disabled={!storeId}
              onPress={() => setOrderLines((current) => [...current, newOrderLine()])}>
              Add line item
            </Button>
            <Field label="Customer name"><TextInput value={orderName} onChangeText={setOrderName} style={inputStyle(theme)} /></Field>
            <Field label="Customer email"><TextInput value={orderEmail} onChangeText={setOrderEmail} keyboardType="email-address" autoCapitalize="none" style={inputStyle(theme)} /></Field>
            <Field label="Shipping address"><TextInput value={shippingLine1} onChangeText={setShippingLine1} style={inputStyle(theme)} /></Field>
            <Field label="Address line 2"><TextInput value={shippingLine2} onChangeText={setShippingLine2} style={inputStyle(theme)} /></Field>
            <Field label="City"><TextInput value={shippingCity} onChangeText={setShippingCity} style={inputStyle(theme)} /></Field>
            <Field label="State"><TextInput value={shippingState} onChangeText={setShippingState} style={inputStyle(theme)} /></Field>
            <Field label="Postal code"><TextInput value={shippingPostal} onChangeText={setShippingPostal} style={inputStyle(theme)} /></Field>
            <Field label="Country code"><TextInput value={shippingCountry} onChangeText={(value) => setShippingCountry(value.toUpperCase())} maxLength={2} autoCapitalize="characters" style={inputStyle(theme)} /></Field>
          </View>
        )}
        <ChoiceHeading>Payment method</ChoiceHeading>
        <View style={styles.choiceGrid}>
          {(['CASH', 'CHECK', 'ACH', 'EXTERNAL_CARD', 'VENMO', 'ZELLE', 'OTHER'] as const).map((value) => (
            <Pressable key={value} onPress={() => setMethod(value)} style={[styles.chip, method === value && styles.chipSelected]}>
              <ThemedText type={method === value ? 'smallBold' : 'small'}>{humanize(value)}</ThemedText>
            </Pressable>
          ))}
        </View>
        <Field label="Reference / check number"><TextInput value={reference} onChangeText={setReference} style={inputStyle(theme)} /></Field>
        <Field label="Received at (ISO date/time)">
          <TextInput value={receivedAt} onChangeText={setReceivedAt} autoCapitalize="none" style={inputStyle(theme)} />
        </Field>
        <Field label="Internal notes">
          <TextInput value={notes} onChangeText={setNotes} multiline style={[inputStyle(theme), styles.textarea]} />
        </Field>
        <Toggle label="Verify now" value={markVerified} onValueChange={setMarkVerified} />
        <Toggle
          label="Send acknowledgement after verification"
          value={sendAcknowledgement && acknowledgementEmailAvailable}
          onValueChange={setSendAcknowledgement}
          disabled={!acknowledgementEmailAvailable}
        />
        <View style={styles.modalActions}>
          <Button variant="secondary" onPress={onClose}>Cancel</Button>
          <Button disabled={pending} onPress={submit}>{pending ? 'Recording…' : 'Record transaction'}</Button>
        </View>
      </ScrollView>
    </Modal>
  );
}

function CorrectionModal({
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
  const [targetType, setTargetType] = useState<FinancialCorrectionTargetType>('CONTRIBUTION');
  const [targetId, setTargetId] = useState('');
  const [amountMajor, setAmountMajor] = useState('');
  const [reason, setReason] = useState('');
  const preview = usePreviewFinancialCorrection(organizationId);
  const execute = useExecuteFinancialCorrection(organizationId);

  function input() {
    const amountMinor = amountMajor.trim()
      ? parseMajorAmountToMinorUnits(amountMajor, 'USD')
      : null;
    if (amountMajor.trim() && amountMinor === null) return null;
    return {
      targetType,
      targetId: targetId.trim(),
      amountMinor,
      reason: reason.trim(),
    };
  }

  function reset() {
    preview.reset();
    setTargetId('');
    setAmountMajor('');
    setReason('');
  }

  async function previewCorrection() {
    const values = input();
    if (!values) {
      return toast.show('Enter a valid amount, or leave it blank for the full remaining amount.', 'error');
    }
    if (!values.targetId || !values.reason) {
      return toast.show('Target ID and reason are required.', 'error');
    }
    if (values.amountMinor !== null && values.amountMinor <= 0) {
      return toast.show('Amount must be positive, or leave it blank for the full remaining amount.', 'error');
    }
    try {
      await preview.mutateAsync(values);
    } catch {
      toast.show('Could not preview the correction.', 'error');
    }
  }

  async function confirmCorrection() {
    if (!preview.data) return;
    const values = input();
    if (!values) return toast.show('Enter a valid correction amount.', 'error');
    try {
      await execute.mutateAsync({
        ...values,
        confirmationHash: preview.data.confirmationHash,
        idempotencyKey: Crypto.randomUUID(),
      });
      toast.show(`${humanize(preview.data.correctionType)} completed.`, 'success');
      reset();
      onClose();
    } catch {
      toast.show('Could not complete the correction.', 'error');
    }
  }

  return (
    <Modal visible={visible} onClose={() => { reset(); onClose(); }}>
      <ModalTitle>New financial correction</ModalTitle>
      <ScrollView style={styles.modalScroll} keyboardShouldPersistTaps="handled">
        {!preview.data ? (
          <View style={styles.form}>
            <ChoiceHeading>Target type</ChoiceHeading>
            <View style={styles.choiceGrid}>
              {(['CONTRIBUTION', 'SPONSORSHIP', 'ORDER', 'OFFLINE_FINANCIAL_RECORD'] as const).map((value) => (
                <Pressable key={value} onPress={() => setTargetType(value)} style={[styles.chip, targetType === value && styles.chipSelected]}>
                  <ThemedText type={targetType === value ? 'smallBold' : 'small'}>{humanize(value)}</ThemedText>
                </Pressable>
              ))}
            </View>
            <Field label="Target ID"><TextInput value={targetId} onChangeText={setTargetId} autoCapitalize="none" style={inputStyle(theme)} /></Field>
            <Field label="Amount (USD, optional)">
              <TextInput value={amountMajor} onChangeText={setAmountMajor} keyboardType="decimal-pad" placeholder="Blank = full remaining amount" placeholderTextColor={theme.textSecondary} style={inputStyle(theme)} />
            </Field>
            <Field label="Reason"><TextInput value={reason} onChangeText={setReason} multiline style={[inputStyle(theme), styles.textarea]} /></Field>
            <Button disabled={preview.isPending} onPress={previewCorrection}>
              {preview.isPending ? 'Previewing…' : 'Preview correction'}
            </Button>
          </View>
        ) : (
          <View style={styles.form}>
            <ThemedView type="backgroundSelected" style={styles.preview}>
              <ThemedText type="smallBold">{humanize(preview.data.correctionType)} · {preview.data.targetLabel}</ThemedText>
              <ThemedText type="small" themeColor="textSecondary">
                Source: {humanize(preview.data.paymentSource)}
              </ThemedText>
              <ThemedText type="small" themeColor="textSecondary">
                Requested: {formatMoneyMinorUnits(preview.data.requestedAmountMinor, preview.data.currency)}
              </ThemedText>
              <ThemedText type="small" themeColor="textSecondary">
                Remaining after: {formatMoneyMinorUnits(preview.data.remainingAfterMinor, preview.data.currency)}
              </ThemedText>
              {preview.data.warnings.map((warning) => (
                <ThemedText key={warning} type="small" style={{ color: Brand.championshipGold }}>{warning}</ThemedText>
              ))}
            </ThemedView>
            <View style={styles.modalActions}>
              <Button variant="secondary" onPress={() => preview.reset()}>Back</Button>
              <Button disabled={execute.isPending} onPress={confirmCorrection}>
                {execute.isPending ? 'Confirming…' : `Confirm ${humanize(preview.data.correctionType)}`}
              </Button>
            </View>
          </View>
        )}
      </ScrollView>
    </Modal>
  );
}

type OrderLineDraft = {
  key: string;
  productId: string;
  variantId: string;
  quantity: number;
};

function newOrderLine(): OrderLineDraft {
  return { key: Crypto.randomUUID(), productId: '', variantId: '', quantity: 1 };
}

function OrderLineEditor({
  organizationId,
  line,
  index,
  products,
  onChange,
  onRemove,
}: {
  organizationId: string | null;
  line: OrderLineDraft;
  index: number;
  products: ProductResponse[];
  onChange: (line: OrderLineDraft) => void;
  onRemove?: () => void;
}) {
  const theme = useTheme();
  const variants = useProductVariants(organizationId, line.productId || null);
  const manualVariants = (variants.data ?? []).filter(
    (variant) => variant.catalogSource === 'MANUAL' && variant.isActive,
  );
  return (
    <ThemedView type="backgroundSelected" style={styles.orderLine}>
      <ChoiceHeading>Product {index + 1}</ChoiceHeading>
      <ChoiceList
        items={products}
        selectedId={line.productId}
        getId={(item) => item.id}
        getLabel={(item) => item.name}
        onSelect={(item) => onChange({ ...line, productId: item.id, variantId: '' })}
      />
      {line.productId ? (
        <>
          <ChoiceHeading>Variant</ChoiceHeading>
          <ChoiceList
            items={manualVariants}
            selectedId={line.variantId}
            getId={(item) => item.id}
            getLabel={(item) => `${item.label} · ${formatMoneyMinorUnits(item.priceMinor, item.currency)}`}
            onSelect={(item) => onChange({ ...line, variantId: item.id })}
          />
        </>
      ) : null}
      <Field label="Quantity">
        <TextInput
          value={String(line.quantity)}
          onChangeText={(value) => onChange({ ...line, quantity: Math.max(1, Number(value) || 1) })}
          keyboardType="number-pad"
          style={inputStyle(theme)}
        />
      </Field>
      {onRemove && <Button variant="secondary" onPress={onRemove}>Remove line</Button>}
    </ThemedView>
  );
}

function Segment({ label, selected, onPress }: { label: string; selected: boolean; onPress: () => void }) {
  return (
    <Pressable onPress={onPress} style={[styles.segmentButton, selected && styles.segmentSelected]}>
      <ThemedText type={selected ? 'smallBold' : 'small'}>{label}</ThemedText>
    </Pressable>
  );
}

function Option({ selected, label, onPress }: { selected: boolean; label: string; onPress: () => void }) {
  const theme = useTheme();
  return (
    <Pressable accessibilityRole="button" onPress={onPress} style={styles.option}>
      <ThemedText type={selected ? 'smallBold' : 'default'}>{label}</ThemedText>
      {selected && <Ionicons name="checkmark" size={18} color={theme.text} />}
    </Pressable>
  );
}

function ChoiceList<T>({
  items,
  selectedId,
  getId,
  getLabel,
  onSelect,
}: {
  items: T[];
  selectedId: string;
  getId: (item: T) => string;
  getLabel: (item: T) => string;
  onSelect: (item: T) => void;
}) {
  return (
    <View style={styles.choiceList}>
      {items.length === 0 ? (
        <ThemedText type="small" themeColor="textSecondary">No available options.</ThemedText>
      ) : (
        items.map((item) => {
          const id = getId(item);
          return (
            <Pressable
              key={id}
              onPress={() => onSelect(item)}
              style={[styles.choiceRow, selectedId === id && styles.choiceRowSelected]}>
              <ThemedText type={selectedId === id ? 'smallBold' : 'small'}>{getLabel(item)}</ThemedText>
              {selectedId === id && <Ionicons name="checkmark" size={16} color={Brand.infoBlue} />}
            </Pressable>
          );
        })
      )}
    </View>
  );
}

function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <View style={styles.field}>
      <ThemedText type="smallBold">{label}</ThemedText>
      {children}
    </View>
  );
}

function Toggle({
  label,
  value,
  onValueChange,
  disabled = false,
}: {
  label: string;
  value: boolean;
  onValueChange: (value: boolean) => void;
  disabled?: boolean;
}) {
  return (
    <View style={styles.toggle}>
      <ThemedText type="small" style={styles.flexOne}>{label}</ThemedText>
      <Switch value={value} onValueChange={onValueChange} disabled={disabled} />
    </View>
  );
}

function ModalTitle({ children }: { children: ReactNode }) {
  return <ThemedText type="smallBold" style={styles.modalTitle}>{children}</ThemedText>;
}

function ChoiceHeading({ children }: { children: ReactNode }) {
  return <ThemedText type="smallBold" style={styles.choiceHeading}>{children}</ThemedText>;
}

function Done({ onPress }: { onPress: () => void }) {
  return (
    <Pressable style={styles.done} onPress={onPress}>
      <ThemedText type="smallBold">Done</ThemedText>
    </Pressable>
  );
}

function inputStyle(theme: ReturnType<typeof useTheme>) {
  return [
    styles.input,
    { color: theme.text, backgroundColor: theme.backgroundElement, borderColor: theme.textSecondary },
  ];
}

function humanize(value: string) {
  return value.toLowerCase().replaceAll('_', ' ').replace(/\b\w/g, (letter) => letter.toUpperCase());
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  segment: {
    flexDirection: 'row',
    gap: Spacing.one,
    paddingHorizontal: Spacing.four,
    paddingBottom: Spacing.two,
  },
  segmentButton: {
    flex: 1,
    minHeight: 40,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: Spacing.two,
  },
  segmentSelected: { backgroundColor: Brand.infoBlue + '22' },
  content: { padding: Spacing.four, gap: Spacing.three, paddingBottom: Spacing.six },
  notice: { padding: Spacing.three, borderRadius: Spacing.three, gap: Spacing.one },
  card: { padding: Spacing.three, borderRadius: Spacing.three, gap: Spacing.two },
  cardTop: { flexDirection: 'row', alignItems: 'flex-start', gap: Spacing.three },
  flexOne: { flex: 1 },
  actionRow: { flexDirection: 'row', gap: Spacing.two, flexWrap: 'wrap' },
  sectionHeading: { gap: Spacing.two, marginTop: Spacing.two },
  inlineChoices: { flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.one },
  chip: {
    minHeight: 36,
    justifyContent: 'center',
    paddingHorizontal: Spacing.three,
    borderRadius: Spacing.four,
    backgroundColor: 'rgba(127,127,127,0.12)',
  },
  chipSelected: { backgroundColor: Brand.infoBlue + '22' },
  modalTitle: { marginBottom: Spacing.three },
  modalScroll: { maxHeight: 620 },
  option: {
    minHeight: 48,
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: Spacing.two,
  },
  done: { minHeight: 44, justifyContent: 'center', alignItems: 'center', marginTop: Spacing.two },
  form: { gap: Spacing.three },
  field: { gap: Spacing.one },
  input: {
    minHeight: 46,
    borderWidth: StyleSheet.hairlineWidth,
    borderRadius: Spacing.two,
    paddingHorizontal: Spacing.three,
    paddingVertical: Spacing.two,
  },
  textarea: { minHeight: 84, textAlignVertical: 'top' },
  toggle: { minHeight: 48, flexDirection: 'row', alignItems: 'center', gap: Spacing.three },
  modalActions: { flexDirection: 'row', justifyContent: 'flex-end', gap: Spacing.two, marginTop: Spacing.two },
  preview: { padding: Spacing.three, borderRadius: Spacing.three, gap: Spacing.one },
  choiceHeading: { marginTop: Spacing.one },
  choiceGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.one },
  choiceList: { gap: Spacing.one },
  choiceRow: {
    minHeight: 42,
    borderRadius: Spacing.two,
    paddingHorizontal: Spacing.three,
    paddingVertical: Spacing.two,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    backgroundColor: 'rgba(127,127,127,0.08)',
  },
  choiceRowSelected: { backgroundColor: Brand.infoBlue + '18' },
  orderLine: { padding: Spacing.three, borderRadius: Spacing.three, gap: Spacing.two },
});
