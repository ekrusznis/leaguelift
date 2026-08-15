import Ionicons from '@expo/vector-icons/Ionicons';
import { router } from 'expo-router';
import { useMemo, useState } from 'react';
import { FlatList, Pressable, StyleSheet, View } from 'react-native';

import { EmptyState } from '@/components/empty-state';
import { ErrorState } from '@/components/error-state';
import { ListControls } from '@/components/list-controls';
import { ListFooter } from '@/components/list-footer';
import { LoadingState } from '@/components/loading-state';
import { Modal } from '@/components/modal';
import { PlatformStatusSpacer } from '@/components/platform-status-spacer';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { useDashboardContext } from '@/features/dashboard/api';
import { flattenInfiniteItems, useInfiniteTeamSearch, type TeamSearchSort } from '@/features/people-search/api';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

export default function OwnerTeamsScreen() {
  const theme = useTheme();
  const dashboardContext = useDashboardContext(true);
  const organizationId = dashboardContext.data?.organizationId ?? null;
  const [query, setQuery] = useState('');
  const [status, setStatus] = useState('');
  const [sort, setSort] = useState<TeamSearchSort>('NAME_ASC');
  const [filterOpen, setFilterOpen] = useState(false);
  const [sortOpen, setSortOpen] = useState(false);

  const teamsQuery = useInfiniteTeamSearch(organizationId, { q: query, status, sort });
  const teams = useMemo(() => flattenInfiniteItems(teamsQuery.data?.pages), [teamsQuery.data?.pages]);
  const total = teamsQuery.data?.pages[0]?.totalElements ?? 0;
  const activeFilters = status ? [status === 'ACTIVE' ? 'Active' : 'Archived'] : [];
  const sortLabel =
    sort === 'NAME_ASC' ? 'Name A–Z'
    : sort === 'NAME_DESC' ? 'Name Z–A'
    : sort === 'SPORT_ASC' ? 'Sport'
    : sort === 'NEWEST' ? 'Newest'
    : 'Oldest';

  return (
    <ThemedView style={styles.container}>
      <PlatformStatusSpacer />
      <View style={styles.header}>
        <ThemedText type="smallBold">Teams</ThemedText>
      </View>

      {teamsQuery.isLoading && <LoadingState label="Loading teams…" />}
      {teamsQuery.isError && <ErrorState message="Could not load teams." onRetry={() => teamsQuery.refetch()} />}

      {!teamsQuery.isLoading && !teamsQuery.isError && (
        <FlatList
          data={teams}
          keyExtractor={(item) => item.id}
          contentContainerStyle={styles.list}
          ListHeaderComponent={
            <View style={styles.controls}>
              <ListControls
                query={query}
                onChangeQuery={setQuery}
                searchPlaceholder="Search teams"
                resultCount={total}
                activeFilters={activeFilters}
                onRemoveFilter={() => setStatus('')}
                onClearFilters={() => {
                  setQuery('');
                  setStatus('');
                  setSort('NAME_ASC');
                }}
                onPressFilter={() => setFilterOpen(true)}
                onPressSort={() => setSortOpen(true)}
                sortLabel={sortLabel}
              />
            </View>
          }
          ListEmptyComponent={
            <EmptyState
              title={query.trim() || status ? 'No results found' : 'No teams yet'}
              description={
                query.trim() || status
                  ? 'Try changing your search or filters.'
                  : 'Teams created for this organization will show up here.'
              }
            />
          }
          ListFooterComponent={
            <ListFooter
              loadedCount={teams.length}
              totalCount={total}
              hasMore={!!teamsQuery.hasNextPage}
              loadingMore={teamsQuery.isFetchingNextPage}
              onLoadMore={() => teamsQuery.fetchNextPage()}
            />
          }
          renderItem={({ item }) => (
            <Pressable
              accessibilityRole="button"
              onPress={() => router.push({ pathname: '/owner/team-detail', params: { id: item.id } })}>
              <ThemedView type="backgroundElement" style={styles.row}>
                <View style={[styles.colorDot, { backgroundColor: item.primaryColor }]} />
                <View style={styles.rowBody}>
                  <ThemedText type="smallBold">{item.name}</ThemedText>
                  <ThemedText type="small" themeColor="textSecondary">
                    {item.sport}
                    {item.season ? ` · ${item.season}` : ''}
                    {item.ageGroup ? ` · ${item.ageGroup}` : ''}
                    {item.status === 'ARCHIVED' ? ' · Archived' : ''}
                  </ThemedText>
                </View>
                <Ionicons name="chevron-forward" size={18} color={theme.textSecondary} />
              </ThemedView>
            </Pressable>
          )}
        />
      )}

      <Modal visible={filterOpen} onClose={() => setFilterOpen(false)}>
        <ThemedText type="smallBold" style={styles.modalTitle}>Filter teams</ThemedText>
        {([
          ['', 'All teams'],
          ['ACTIVE', 'Active'],
          ['ARCHIVED', 'Archived'],
        ] as const).map(([value, label]) => (
          <Pressable
            key={value || 'ALL'}
            onPress={() => {
              setStatus(value);
              setFilterOpen(false);
            }}
            style={styles.option}>
            <ThemedText type={status === value ? 'smallBold' : 'default'}>{label}</ThemedText>
            {status === value && <Ionicons name="checkmark" size={18} color={theme.text} />}
          </Pressable>
        ))}
      </Modal>

      <Modal visible={sortOpen} onClose={() => setSortOpen(false)}>
        <ThemedText type="smallBold" style={styles.modalTitle}>Sort teams</ThemedText>
        {([
          ['NAME_ASC', 'Name A–Z'],
          ['NAME_DESC', 'Name Z–A'],
          ['SPORT_ASC', 'Sport'],
          ['NEWEST', 'Newest'],
          ['OLDEST', 'Oldest'],
        ] as const).map(([value, label]) => (
          <Pressable
            key={value}
            onPress={() => {
              setSort(value);
              setSortOpen(false);
            }}
            style={styles.option}>
            <ThemedText type={sort === value ? 'smallBold' : 'default'}>{label}</ThemedText>
            {sort === value && <Ionicons name="checkmark" size={18} color={theme.text} />}
          </Pressable>
        ))}
      </Modal>
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  header: {
    paddingHorizontal: Spacing.four,
    paddingVertical: Spacing.two,
  },
  controls: { paddingBottom: Spacing.three },
  list: {
    paddingHorizontal: Spacing.four,
    paddingBottom: Spacing.six,
  },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.three,
    borderRadius: Spacing.three,
    padding: Spacing.three,
    marginBottom: Spacing.two,
  },
  colorDot: { width: 12, height: 12, borderRadius: 6 },
  rowBody: { flex: 1, gap: 2 },
  modalTitle: { marginBottom: Spacing.three },
  option: {
    minHeight: 48,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: Spacing.two,
  },
});
