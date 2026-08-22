import { router } from 'expo-router';
import { useState } from 'react';
import { KeyboardAvoidingView, Platform, Pressable, ScrollView, StyleSheet, TextInput, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { Button } from '@/components/button';
import { PasswordInput } from '@/components/password-input';
import { Rally26Logo } from '@/components/rally26-logo';
import { ThemedText } from '@/components/themed-text';
import { messageForAuthError, resendVerificationEmail } from '@/features/auth/authApi';
import { useAuth } from '@/features/auth/AuthContext';
import { Brand, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

/**
 * Owner sign-up — POST /auth/register-owner (mirrors frontend/src/pages/auth/RegisterPage.tsx
 * exactly). This only ever creates a bare, unverified AppUser; it never creates an
 * organization or a session. Organization setup happens after email verification and
 * sign-in, inside the app — same as web.
 */
export default function RegisterScreen() {
  const theme = useTheme();
  const { register } = useAuth();
  const [firstName, setFirstName] = useState('');
  const [lastName, setLastName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [agreeToTerms, setAgreeToTerms] = useState(false);
  const [confirmAdult, setConfirmAdult] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [step, setStep] = useState<'form' | 'confirmation'>('form');
  const [resendStatus, setResendStatus] = useState<'idle' | 'sending' | 'sent'>('idle');

  function validate(): string | null {
    if (!firstName.trim() || !lastName.trim()) return 'Enter your first and last name.';
    if (!email.trim()) return 'Enter your email address.';
    if (password.length < 8) return 'Password must be at least 8 characters.';
    if (password !== confirmPassword) return 'Passwords must match.';
    if (!agreeToTerms) return 'You must agree to the Terms and Privacy Policy.';
    if (!confirmAdult) return 'You must confirm you are at least 18 years old.';
    return null;
  }

  async function onSubmit() {
    const validationError = validate();
    if (validationError) {
      setError(validationError);
      return;
    }
    setError(null);
    setSubmitting(true);
    try {
      await register({
        firstName: firstName.trim(),
        lastName: lastName.trim(),
        email: email.trim(),
        password,
        agreeToTerms,
        confirmAdult,
      });
      setStep('confirmation');
    } catch (e) {
      setError(messageForAuthError(e));
    } finally {
      setSubmitting(false);
    }
  }

  async function onResend() {
    setResendStatus('sending');
    try {
      await resendVerificationEmail(email.trim());
      setResendStatus('sent');
    } catch {
      setResendStatus('idle');
    }
  }

  if (step === 'confirmation') {
    return (
      <View style={[styles.container, { backgroundColor: theme.background }]}>
        <SafeAreaView style={styles.safeArea}>
          <ThemedText type="title" style={styles.confirmationTitle}>
            Check your email
          </ThemedText>
          <ThemedText themeColor="textSecondary" style={styles.confirmationBody}>
            Use the verification link we sent to {email.trim()} to activate your account, then sign in to create your
            organization.
          </ThemedText>
          <Pressable onPress={onResend} disabled={resendStatus !== 'idle'}>
            <ThemedText type="small" style={styles.link}>
              {resendStatus === 'sending' ? 'Sending…' : resendStatus === 'sent' ? "Sent — check your email" : "Didn't get the email? Resend verification"}
            </ThemedText>
          </Pressable>
          <Button onPress={() => router.replace('/login')} style={styles.confirmationButton}>
            Go to Sign In
          </Button>
        </SafeAreaView>
      </View>
    );
  }

  return (
    <KeyboardAvoidingView style={styles.container} behavior={Platform.OS === 'ios' ? 'padding' : 'height'}>
      <SafeAreaView style={styles.safeArea}>
        <ScrollView contentContainerStyle={styles.scrollContent} keyboardShouldPersistTaps="handled">
          <View style={styles.header}>
            <Rally26Logo width={196} />
            <ThemedText themeColor="textSecondary" style={styles.subtitle}>
              Create your owner account
            </ThemedText>
          </View>

          <View style={styles.form}>
            <View style={styles.row}>
              <View style={[styles.field, styles.rowField]}>
                <ThemedText type="small" themeColor="textSecondary">
                  First name
                </ThemedText>
                <TextInput
                  value={firstName}
                  onChangeText={setFirstName}
                  autoComplete="given-name"
                  placeholder="Jordan"
                  placeholderTextColor={theme.textSecondary}
                  style={[styles.input, { color: theme.text, backgroundColor: theme.backgroundElement }]}
                />
              </View>
              <View style={[styles.field, styles.rowField]}>
                <ThemedText type="small" themeColor="textSecondary">
                  Last name
                </ThemedText>
                <TextInput
                  value={lastName}
                  onChangeText={setLastName}
                  autoComplete="family-name"
                  placeholder="Smith"
                  placeholderTextColor={theme.textSecondary}
                  style={[styles.input, { color: theme.text, backgroundColor: theme.backgroundElement }]}
                />
              </View>
            </View>

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
              <PasswordInput
                value={password}
                onChangeText={setPassword}
                autoComplete="password-new"
                placeholder="At least 8 characters"
              />
            </View>

            <View style={styles.field}>
              <ThemedText type="small" themeColor="textSecondary">
                Confirm password
              </ThemedText>
              <PasswordInput
                value={confirmPassword}
                onChangeText={setConfirmPassword}
                autoComplete="password-new"
                placeholder="••••••••"
                onSubmitEditing={onSubmit}
              />
            </View>

            <Checkbox
              checked={agreeToTerms}
              onToggle={() => setAgreeToTerms((v) => !v)}
              label="I agree to the Terms of Service and Privacy Policy."
            />
            <Checkbox checked={confirmAdult} onToggle={() => setConfirmAdult((v) => !v)} label="I am at least 18 years old." />

            {error && (
              <ThemedText style={styles.error} accessibilityRole="alert">
                {error}
              </ThemedText>
            )}

            <Button onPress={onSubmit} disabled={submitting}>
              {submitting ? 'Creating account…' : 'Create Owner Account'}
            </Button>

            <Pressable onPress={() => router.replace('/login')} style={styles.link}>
              <ThemedText type="small" themeColor="textSecondary">
                Already have an account? <ThemedText type="small" style={styles.linkAccent}>Sign in</ThemedText>
              </ThemedText>
            </Pressable>
          </View>
        </ScrollView>
      </SafeAreaView>
    </KeyboardAvoidingView>
  );
}

function Checkbox({ checked, onToggle, label }: { checked: boolean; onToggle: () => void; label: string }) {
  const theme = useTheme();
  return (
    <Pressable onPress={onToggle} style={styles.checkboxRow} accessibilityRole="checkbox" accessibilityState={{ checked }}>
      <View style={[styles.checkboxBox, { borderColor: theme.textSecondary }, checked && styles.checkboxBoxChecked]}>
        {checked && <ThemedText style={styles.checkboxMark}>✓</ThemedText>}
      </View>
      <ThemedText type="small" themeColor="textSecondary" style={styles.checkboxLabel}>
        {label}
      </ThemedText>
    </Pressable>
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
    paddingHorizontal: Spacing.five,
    paddingVertical: Spacing.six,
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
  row: {
    flexDirection: 'row',
    gap: Spacing.three,
  },
  rowField: {
    flex: 1,
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
  checkboxRow: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: Spacing.two,
  },
  checkboxBox: {
    width: 22,
    height: 22,
    borderRadius: Spacing.one,
    borderWidth: 1.5,
    alignItems: 'center',
    justifyContent: 'center',
    marginTop: 2,
  },
  checkboxBoxChecked: {
    backgroundColor: Brand.championshipGold,
    borderColor: Brand.championshipGold,
  },
  checkboxMark: {
    color: '#0B1F33',
    fontSize: 14,
    lineHeight: 16,
  },
  checkboxLabel: {
    flex: 1,
  },
  link: {
    alignSelf: 'center',
  },
  linkAccent: {
    color: Brand.championshipGold,
  },
  confirmationTitle: {
    marginTop: Spacing.six,
    textAlign: 'center',
  },
  confirmationBody: {
    marginTop: Spacing.three,
    textAlign: 'center',
    paddingHorizontal: Spacing.four,
  },
  confirmationButton: {
    marginTop: Spacing.five,
    marginHorizontal: Spacing.five,
  },
});
