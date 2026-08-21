import Ionicons from '@expo/vector-icons/Ionicons';
import { router, useLocalSearchParams } from 'expo-router';
import { useMemo, useState } from 'react';
import { FlatList, Pressable, StyleSheet, View } from 'react-native';

import { EmptyState } from '@/components/empty-state';
import { ErrorState } from '@/components/error-state';
import { ListControls } from '@/components/list-controls';
import { LoadingState } from '@/components/loading-state';
import { ScreenHeader } from '@/components/screen-header';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { useParticipantTeams } from '@/features/household/api';
import { useOrgTeams } from '@/features/organization-teams/api';
import { sportLabel } from '@/features/teams/sportLabel';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

export default function ParticipantTeamsScreen() {
  const { organizationId, participantId, participantName } = useLocalSearchParams<{
    organizationId: string;
    participantId: string;
    participantName?: string;
  }>();
  const theme = useTheme();
  const assignmentsQuery = useParticipantTeams(organizationId ?? null, participantId ?? null);
  const teamsQuery = useOrgTeams(organizationId ?? null);
  const [query, setQuery] = useState('');

  const teams = useMemo(() => {
    const activeIds = new Set(
      (assignmentsQuery.data ?? []).filter((assignment) => assignment.status === 'ACTIVE').map((assignment) => assignment.teamId),
    );
    const needle = query.trim().toLowerCase();
    return (teamsQuery.data?.items ?? [])
      .filter((team) => activeIds.has(team.id))
      .filter((team) => !needle || `${team.name} ${team.sport} ${team.season ?? ''}`.toLowerCase().includes(needle))
      .sort((a, b) => a.name.localeCompare(b.name));
  }, [assignmentsQuery.data, query, teamsQuery.data?.items]);

  const loading = assignmentsQuery.isLoading || teamsQuery.isLoading;
  const error = assignmentsQuery.isError || teamsQuery.isError;

  return (
    <ThemedView style={styles.container}>
      <ScreenHeader title="Teams & Coaches" />
      <View style={styles.context}>
        {participantName ? <ThemedText type="smallBold">{participantName}</ThemedText> : null}
        <ThemedText type="small" themeColor="textSecondary">
          Open a connected team to see its coaches and team staff.
        </ThemedText>
      </View>

      {loading && <LoadingState label="Loading teams…" />}
      {error && (
        <ErrorState
          message="Could not load connected teams."
          onRetry={() => {
            assignmentsQuery.refetch();
            teamsQuery.refetch();
          }}
        />
      )}

      {!loading && !error && (
        <FlatList
          data={teams}
          keyExtractor={(item) => item.id}
          contentContainerStyle={styles.list}
          ListHeaderComponent={
            <View style={styles.controls}>
              <ListControls
                query={query}
                onChangeQuery={setQuery}
                searchPlaceholder="Search connected teams"
                resultCount={teams.length}
                onClearFilters={() => setQuery('')}
              />
            </View>
          }
          ListEmptyComponent={
            <EmptyState
              title={query.trim() ? 'No results found' : 'No connected teams yet'}
              description={
                query.trim()
                  ? 'Try changing your search.'
                  : 'Teams connected to this athlete will appear here.'
              }
            />
          }
          renderItem={({ item }) => (
            <Pressable
              accessibilityRole="button"
              accessibilityLabel={`View coaches and staff for ${item.name}`}
              onPress={() =>
                router.push({
                  pathname: '/team-staff',
                  params: { organizationId, teamId: item.id, teamName: item.name },
                })
              }>
              <ThemedView type="backgroundElement" style={styles.row}>
                <View style={[styles.colorDot, { backgroundColor: item.primaryColor }]} />
                <View style={styles.rowBody}>
                  <ThemedText type="smallBold">{item.name}</ThemedText>
                  <ThemedText type="small" themeColor="textSecondary">
                    {sportLabel(item.sport, item.sportOtherLabel)}{item.season ? ` · ${item.season}` : ''}
                  </ThemedText>
                </View>
                <Ionicons name="people-outline" size={20} color={theme.textSecondary} />
                <Ionicons name="chevron-forward" size={18} color={theme.textSecondary} />
              </ThemedView>
            </Pressable>
          )}
        />
      )}
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  context: {
    paddingHorizontal: Spacing.four,
    paddingBottom: Spacing.two,
    gap: Spacing.one,
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
  rowBody: { flex: 1, gap: 2 },
  colorDot: { width: 12, height: 12, borderRadius: 6 },
});
