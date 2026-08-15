import { QueryClientProvider } from '@tanstack/react-query';
import { DarkTheme, DefaultTheme, Redirect, router, Stack, ThemeProvider } from 'expo-router';
import { useEffect, useRef } from 'react';
import * as SplashScreen from 'expo-splash-screen';
import { AnimatedSplashOverlay } from '@/components/animated-icon';
import { ToastProvider } from '@/components/toast';
import { AuthProvider, useAuth } from '@/features/auth/AuthContext';
import { useDashboardContext } from '@/features/dashboard/api';
import type { DashboardRole } from '@/features/dashboard/types';
import { AppThemeProvider, useThemeScheme } from '@/hooks/use-theme';
import { OnboardingContext, useOnboarding, useOnboardingState } from '@/lib/onboarding';
import { queryClient } from '@/lib/queryClient';

SplashScreen.preventAutoHideAsync();

export default function RootLayout() {
  const onboarding = useOnboardingState();
  return (
    <QueryClientProvider client={queryClient}>
      <AuthProvider><AppThemeProvider><ToastProvider><OnboardingContext.Provider value={onboarding}><NavigationThemeBridge /></OnboardingContext.Provider></ToastProvider></AppThemeProvider></AuthProvider>
    </QueryClientProvider>
  );
}
function NavigationThemeBridge() {
  const scheme = useThemeScheme();
  return <ThemeProvider value={scheme === 'dark' ? DarkTheme : DefaultTheme}><AnimatedSplashOverlay /><RootNavigator /></ThemeProvider>;
}
const ROLE_HOME: Partial<Record<DashboardRole, string>> = { COACH: '/', PARENT: '/parent', ATHLETE: '/athlete', OWNER: '/owner' };
function RootNavigator() {
  const { status: onboardingStatus } = useOnboarding();
  const { user, isLoading: authLoading } = useAuth();
  const dashboardContext = useDashboardContext(onboardingStatus === 'seen' && !authLoading && !!user);
  const loading = onboardingStatus === 'loading' || authLoading || (!!user && dashboardContext.isLoading);
  const role = dashboardContext.data?.role;
  const lastRoutedRole = useRef<DashboardRole | null>(null);
  useEffect(() => {
    if (!user) { lastRoutedRole.current = null; return; }
    if (!role || lastRoutedRole.current === role) return;
    lastRoutedRole.current = role;
    const home = ROLE_HOME[role];
    router.replace((home ?? '/role-not-available') as never);
  }, [role, user]);
  return (
    <>
      {!loading && <Stack screenOptions={{ headerShown: false }}>
        <Stack.Screen name="onboarding" /><Stack.Screen name="login" /><Stack.Screen name="register" /><Stack.Screen name="role-not-available" />
        <Stack.Screen name="(tabs)" /><Stack.Screen name="parent/(tabs)" /><Stack.Screen name="athlete/(tabs)" /><Stack.Screen name="owner/(tabs)" />
        <Stack.Screen name="event-details" options={{ presentation: 'card' }} /><Stack.Screen name="event-form" options={{ presentation: 'card' }} />
        <Stack.Screen name="announcements" options={{ presentation: 'card' }} /><Stack.Screen name="announcement-details" options={{ presentation: 'card' }} />
        <Stack.Screen name="documents" options={{ presentation: 'card' }} /><Stack.Screen name="eligibility" options={{ presentation: 'card' }} />
        <Stack.Screen name="fee-details" options={{ presentation: 'card' }} /><Stack.Screen name="guardians" options={{ presentation: 'card' }} />
        <Stack.Screen name="athlete/new-conversation" options={{ presentation: 'card' }} /><Stack.Screen name="message-compose" options={{ presentation: 'card' }} />
        <Stack.Screen name="safety-controls" options={{ presentation: 'card' }} /><Stack.Screen name="owner/team-detail" options={{ presentation: 'card' }} />
        <Stack.Screen name="owner/reports" options={{ presentation: 'card' }} /><Stack.Screen name="owner/payout" options={{ presentation: 'card' }} />
        <Stack.Screen name="owner/announcements-manage" options={{ presentation: 'card' }} /><Stack.Screen name="owner/announcement-compose" options={{ presentation: 'card' }} />
        <Stack.Screen name="owner/broadcasts-manage" options={{ presentation: 'card' }} /><Stack.Screen name="owner/broadcast-compose" options={{ presentation: 'card' }} />
        <Stack.Screen name="owner/broadcast-detail" options={{ presentation: 'card' }} /><Stack.Screen name="web-embed" options={{ presentation: 'card' }} />
        <Stack.Screen name="messages/[threadId]" options={{ presentation: 'card' }} /><Stack.Screen name="settings" options={{ presentation: 'card' }} />
        <Stack.Screen name="action-center" options={{ presentation: 'card' }} /><Stack.Screen name="help" options={{ presentation: 'card' }} />
        <Stack.Screen name="help/[slug]" options={{ presentation: 'card' }} /><Stack.Screen name="support-request" options={{ presentation: 'card' }} />
        <Stack.Screen name="owner/documents" options={{ presentation: 'card' }} />
        <Stack.Screen name="fundraising" options={{ presentation: 'card' }} />
        <Stack.Screen name="fundraising-form" options={{ presentation: 'card' }} />
        <Stack.Screen name="fundraising-detail" options={{ presentation: 'card' }} />
        <Stack.Screen name="fundraising-game" options={{ presentation: 'card' }} />
      </Stack>}
      {!loading && onboardingStatus === 'unseen' && <Redirect href="/onboarding" />}
      {!loading && onboardingStatus === 'seen' && !user && <Redirect href="/login" />}
    </>
  );
}
