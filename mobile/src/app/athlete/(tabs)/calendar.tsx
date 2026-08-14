import { EmptyState } from '@/components/empty-state';
import { LoadingState } from '@/components/loading-state';
import { PlatformStatusSpacer } from '@/components/platform-status-spacer';
import { SearchableCalendar } from '@/components/searchable-calendar';
import { ThemedView } from '@/components/themed-view';
import { useAthleteSelf } from '@/features/athlete/AthleteSelfContext';

export default function AthleteCalendarScreen() {
  const athleteSelf = useAthleteSelf();

  if (athleteSelf.isLoading) {
    return (
      <ThemedView style={{ flex: 1 }}>
        <PlatformStatusSpacer />
        <LoadingState label="Loading…" />
      </ThemedView>
    );
  }

  if (!athleteSelf.organizationId || !athleteSelf.participantId) {
    return (
      <ThemedView style={{ flex: 1 }}>
        <PlatformStatusSpacer />
        <EmptyState
          title="No linked athlete record"
          description="Your account isn't linked to a participant record yet — ask your organization to set this up."
        />
      </ThemedView>
    );
  }

  return (
    <SearchableCalendar
      scope={{
        type: 'participant',
        organizationId: athleteSelf.organizationId,
        participantId: athleteSelf.participantId,
      }}
      emptyDescription="Nothing is scheduled for you in this month."
      errorMessage="Could not load your schedule."
    />
  );
}
