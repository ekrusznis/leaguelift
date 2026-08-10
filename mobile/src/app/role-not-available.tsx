import { StyleSheet, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { Button } from '@/components/button';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { useAuth } from '@/features/auth/AuthContext';
import { useDashboardContext } from '@/features/dashboard/api';
import { Spacing } from '@/constants/theme';

const ROLE_LABEL: Record<string, string> = {
  OWNER: 'organization owner',
  PARENT: 'parent/guardian',
  ATHLETE: 'athlete',
  TOURNAMENT_ADMIN: 'tournament admin',
  PLATFORM_ADMIN: 'platform admin',
};

/**
 * Only the Coach persona is built out so far (ADR-101/102). A real, successful
 * login for any other role lands here rather than showing a broken/empty coach UI.
 */
export default function RoleNotAvailableScreen() {
  const { user, logout } = useAuth();
  const dashboardContext = useDashboardContext(true);
  const roleLabel = dashboardContext.data ? (ROLE_LABEL[dashboardContext.data.role] ?? dashboardContext.data.role) : 'this role';

  return (
    <ThemedView style={styles.container}>
      <SafeAreaView style={styles.safeArea}>
        <View style={styles.content}>
          <ThemedText type="title" style={styles.title}>
            Not built yet
          </ThemedText>
          <ThemedText themeColor="textSecondary" style={styles.body}>
            {user?.displayName ? `Hi ${user.displayName} — the` : 'The'} Rally26 mobile app is coach-only for now.
            The {roleLabel} experience is coming in a later phase.
          </ThemedText>
          <Button onPress={() => void logout()}>Sign Out</Button>
        </View>
      </SafeAreaView>
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  safeArea: {
    flex: 1,
    justifyContent: 'center',
  },
  content: {
    paddingHorizontal: Spacing.five,
    gap: Spacing.three,
  },
  title: {
    textAlign: 'center',
  },
  body: {
    textAlign: 'center',
    lineHeight: 22,
    marginBottom: Spacing.two,
  },
});
