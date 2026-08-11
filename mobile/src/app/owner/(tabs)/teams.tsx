import { router } from 'expo-router';
import { Pressable, ScrollView, StyleSheet, View } from 'react-native';
import Ionicons from '@expo/vector-icons/Ionicons';

import { EmptyState } from '@/components/empty-state';
import { ErrorState } from '@/components/error-state';
import { LoadingState } from '@/components/loading-state';
import { PlatformStatusSpacer } from '@/components/platform-status-spacer';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { useDashboardContext } from '@/features/dashboard/api';
import { useOrgTeams } from '@/features/organization-teams/api';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

/** Org-wide Teams list (Teams tab) — real GET /organizations/{id}/teams, read-only this slice (ADR-105). */
export default function OwnerTeamsScreen() {
  const theme = useTheme();
  const dashboardContext = useDashboardContext(true);
  const organizationId = dashboardContext.data?.organizationId ?? null;
  const teamsQuery = useOrgTeams(organizationId);

  return (
    <ThemedView style={styles.container}>
      <PlatformStatusSpacer />
      <View style={styles.header}>
        <ThemedText type="smallBold">Teams</ThemedText>
      </View>

      {teamsQuery.isLoading && <LoadingState label="Loading teams…" />}
      {teamsQuery.isError && <ErrorState message="Could not load teams." onRetry={() => teamsQuery.refetch()} />}
      {teamsQuery.data && teamsQuery.data.items.length === 0 && (
        <EmptyState title="No teams yet" description="Teams created for this organization will show up here." />
      )}

      <ScrollView contentContainerStyle={styles.list}>
        {teamsQuery.data?.items.map((team) => (
          <Pressable key={team.id} onPress={() => router.push({ pathname: '/owner/team-detail', params: { id: team.id } })}>
            <ThemedView type="backgroundElement" style={styles.row}>
              <View style={[styles.colorDot, { backgroundColor: team.primaryColor }]} />
              <View style={styles.rowBody}>
                <ThemedText type="smallBold">{team.name}</ThemedText>
                <ThemedText type="small" themeColor="textSecondary">
                  {team.sport}
                  {team.season ? ` · ${team.season}` : ''}
                  {team.ageGroup ? ` · ${team.ageGroup}` : ''}
                </ThemedText>
              </View>
              <Ionicons name="chevron-forward" size={18} color={theme.textSecondary} />
            </ThemedView>
          </Pressable>
        ))}
      </ScrollView>
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
  colorDot: {
    width: 12,
    height: 12,
    borderRadius: 6,
  },
  rowBody: {
    flex: 1,
    gap: 2,
  },
});
