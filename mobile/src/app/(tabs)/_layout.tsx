import AppTabs from '@/components/app-tabs';
import { CoachContextProvider } from '@/features/teams/CoachContext';

export default function TabsLayout() {
  return (
    <CoachContextProvider>
      <AppTabs />
    </CoachContextProvider>
  );
}
