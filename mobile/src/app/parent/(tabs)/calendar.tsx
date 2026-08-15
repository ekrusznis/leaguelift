import { EmptyState } from '@/components/empty-state';
import { LoadingState } from '@/components/loading-state';
import { PlatformStatusSpacer } from '@/components/platform-status-spacer';
import { SearchableCalendar } from '@/components/searchable-calendar';
import { ThemedView } from '@/components/themed-view';
import { useHouseholdCtx } from '@/features/household/HouseholdContext';

export default function ParentCalendarScreen() {
  const household = useHouseholdCtx();

  if (household.isLoading) {
    return (
      <ThemedView style={{ flex: 1 }}>
        <PlatformStatusSpacer />
        <LoadingState label="Loading…" />
      </ThemedView>
    );
  }

  if (household.athletes.length === 0 || !household.organizationId || !household.householdId) {
    return (
      <ThemedView style={{ flex: 1 }}>
        <PlatformStatusSpacer />
        <EmptyState
          title="No linked athletes"
          description="No participants are linked to your household in this organization yet."
        />
      </ThemedView>
    );
  }

  return (
    <SearchableCalendar
      scope={{
        type: 'household',
        organizationId: household.organizationId,
        householdId: household.householdId,
      }}
      emptyDescription="Nothing is scheduled for your family in this month."
      errorMessage="Could not load your family's schedule."
    />
  );
}
