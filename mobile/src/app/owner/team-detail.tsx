import { useLocalSearchParams } from 'expo-router';
import { ScrollView, StyleSheet, View } from 'react-native';

import { ErrorState } from '@/components/error-state';
import { LoadingState } from '@/components/loading-state';
import { ScreenHeader } from '@/components/screen-header';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { useDashboardContext } from '@/features/dashboard/api';
import { useOrgTeam } from '@/features/organization-teams/api';
import { Spacing } from '@/constants/theme';

/** Read-only team detail, pushed from the Teams tab — real GET /organizations/{id}/teams/{teamId} (ADR-105). Editing is a later slice. */
export default function OwnerTeamDetailScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const dashboardContext = useDashboardContext(true);
  const organizationId = dashboardContext.data?.organizationId ?? null;
  const teamQuery = useOrgTeam(organizationId, id ?? null);

  if (teamQuery.isLoading) {
    return (
      <ThemedView style={styles.container}>
        <ScreenHeader title="Team" />
        <LoadingState label="Loading team…" />
      </ThemedView>
    );
  }

  if (teamQuery.isError || !teamQuery.data) {
    return (
      <ThemedView style={styles.container}>
        <ScreenHeader title="Team" />
        <ErrorState message="Could not load this team." onRetry={() => teamQuery.refetch()} />
      </ThemedView>
    );
  }

  const team = teamQuery.data;

  return (
    <ThemedView style={styles.container}>
      <ScreenHeader title={team.name} />
      <ScrollView contentContainerStyle={styles.content}>
        <View style={styles.colorRow}>
          <View style={[styles.colorSwatch, { backgroundColor: team.primaryColor }]} />
          <View style={[styles.colorSwatch, { backgroundColor: team.secondaryColor }]} />
        </View>

        <DetailRow label="Sport" value={team.sport} />
        <DetailRow label="Season" value={team.season} />
        <DetailRow label="Age Group" value={team.ageGroup} />
        <DetailRow label="Gender Category" value={team.genderCategory} />
        <DetailRow label="Level" value={team.level} />
        <DetailRow label="Status" value={team.status} />
        <DetailRow label="Contact Email" value={team.contactEmail} />
        <DetailRow label="Timezone Override" value={team.timezoneOverride} />
      </ScrollView>
    </ThemedView>
  );
}

function DetailRow({ label, value }: { label: string; value: string | null }) {
  return (
    <ThemedView type="backgroundElement" style={styles.detailRow}>
      <ThemedText type="small" themeColor="textSecondary">
        {label}
      </ThemedText>
      <ThemedText type="smallBold">{value ?? '—'}</ThemedText>
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  content: {
    paddingHorizontal: Spacing.four,
    paddingBottom: Spacing.six,
    gap: Spacing.two,
  },
  colorRow: {
    flexDirection: 'row',
    gap: Spacing.two,
    marginBottom: Spacing.two,
  },
  colorSwatch: {
    width: 40,
    height: 40,
    borderRadius: Spacing.two,
  },
  detailRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    borderRadius: Spacing.three,
    padding: Spacing.three,
  },
});
