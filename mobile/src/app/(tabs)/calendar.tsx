import { router } from 'expo-router';

import { EmptyState } from '@/components/empty-state';
import { LoadingState } from '@/components/loading-state';
import { PlatformStatusSpacer } from '@/components/platform-status-spacer';
import { SearchableCalendar } from '@/components/searchable-calendar';
import { ThemedView } from '@/components/themed-view';
import { useCoach } from '@/features/teams/CoachContext';

export default function CalendarScreen() {
  const coach = useCoach();

  if (coach.isLoading) {
    return (
      <ThemedView style={{ flex: 1 }}>
        <PlatformStatusSpacer />
        <LoadingState label="Loading…" />
      </ThemedView>
    );
  }

  if (coach.teams.length === 0 || !coach.organizationId || !coach.selectedTeamId) {
    return (
      <ThemedView style={{ flex: 1 }}>
        <PlatformStatusSpacer />
        <EmptyState
          title="No team selected"
          description="You aren't assigned to a team as a coach in this organization."
        />
      </ThemedView>
    );
  }

  return (
    <SearchableCalendar
      scope={{
        type: 'team',
        organizationId: coach.organizationId,
        teamId: coach.selectedTeamId,
      }}
      emptyDescription="Nothing is scheduled for this team in this month."
      errorMessage="Could not load the team schedule."
      onCreate={() =>
        router.push({
          pathname: '/event-form',
          params: { mode: 'create', teamId: coach.selectedTeamId ?? '' },
        })
      }
    />
  );
}
