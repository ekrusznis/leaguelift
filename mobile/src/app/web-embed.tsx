import { useQueryClient } from '@tanstack/react-query';
import { router, useLocalSearchParams } from 'expo-router';
import { useEffect, useRef, useState } from 'react';
import { ActivityIndicator, StyleSheet, View } from 'react-native';
import { WebView, type WebViewNavigation } from 'react-native-webview';

import { ErrorState } from '@/components/error-state';
import { ScreenHeader } from '@/components/screen-header';
import { ThemedView } from '@/components/themed-view';
import { useToast } from '@/components/toast';
import { Brand } from '@/constants/theme';
import { useAuth } from '@/features/auth/AuthContext';
import { useOwnerOnboarding, ownerOnboardingQueryKey } from '@/features/ownerOnboarding/api';
import { checkoutSignalFromUrl, isOwnerAccessUnlocked, type CheckoutSignal } from '@/features/ownerOnboarding/routing';
import { env } from '@/lib/env';

/**
 * Shared authenticated embed for real frontend pages (ADR-106). Commerce and the
 * owner setup wizard reuse the existing web implementation instead of duplicating
 * domain logic in React Native. The owner-onboarding flow is special only in its
 * navigation gate: Checkout redirects are informational; webhook-backed onboarding
 * state is authoritative before native Owner routes unlock.
 */
export default function WebEmbedScreen() {
  const { path, title, authenticated, flow } = useLocalSearchParams<{
    path: string;
    title: string;
    authenticated?: string;
    flow?: string;
  }>();
  const { getWebSession } = useAuth();
  const toast = useToast();
  const queryClient = useQueryClient();
  const [loading, setLoading] = useState(true);
  const [checkoutSignal, setCheckoutSignal] = useState<CheckoutSignal>(null);
  const transitioned = useRef(false);
  const requiresAuth = authenticated !== 'false';
  const ownerOnboardingFlow = flow === 'owner-onboarding';
  const session = requiresAuth ? getWebSession() : null;
  const onboarding = useOwnerOnboarding(ownerOnboardingFlow && !!session);

  useEffect(() => {
    if (!ownerOnboardingFlow || checkoutSignal !== 'success') return;
    const timer = setInterval(() => void onboarding.refetch(), 2000);
    return () => clearInterval(timer);
  }, [checkoutSignal, onboarding.refetch, ownerOnboardingFlow]);

  useEffect(() => {
    if (!ownerOnboardingFlow || transitioned.current || !isOwnerAccessUnlocked(onboarding.data)) return;
    transitioned.current = true;
    void (async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ownerOnboardingQueryKey }),
        queryClient.invalidateQueries({ queryKey: ['me', 'dashboard-context'] }),
      ]);
      router.replace('/owner' as never);
    })();
  }, [onboarding.data, ownerOnboardingFlow, queryClient]);

  if (requiresAuth && !session) {
    return (
      <ThemedView style={styles.container}>
        <ScreenHeader title={title ?? 'Rally26'} />
        <ErrorState message="Your session has expired. Please sign in again." />
      </ThemedView>
    );
  }

  const url = `${env.frontendBaseUrl}${path}`;
  const injectedJavaScriptBeforeContentLoaded = session
    ? `window.sessionStorage.setItem('rally26.session', ${JSON.stringify(JSON.stringify(session))}); true;`
    : undefined;

  function onNavigationStateChange(navState: WebViewNavigation) {
    const signal = checkoutSignalFromUrl(navState.url);

    if (ownerOnboardingFlow) {
      if (signal === 'success') {
        setCheckoutSignal('success');
        toast.show('Checkout received. Waiting for subscription activation…', 'info');
        void onboarding.refetch();
      } else if (signal === 'cancelled') {
        setCheckoutSignal('cancelled');
        toast.show('Checkout canceled. Your setup is saved.', 'info');
        void onboarding.refetch();
      }
      return;
    }

    if (signal === 'success') {
      toast.show('Completed successfully.', 'success');
    } else if (signal === 'cancelled') {
      toast.show('Canceled.', 'info');
    }
  }

  return (
    <ThemedView style={styles.container}>
      <ScreenHeader title={title ?? 'Rally26'} />
      <View style={styles.webviewWrap}>
        <WebView
          source={{ uri: url }}
          injectedJavaScriptBeforeContentLoaded={injectedJavaScriptBeforeContentLoaded}
          onNavigationStateChange={onNavigationStateChange}
          onLoadEnd={() => setLoading(false)}
          style={styles.webview}
        />
        {loading && (
          <View style={styles.loadingOverlay}>
            <ActivityIndicator size="large" color={Brand.championshipGold} />
          </View>
        )}
      </View>
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  webviewWrap: {
    flex: 1,
  },
  webview: {
    flex: 1,
    backgroundColor: 'transparent',
  },
  loadingOverlay: {
    ...StyleSheet.absoluteFill,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: Brand.navy,
  },
});
