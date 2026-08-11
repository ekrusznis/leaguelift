import { useLocalSearchParams } from 'expo-router';
import { useState } from 'react';
import { ActivityIndicator, StyleSheet, View } from 'react-native';
import { WebView, type WebViewNavigation } from 'react-native-webview';

import { ErrorState } from '@/components/error-state';
import { ScreenHeader } from '@/components/screen-header';
import { ThemedView } from '@/components/themed-view';
import { useToast } from '@/components/toast';
import { useAuth } from '@/features/auth/AuthContext';
import { Brand } from '@/constants/theme';
import { env } from '@/lib/env';

/**
 * Shared embed for real frontend/ pages inside the app (ADR-106) — Swag Shop,
 * Fundraising, Sponsorships stay web-only rather than being rebuilt natively.
 * `authenticated` (default true) bootstraps the WebView's own sessionStorage by
 * injecting the exact JSON shape frontend/src/auth/AuthContext.tsx reads under its
 * "rally26.session" key, via injectedJavaScriptBeforeContentLoaded — verified against
 * the real web auth code that nothing else (no cookie/CSRF token) gates access, so no
 * new backend "exchange token" endpoint was needed. `path` is relative to
 * EXPO_PUBLIC_FRONTEND_BASE_URL, e.g. `/app/organizations/{id}/swag-shop`.
 */
export default function WebEmbedScreen() {
  const { path, title, authenticated } = useLocalSearchParams<{ path: string; title: string; authenticated?: string }>();
  const { getWebSession } = useAuth();
  const toast = useToast();
  const [loading, setLoading] = useState(true);
  const requiresAuth = authenticated !== 'false';
  const session = requiresAuth ? getWebSession() : null;

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
    if (navState.url.includes('status=success')) {
      toast.show('Completed successfully.', 'success');
    } else if (navState.url.includes('status=canceled')) {
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
