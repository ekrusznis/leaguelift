import ParentTabs from '@/components/parent-tabs';
import { HouseholdContextProvider } from '@/features/household/HouseholdContext';

export default function ParentTabsLayout() {
  return (
    <HouseholdContextProvider>
      <ParentTabs />
    </HouseholdContextProvider>
  );
}
