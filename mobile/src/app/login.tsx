import { router } from 'expo-router';
import { useState } from 'react';
import { KeyboardAvoidingView, Platform, StyleSheet, TextInput, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { Button } from '@/components/button';
import { ThemedText } from '@/components/themed-text';
import { useAuth } from '@/features/auth/AuthContext';
import { ApiError } from '@/lib/apiClient';
import { Brand, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

/** Real login — POST /auth/login (ADR-102). No "forgot password"/registration flow yet; those exist on web only for now. */
export default function LoginScreen() {
  const theme = useTheme();
  const { login } = useAuth();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

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

  return (
    <KeyboardAvoidingView style={styles.container} behavior={Platform.select({ ios: 'padding', android: undefined })}>
      <SafeAreaView style={styles.safeArea}>
        <View style={styles.header}>
          <ThemedText type="title" style={styles.wordmark}>
            RALLY<ThemedText type="title" style={[styles.wordmark, styles.wordmarkAccent]}>26</ThemedText>
          </ThemedText>
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
            <ThemedText type="small" themeColor="textSecondary">
              Password
            </ThemedText>
            <TextInput
              value={password}
              onChangeText={setPassword}
              secureTextEntry
              autoComplete="password"
              placeholder="••••••••"
              placeholderTextColor={theme.textSecondary}
              style={[styles.input, { color: theme.text, backgroundColor: theme.backgroundElement }]}
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
        </View>
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
    paddingHorizontal: Spacing.five,
    justifyContent: 'center',
    gap: Spacing.six,
  },
  header: {
    alignItems: 'center',
    gap: Spacing.two,
  },
  wordmark: {
    fontSize: 32,
  },
  wordmarkAccent: {
    color: Brand.championshipGold,
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
  input: {
    minHeight: 48,
    borderRadius: Spacing.two,
    paddingHorizontal: Spacing.three,
  },
  error: {
    color: '#C93636',
  },
});
