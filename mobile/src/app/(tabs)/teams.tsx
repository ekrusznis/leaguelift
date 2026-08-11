import { useState } from 'react';
import { FlatList, Pressable, StyleSheet, View } from 'react-native';

import { EmptyState } from '@/components/empty-state';
import { ErrorState } from '@/components/error-state';
import { LoadingState } from '@/components/loading-state';
import { PlatformStatusSpacer } from '@/components/platform-status-spacer';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { useTeamEligibilityClearance } from '@/features/eligibility/api';
import type { ClearanceStatus } from '@/features/eligibility/types';
import { useTeamRoster } from '@/features/roster/api';
import { useCoach } from '@/features/teams/CoachContext';
import { Brand, Spacing } from '@/constants/theme';

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

/**
 * Team Roster (Teams tab) — real participants (ADR-102). No "position" field: the
 * backend Participant model has no such concept (that was invented for the mockup) —
 * founder decision was to use the real domain rather than a fake client-only field.
 * Eligibility clearance badges + "Show ineligible only" filter added Phase 31 slice
 * 31.4 (DESIGN-DOC.md 14.1L §30.2 — operational status only, never evidence contents).
 * A participant with no clearance row at all is ROSTER_PENDING (never evaluated yet).
 */
export default function TeamsScreen() {
  const coach = useCoach();
  const selectedTeam = coach.teams.find((t) => t.teamId === coach.selectedTeamId) ?? null;
  const rosterQuery = useTeamRoster(coach.organizationId, coach.selectedTeamId);
  const [ineligibleOnly, setIneligibleOnly] = useState(false);
  const clearanceQuery = useTeamEligibilityClearance(
    coach.organizationId,
    coach.selectedTeamId,
    ineligibleOnly ? 'INELIGIBLE' : null,
  );
  const clearanceByParticipant = new Map(clearanceQuery.data?.map((c) => [c.participantId, c]));

  const roster = rosterQuery.data ?? [];
  const visibleRoster = ineligibleOnly ? roster.filter((p) => clearanceByParticipant.has(p.id)) : roster;

  return (
    <ThemedView style={styles.container}>
      <PlatformStatusSpacer />
      <View style={styles.header}>
        <ThemedText type="smallBold">Team Roster</ThemedText>
        {selectedTeam && (
          <ThemedText type="small" themeColor="textSecondary">
            {selectedTeam.name} · {selectedTeam.sport}
          </ThemedText>
        )}
      </View>

      <Pressable onPress={() => setIneligibleOnly((v) => !v)} style={styles.filterRow}>
        <ThemedView type={ineligibleOnly ? 'backgroundSelected' : 'backgroundElement'} style={styles.filterChip}>
          <ThemedText type="small">{ineligibleOnly ? 'Showing ineligible only' : 'Show ineligible only'}</ThemedText>
        </ThemedView>
      </Pressable>

      {coach.isLoading && <LoadingState label="Loading…" />}
      {coach.isError && <ErrorState message="Could not load your team." />}
      {rosterQuery.isLoading && <LoadingState label="Loading roster…" />}
      {rosterQuery.isError && <ErrorState message="Could not load the roster." onRetry={() => rosterQuery.refetch()} />}
      {rosterQuery.data && visibleRoster.length === 0 && (
        <EmptyState
          title={ineligibleOnly ? 'No ineligible players' : 'No participants yet'}
          description={ineligibleOnly ? 'Every reviewed player on this roster is currently eligible.' : "Nobody is on this team's roster yet."}
        />
      )}

      <FlatList
        data={visibleRoster}
        keyExtractor={(item) => item.id}
        contentContainerStyle={styles.list}
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
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  header: {
    paddingHorizontal: Spacing.four,
    paddingVertical: Spacing.two,
  },
  filterRow: {
    paddingHorizontal: Spacing.four,
    paddingBottom: Spacing.two,
  },
  filterChip: {
    alignSelf: 'flex-start',
    borderRadius: 999,
    paddingHorizontal: Spacing.three,
    paddingVertical: Spacing.one,
  },
  list: {
    paddingHorizontal: Spacing.four,
    paddingBottom: Spacing.six,
    gap: Spacing.two,
  },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.three,
    borderRadius: Spacing.three,
    padding: Spacing.three,
  },
  avatar: {
    width: 36,
    height: 36,
    borderRadius: 18,
    alignItems: 'center',
    justifyContent: 'center',
  },
  body: {
    flex: 1,
    gap: 2,
  },
  badge: {
    borderRadius: 999,
    paddingHorizontal: Spacing.two,
    paddingVertical: 2,
  },
});
