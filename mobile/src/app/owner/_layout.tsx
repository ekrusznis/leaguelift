import { Redirect, Stack } from 'expo-router';
import { ActivityIndicator, View } from 'react-native';

import { Brand } from '@/constants/theme';
import { useAuth } from '@/features/auth/AuthContext';
import { useDashboardContext } from '@/features/dashboard/api';
import { useOwnerOnboarding } from '@/features/ownerOnboarding/api';
import { isOwnerAccessUnlocked, ownerOnboardingWebPath } from '@/features/ownerOnboarding/routing';
import { webEmbedRoute } from '@/lib/webEmbed';

/** Defense-in-depth gate for every /owner route, including deep links. */
export default function OwnerLayout() {
  const { user, isLoading: authLoading } = useAuth();
  const dashboard = useDashboardContext(!authLoading && !!user);
  const isOwner = dashboard.data?.role === 'OWNER';
  const onboarding = useOwnerOnboarding(isOwner);

  if (authLoading || dashboard.isLoading || (isOwner && onboarding.isLoading)) {
    return (
      <View style={{ flex: 1, alignItems: 'center', justifyContent: 'center', backgroundColor: Brand.navy }}>
        <ActivityIndicator size="large" color={Brand.championshipGold} />
      </View>
    );
  }

  if (!user) return <Redirect href="/login" />;
  if (!isOwner) return <Redirect href="/role-not-available" />;

  if (!isOwnerAccessUnlocked(onboarding.data)) {
    return (
      <Redirect
        href={webEmbedRoute(ownerOnboardingWebPath(onboarding.data), 'Finish Rally26 setup', 'owner-onboarding') as never}
      />
    );
  }

  return <Stack screenOptions={{ headerShown: false }} />;
}
