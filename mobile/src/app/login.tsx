import * as AppleAuthentication from 'expo-apple-authentication';
import { router } from 'expo-router';
import * as WebBrowser from 'expo-web-browser';
import { useEffect, useState } from 'react';
import { KeyboardAvoidingView, Platform, Pressable, ScrollView, StyleSheet, TextInput, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { Button } from '@/components/button';
import { PasswordInput } from '@/components/password-input';
import { Rally26Logo } from '@/components/rally26-logo';
import { ThemedText } from '@/components/themed-text';
import { useToast } from '@/components/toast';
import { useAuth } from '@/features/auth/AuthContext';
import { isOAuthNotConfiguredError, messageForAuthError } from '@/features/auth/authApi';
import { useGoogleIdToken } from '@/features/auth/useGoogleIdToken';
import { ApiError } from '@/lib/apiClient';
import { env } from '@/lib/env';
import { Brand, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

// Required once per screen that uses expo-auth-session, so the in-app browser tab
// used for the Google flow closes itself after redirecting back into the app.
WebBrowser.maybeCompleteAuthSession();

/** Real login — POST /auth/login (ADR-102). "Forgot password?" opens the web reset flow in an in-app browser tab rather than duplicating it natively. */
export default function LoginScreen() {
  const theme = useTheme();
  const toast = useToast();
  const { login, loginWithGoogle, loginWithApple } = useAuth();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [appleAvailable, setAppleAvailable] = useState(false);
  const { request: googleRequest, response: googleResponse, promptAsync: promptGoogleAsync } = useGoogleIdToken();

  useEffect(() => {
    if (Platform.OS !== 'ios') return;
    AppleAuthentication.isAvailableAsync()
      .then(setAppleAvailable)
      .catch(() => setAppleAvailable(false));
  }, []);

  useEffect(() => {
    if (googleResponse?.type !== 'success') return;
    const idToken = googleResponse.params.id_token;
    if (!idToken) return;
    (async () => {
      setError(null);
      setSubmitting(true);
      try {
        await loginWithGoogle(idToken);
        router.replace('/');
      } catch (e) {
        if (isOAuthNotConfiguredError(e)) {
          toast.show('Google sign-in is coming soon.', 'info');
        } else {
          setError(messageForAuthError(e));
        }
      } finally {
        setSubmitting(false);
      }
    })();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [googleResponse]);

  async function onSubmit() {
    if (!email.trim() || !password) {
      setError('Enter your email and password.');
      return;
    }
    setError(null);
    setSubmitting(true);
    try {
      await login(email.trim(), password);
      router.replace('/');
    } catch (e) {
      setError(e instanceof ApiError ? e.body.message : 'Could not sign in. Please try again.');
    } finally {
      setSubmitting(false);
    }
  }

  async function onForgotPasswordPress() {
    await WebBrowser.openBrowserAsync(`${env.frontendBaseUrl}/auth/forgot-password`);
  }

  function onGooglePress() {
    if (!env.googleOAuthClientId) {
      toast.show('Google sign-in is coming soon.', 'info');
      return;
    }
    void promptGoogleAsync();
  }

  async function onApplePress() {
    setError(null);
    try {
      const credential = await AppleAuthentication.signInAsync({
        requestedScopes: [AppleAuthentication.AppleAuthenticationScope.FULL_NAME, AppleAuthentication.AppleAuthenticationScope.EMAIL],
      });
      if (!credential.identityToken) {
        setError('Could not sign in with Apple. Please try again.');
        return;
      }
      setSubmitting(true);
      await loginWithApple(credential.identityToken);
      router.replace('/');
    } catch (e) {
      if (isOAuthNotConfiguredError(e)) {
        toast.show('Apple sign-in is coming soon.', 'info');
      } else if ((e as { code?: string })?.code !== 'ERR_REQUEST_CANCELED') {
        setError(messageForAuthError(e));
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <KeyboardAvoidingView style={styles.container} behavior={Platform.OS === 'ios' ? 'padding' : 'height'}>
      <SafeAreaView style={styles.safeArea}>
        <ScrollView contentContainerStyle={styles.scrollContent} keyboardShouldPersistTaps="handled">
          <View style={styles.header}>
            <Rally26Logo width={196} />
            <ThemedText themeColor="textSecondary" style={styles.subtitle}>
              Sign in to your account
            </ThemedText>
          </View>

          <View style={styles.form}>
            <View style={styles.field}>
              <ThemedText type="small" themeColor="textSecondary">
                Email
              </ThemedText>
              <TextInput
                value={email}
                onChangeText={setEmail}
                autoCapitalize="none"
                autoComplete="email"
                keyboardType="email-address"
                placeholder="you@example.com"
                placeholderTextColor={theme.textSecondary}
                style={[styles.input, { color: theme.text, backgroundColor: theme.backgroundElement }]}
              />
            </View>
            <View style={styles.field}>
              <View style={styles.passwordLabelRow}>
                <ThemedText type="small" themeColor="textSecondary">
                  Password
                </ThemedText>
                <Pressable onPress={onForgotPasswordPress}>
                  <ThemedText type="small" style={styles.forgotPasswordLink}>
                    Forgot password?
                  </ThemedText>
                </Pressable>
              </View>
              <PasswordInput
                value={password}
                onChangeText={setPassword}
                autoComplete="password"
                placeholder="••••••••"
                onSubmitEditing={onSubmit}
              />
            </View>

            {error && (
              <ThemedText style={styles.error} accessibilityRole="alert">
                {error}
              </ThemedText>
            )}

            <Button onPress={onSubmit} disabled={submitting}>
              {submitting ? 'Signing in…' : 'Sign In'}
            </Button>

            <View style={styles.dividerRow}>
              <View style={styles.dividerLine} />
              <ThemedText type="small" themeColor="textSecondary">
                or continue with
              </ThemedText>
              <View style={styles.dividerLine} />
            </View>

            <Button variant="secondary" onPress={onGooglePress} disabled={submitting || !googleRequest}>
              Continue with Google
            </Button>

            {Platform.OS === 'ios' && appleAvailable && (
              <AppleAuthentication.AppleAuthenticationButton
                buttonType={AppleAuthentication.AppleAuthenticationButtonType.CONTINUE}
                buttonStyle={AppleAuthentication.AppleAuthenticationButtonStyle.WHITE_OUTLINE}
                cornerRadius={Spacing.two}
                style={styles.appleButton}
                onPress={onApplePress}
              />
            )}

            <Pressable onPress={() => router.push('/register')} style={styles.registerLink}>
              <ThemedText type="small" themeColor="textSecondary">
                Don&rsquo;t have an account? <ThemedText type="small" style={styles.registerLinkAccent}>Create one</ThemedText>
              </ThemedText>
            </Pressable>
          </View>
        </ScrollView>
      </SafeAreaView>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: Brand.navy,
  },
  safeArea: {
    flex: 1,
  },
  scrollContent: {
    flexGrow: 1,
    paddingHorizontal: Spacing.five,
    justifyContent: 'center',
    gap: Spacing.six,
  },
  header: {
    alignItems: 'center',
    gap: Spacing.two,
  },
  subtitle: {
    fontSize: 15,
  },
  form: {
    gap: Spacing.four,
  },
  field: {
    gap: Spacing.one,
  },
  passwordLabelRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  forgotPasswordLink: {
    color: Brand.championshipGold,
  },
  input: {
    minHeight: 48,
    borderRadius: Spacing.two,
    paddingHorizontal: Spacing.three,
  },
  error: {
    color: '#C93636',
  },
  dividerRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.three,
  },
  dividerLine: {
    flex: 1,
    height: StyleSheet.hairlineWidth,
    backgroundColor: Brand.slateGray,
  },
  appleButton: {
    height: 44,
  },
  registerLink: {
    alignSelf: 'center',
    marginTop: Spacing.two,
  },
  registerLinkAccent: {
    color: Brand.championshipGold,
  },
});
