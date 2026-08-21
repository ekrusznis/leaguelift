import Ionicons from '@expo/vector-icons/Ionicons';
import { router, useLocalSearchParams } from 'expo-router';
import { Pressable, ScrollView, StyleSheet, View } from 'react-native';

import { ErrorState } from '@/components/error-state';
import { LoadingState } from '@/components/loading-state';
import { ScreenHeader } from '@/components/screen-header';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { useDashboardContext } from '@/features/dashboard/api';
import { useOrgTeam } from '@/features/organization-teams/api';
import { sportLabel } from '@/features/teams/sportLabel';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

export default function OwnerTeamDetailScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const theme = useTheme();
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
      <ScreenHeader
        title={team.name}
        right={
          <View style={styles.headerActions}>
            <Pressable
              hitSlop={8}
              onPress={() => router.push({ pathname: '/message-compose', params: { teamId: team.id } })}>
              <Ionicons name="chatbubble-outline" size={22} color={theme.text} />
            </Pressable>
            <Pressable
              hitSlop={8}
              onPress={() => router.push({ pathname: '/event-form', params: { mode: 'create', teamId: team.id } })}>
              <Ionicons name="add-circle-outline" size={24} color={theme.text} />
            </Pressable>
          </View>
        }
      />
      <ScrollView contentContainerStyle={styles.content}>
        <View style={styles.colorRow}>
          <View style={[styles.colorSwatch, { backgroundColor: team.primaryColor }]} />
          <View style={[styles.colorSwatch, { backgroundColor: team.secondaryColor }]} />
        </View>

        <Pressable
          accessibilityRole="button"
          onPress={() =>
            router.push({
              pathname: '/team-staff',
              params: {
                organizationId: organizationId ?? '',
                teamId: team.id,
                teamName: team.name,
              },
            })
          }>
          <ThemedView type="backgroundElement" style={styles.staffRow}>
            <View style={styles.staffLabel}>
              <Ionicons name="people-outline" size={20} color={theme.text} />
              <ThemedText type="smallBold">Coaches & Staff</ThemedText>
            </View>
            <Ionicons name="chevron-forward" size={18} color={theme.textSecondary} />
          </ThemedView>
        </Pressable>

        <DetailRow label="Sport" value={sportLabel(team.sport, team.sportOtherLabel)} />
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
      <ThemedText type="small" themeColor="textSecondary">{label}</ThemedText>
      <ThemedText type="smallBold">{value ?? '—'}</ThemedText>
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  content: {
    paddingHorizontal: Spacing.four,
    paddingBottom: Spacing.six,
    gap: Spacing.two,
  },
  headerActions: {
    flexDirection: 'row',
    gap: Spacing.three,
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
  staffRow: {
    minHeight: 52,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    borderRadius: Spacing.three,
    padding: Spacing.three,
  },
  staffLabel: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
  },
  detailRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    borderRadius: Spacing.three,
    padding: Spacing.three,
  },
});
