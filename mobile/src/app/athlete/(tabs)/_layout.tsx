import AthleteTabs from '@/components/athlete-tabs';
import { AthleteSelfContextProvider } from '@/features/athlete/AthleteSelfContext';

export default function AthleteTabsLayout() {
  return (
    <AthleteSelfContextProvider>
      <AthleteTabs />
    </AthleteSelfContextProvider>
  );
}
