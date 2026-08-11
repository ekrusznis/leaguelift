import Ionicons from '@expo/vector-icons/Ionicons';
import { Linking, StyleSheet, View } from 'react-native';

import { EmptyState } from '@/components/empty-state';
import { ErrorState } from '@/components/error-state';
import { LoadingState } from '@/components/loading-state';
import { ScreenHeader } from '@/components/screen-header';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { useAthleteGuardians } from '@/features/athlete/api';
import { Brand, Spacing } from '@/constants/theme';

/** Athlete-only "My Guardians" screen — real GET /me/dashboard/athlete/guardians (ADR-104). */
export default function GuardiansScreen() {
  const guardiansQuery = useAthleteGuardians(true);

  return (
    <ThemedView style={styles.container}>
      <ScreenHeader title="My Guardians" />

      {guardiansQuery.isLoading && <LoadingState label="Loading guardians…" />}
      {guardiansQuery.isError && <ErrorState message="Could not load your guardians." onRetry={() => guardiansQuery.refetch()} />}
      {guardiansQuery.data && guardiansQuery.data.length === 0 && (
        <EmptyState title="No guardians on file" description="No guardian contacts are linked to your household yet." />
      )}

      <View style={styles.list}>
        {guardiansQuery.data?.map((guardian) => (
          <ThemedView key={guardian.email || guardian.name} type="backgroundElement" style={styles.card}>
            <ThemedText type="smallBold">{guardian.name}</ThemedText>
            <ThemedText type="small" themeColor="textSecondary">
              {guardian.role}
            </ThemedText>
            {guardian.email && (
              <View style={styles.contactRow}>
                <Ionicons name="mail-outline" size={16} color={Brand.slateGray} />
                <ThemedText type="small" themeColor="textSecondary" onPress={() => Linking.openURL(`mailto:${guardian.email}`)}>
                  {guardian.email}
                </ThemedText>
              </View>
            )}
            {guardian.phone && (
              <View style={styles.contactRow}>
                <Ionicons name="call-outline" size={16} color={Brand.slateGray} />
                <ThemedText type="small" themeColor="textSecondary" onPress={() => Linking.openURL(`tel:${guardian.phone}`)}>
                  {guardian.phone}
                </ThemedText>
              </View>
            )}
          </ThemedView>
        ))}
      </View>
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  list: {
    paddingHorizontal: Spacing.four,
    paddingBottom: Spacing.six,
    gap: Spacing.two,
  },
  card: {
    borderRadius: Spacing.three,
    padding: Spacing.three,
    gap: Spacing.one,
  },
  contactRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.one,
  },
});
