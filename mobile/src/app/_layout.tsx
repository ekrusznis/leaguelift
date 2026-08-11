import { QueryClientProvider } from '@tanstack/react-query';
import { DarkTheme, Redirect, router, Stack, ThemeProvider } from 'expo-router';
import { useEffect, useRef } from 'react';
import * as SplashScreen from 'expo-splash-screen';

import { AnimatedSplashOverlay } from '@/components/animated-icon';
import { ToastProvider } from '@/components/toast';
import { AuthProvider, useAuth } from '@/features/auth/AuthContext';
import { useDashboardContext } from '@/features/dashboard/api';
import type { DashboardRole } from '@/features/dashboard/types';
import { OnboardingContext, useOnboarding, useOnboardingState } from '@/lib/onboarding';
import { queryClient } from '@/lib/queryClient';

SplashScreen.preventAutoHideAsync();

/**
 * Forced DarkTheme (matches docs/design/mobile_sample_design.png, ADR-101) rather
 * than following the OS scheme — see src/hooks/use-theme.ts. Real System/Light/Dark
 * alignment with Phase 28 is a later Phase 33 §32.1 design-system task.
 */
export default function RootLayout() {
  const onboarding = useOnboardingState();

  return (
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <ToastProvider>
          <OnboardingContext.Provider value={onboarding}>
            <ThemeProvider value={DarkTheme}>
              <AnimatedSplashOverlay />
              <RootNavigator />
            </ThemeProvider>
          </OnboardingContext.Provider>
        </ToastProvider>
      </AuthProvider>
    </QueryClientProvider>
  );
}

/**
 * Only these roles have a built experience so far (ADR-105) — everything else lands
 * on /role-not-available. `role: 'OWNER'` covers MembershipRole.OWNER/ADMINISTRATOR/
 * VIEWER alike (all three route to the same dashboard-context role server-side) — the
 * Owner screens themselves gate mutating actions on the real membership role/
 * capabilities from /me/contexts, not on this route having been reached at all.
 */
const ROLE_HOME: Partial<Record<DashboardRole, string>> = {
  COACH: '/',
  PARENT: '/parent',
  ATHLETE: '/athlete',
  OWNER: '/owner',
};

/**
 * Gate order: onboarding (first launch only) → auth (must be signed in) →
 * dashboard-context role. Each gate only evaluates once the one before it has
 * resolved, so a signed-out user never triggers a dashboard-context fetch, etc.
 *
 * Role routing uses an effect + router.replace(), not a declarative <Redirect>,
 * because it only fires ONCE per resolved role — a <Redirect> re-renders (and
 * re-navigates) every time this component re-renders, which would kick a parent
 * back to /parent on every background dashboard-context refetch, fighting any
 * navigation they've done since (e.g. into /parent/(tabs)/calendar).
 */
function RootNavigator() {
  const { status: onboardingStatus } = useOnboarding();
  const { user, isLoading: authLoading } = useAuth();
  const dashboardContext = useDashboardContext(onboardingStatus === 'seen' && !authLoading && !!user);

  const loading = onboardingStatus === 'loading' || authLoading || (!!user && dashboardContext.isLoading);
  const role = dashboardContext.data?.role;
  const lastRoutedRole = useRef<DashboardRole | null>(null);

  useEffect(() => {
    if (!user) {
      // Cleared on logout so a later login — even as the same role — re-triggers routing.
      lastRoutedRole.current = null;
      return;
    }
    if (!role || lastRoutedRole.current === role) return;
    lastRoutedRole.current = role;
    const home = ROLE_HOME[role];
    router.replace((home ?? '/role-not-available') as never);
  }, [role, user]);

  return (
    <>
      {!loading && (
        <Stack screenOptions={{ headerShown: false }}>
          <Stack.Screen name="onboarding" />
          <Stack.Screen name="login" />
          <Stack.Screen name="role-not-available" />
          <Stack.Screen name="(tabs)" />
          <Stack.Screen name="parent/(tabs)" />
          <Stack.Screen name="athlete/(tabs)" />
          <Stack.Screen name="owner/(tabs)" />
          <Stack.Screen name="event-details" options={{ presentation: 'card' }} />
          <Stack.Screen name="announcements" options={{ presentation: 'card' }} />
          <Stack.Screen name="announcement-details" options={{ presentation: 'card' }} />
          <Stack.Screen name="documents" options={{ presentation: 'card' }} />
          <Stack.Screen name="fee-details" options={{ presentation: 'card' }} />
          <Stack.Screen name="guardians" options={{ presentation: 'card' }} />
          <Stack.Screen name="athlete/new-conversation" options={{ presentation: 'card' }} />
          <Stack.Screen name="owner/team-detail" options={{ presentation: 'card' }} />
          <Stack.Screen name="owner/reports" options={{ presentation: 'card' }} />
          <Stack.Screen name="owner/payout" options={{ presentation: 'card' }} />
          <Stack.Screen name="owner/announcements-manage" options={{ presentation: 'card' }} />
          <Stack.Screen name="owner/announcement-compose" options={{ presentation: 'card' }} />
          <Stack.Screen name="owner/broadcasts-manage" options={{ presentation: 'card' }} />
          <Stack.Screen name="owner/broadcast-compose" options={{ presentation: 'card' }} />
          <Stack.Screen name="owner/broadcast-detail" options={{ presentation: 'card' }} />
          <Stack.Screen name="web-embed" options={{ presentation: 'card' }} />
          <Stack.Screen name="messages/[threadId]" options={{ presentation: 'card' }} />
          <Stack.Screen name="settings" options={{ presentation: 'card' }} />
        </Stack>
      )}
      {!loading && onboardingStatus === 'unseen' && <Redirect href="/onboarding" />}
      {!loading && onboardingStatus === 'seen' && !user && <Redirect href="/login" />}
    </>
  );
}
