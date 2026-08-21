import { router } from 'expo-router';
import { useMemo, useState } from 'react';
import { FlatList, Pressable, StyleSheet, View } from 'react-native';
import Ionicons from '@expo/vector-icons/Ionicons';

import { EmptyState } from '@/components/empty-state';
import { ErrorState } from '@/components/error-state';
import { ListControls } from '@/components/list-controls';
import { LoadingState } from '@/components/loading-state';
import { Modal } from '@/components/modal';
import { PlatformStatusSpacer } from '@/components/platform-status-spacer';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { useTeamEligibilityClearance } from '@/features/eligibility/api';
import type { ClearanceStatus } from '@/features/eligibility/types';
import { useTeamRoster } from '@/features/roster/api';
import { useCoach } from '@/features/teams/CoachContext';
import { sportLabel, terminologyForSport } from '@/features/teams/sportLabel';
import { Brand, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

const CLEARANCE_LABELS: Record<ClearanceStatus, string> = {
  ROSTER_PENDING: 'Not yet reviewed',
  DOCUMENTS_REQUIRED: 'Documents required',
  UNDER_REVIEW: 'Under review',
  CLEARED: 'Cleared',
  EXPIRED: 'Expired',
  INELIGIBLE: 'Ineligible',
};

const CLEARANCE_COLORS: Record<ClearanceStatus, string> = {
  ROSTER_PENDING: Brand.slateGray,
  DOCUMENTS_REQUIRED: Brand.championshipGold,
  UNDER_REVIEW: Brand.infoBlue,
  CLEARED: Brand.victoryGreen,
  EXPIRED: Brand.errorRed,
  INELIGIBLE: Brand.errorRed,
};

type RosterSort = 'NAME_ASC' | 'NAME_DESC';

export default function TeamsScreen() {
  const coach = useCoach();
  const theme = useTheme();
  const selectedTeam = coach.teams.find((team) => team.teamId === coach.selectedTeamId) ?? null;
  const terminology = terminologyForSport(selectedTeam?.sport ?? null);
  const rosterQuery = useTeamRoster(coach.organizationId, coach.selectedTeamId);
  const [query, setQuery] = useState('');
  const [ineligibleOnly, setIneligibleOnly] = useState(false);
  const [sort, setSort] = useState<RosterSort>('NAME_ASC');
  const [filterOpen, setFilterOpen] = useState(false);
  const [sortOpen, setSortOpen] = useState(false);

  const clearanceQuery = useTeamEligibilityClearance(
    coach.organizationId,
    coach.selectedTeamId,
    ineligibleOnly ? 'INELIGIBLE' : null,
  );
  const clearanceByParticipant = useMemo(
    () => new Map(clearanceQuery.data?.map((clearance) => [clearance.participantId, clearance])),
    [clearanceQuery.data],
  );
  const roster = rosterQuery.data ?? [];
  const visibleRoster = useMemo(() => {
    const needle = query.trim().toLowerCase();
    const filtered = roster.filter((participant) => {
      if (ineligibleOnly && !clearanceByParticipant.has(participant.id)) return false;
      if (!needle) return true;
      return `${participant.firstName} ${participant.lastName}`.toLowerCase().includes(needle);
    });
    return [...filtered].sort((a, b) => {
      const left = `${a.lastName} ${a.firstName}`.toLowerCase();
      const right = `${b.lastName} ${b.firstName}`.toLowerCase();
      const value = left.localeCompare(right);
      return sort === 'NAME_ASC' ? value : -value;
    });
  }, [clearanceByParticipant, ineligibleOnly, query, roster, sort]);

  const activeFilters = ineligibleOnly ? ['Ineligible only'] : [];
  const sortLabel = sort === 'NAME_ASC' ? 'Name A–Z' : 'Name Z–A';

  return (
    <ThemedView style={styles.container}>
      <PlatformStatusSpacer />
      <View style={styles.header}>
        <View style={styles.headerBody}>
          <ThemedText type="smallBold">Team Roster</ThemedText>
          {selectedTeam && (
            <ThemedText type="small" themeColor="textSecondary">
              {selectedTeam.name} · {sportLabel(selectedTeam.sport)}
            </ThemedText>
          )}
        </View>
        {selectedTeam && (
          <Pressable
            accessibilityRole="button"
            accessibilityLabel={`View coaches and staff for ${selectedTeam.name}`}
            onPress={() =>
              router.push({
                pathname: '/team-staff',
                params: {
                  organizationId: coach.organizationId ?? '',
                  teamId: selectedTeam.teamId,
                  teamName: selectedTeam.name,
                },
              })
            }
            style={styles.staffButton}>
            <Ionicons name="people-outline" size={18} color={theme.text} />
            <ThemedText type="smallBold">Staff</ThemedText>
          </Pressable>
        )}
      </View>

      {coach.isLoading && <LoadingState label="Loading…" />}
      {coach.isError && <ErrorState message="Could not load your team." />}
      {rosterQuery.isLoading && <LoadingState label="Loading roster…" />}
      {rosterQuery.isError && <ErrorState message="Could not load the roster." onRetry={() => rosterQuery.refetch()} />}

      {!rosterQuery.isLoading && !rosterQuery.isError && (
        <FlatList
          data={visibleRoster}
          keyExtractor={(item) => item.id}
          contentContainerStyle={styles.list}
          ListHeaderComponent={
            <View style={styles.controls}>
              <ListControls
                query={query}
                onChangeQuery={setQuery}
                searchPlaceholder={`Search ${terminology.athletePlural.toLowerCase()}`}
                resultCount={visibleRoster.length}
                activeFilters={activeFilters}
                onRemoveFilter={() => setIneligibleOnly(false)}
                onClearFilters={() => {
                  setQuery('');
                  setIneligibleOnly(false);
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
              title={query.trim() || ineligibleOnly ? 'No results found' : 'No participants yet'}
              description={
                query.trim() || ineligibleOnly
                  ? 'Try changing your search or filters.'
                  : "Nobody is on this team's roster yet."
              }
            />
          }
          renderItem={({ item }) => {
            const clearance = clearanceByParticipant.get(item.id);
            const status = clearance?.status ?? 'ROSTER_PENDING';
            return (
              <ThemedView type="backgroundElement" style={styles.row}>
                <ThemedView type="backgroundSelected" style={styles.avatar}>
                  <ThemedText type="smallBold">
                    {item.firstName[0]}
                    {item.lastName[0]}
                  </ThemedText>
                </ThemedView>
                <View style={styles.body}>
                  <ThemedText type="smallBold">
                    {item.firstName} {item.lastName}
                  </ThemedText>
                </View>
                <View style={[styles.badge, { backgroundColor: `${CLEARANCE_COLORS[status]}22` }]}>
                  <ThemedText type="small" style={{ color: CLEARANCE_COLORS[status] }}>
                    {CLEARANCE_LABELS[status]}
                  </ThemedText>
                </View>
              </ThemedView>
            );
          }}
        />
      )}

      <Modal visible={filterOpen} onClose={() => setFilterOpen(false)}>
        <ThemedText type="smallBold" style={styles.modalTitle}>Filter roster</ThemedText>
        {([
          [false, `All ${terminology.athletePlural.toLowerCase()}`],
          [true, 'Ineligible only'],
        ] as const).map(([value, label]) => (
          <Pressable
            key={label}
            onPress={() => {
              setIneligibleOnly(value);
              setFilterOpen(false);
            }}
            style={styles.option}>
            <ThemedText type={ineligibleOnly === value ? 'smallBold' : 'default'}>{label}</ThemedText>
            {ineligibleOnly === value && <Ionicons name="checkmark" size={18} color={theme.text} />}
          </Pressable>
        ))}
      </Modal>

      <Modal visible={sortOpen} onClose={() => setSortOpen(false)}>
        <ThemedText type="smallBold" style={styles.modalTitle}>Sort roster</ThemedText>
        {([
          ['NAME_ASC', 'Name A–Z'],
          ['NAME_DESC', 'Name Z–A'],
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
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
  },
  headerBody: { flex: 1, gap: 2 },
  staffButton: {
    minHeight: 40,
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.one,
    paddingHorizontal: Spacing.two,
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
  avatar: {
    width: 36,
    height: 36,
    borderRadius: 18,
    alignItems: 'center',
    justifyContent: 'center',
  },
  body: { flex: 1, gap: 2 },
  badge: {
    borderRadius: 999,
    paddingHorizontal: Spacing.two,
    paddingVertical: 2,
  },
  modalTitle: { marginBottom: Spacing.three },
  option: {
    minHeight: 48,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: Spacing.two,
  },
});
