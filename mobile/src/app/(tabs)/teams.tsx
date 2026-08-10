import { FlatList, StyleSheet, View } from 'react-native';

import { EmptyState } from '@/components/empty-state';
import { ErrorState } from '@/components/error-state';
import { LoadingState } from '@/components/loading-state';
import { PlatformStatusSpacer } from '@/components/platform-status-spacer';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { useTeamRoster } from '@/features/roster/api';
import { useCoach } from '@/features/teams/CoachContext';
import { Spacing } from '@/constants/theme';

/**
 * Team Roster (Teams tab) — real participants (ADR-102). No "position" field: the
 * backend Participant model has no such concept (that was invented for the mockup) —
 * founder decision was to use the real domain rather than a fake client-only field.
 */
export default function TeamsScreen() {
  const coach = useCoach();
  const selectedTeam = coach.teams.find((t) => t.teamId === coach.selectedTeamId) ?? null;
  const rosterQuery = useTeamRoster(coach.organizationId, coach.selectedTeamId);

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

      {coach.isLoading && <LoadingState label="Loading…" />}
      {coach.isError && <ErrorState message="Could not load your team." />}
      {rosterQuery.isLoading && <LoadingState label="Loading roster…" />}
      {rosterQuery.isError && <ErrorState message="Could not load the roster." onRetry={() => rosterQuery.refetch()} />}
      {rosterQuery.data && rosterQuery.data.length === 0 && (
        <EmptyState title="No participants yet" description="Nobody is on this team's roster yet." />
      )}

      <FlatList
        data={rosterQuery.data ?? []}
        keyExtractor={(item) => item.id}
        contentContainerStyle={styles.list}
        renderItem={({ item }) => (
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
          </ThemedView>
        )}
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
    gap: 2,
  },
});
