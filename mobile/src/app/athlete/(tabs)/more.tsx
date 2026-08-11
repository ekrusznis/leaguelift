import Ionicons from '@expo/vector-icons/Ionicons';
import { router } from 'expo-router';
import { Pressable, StyleSheet, View } from 'react-native';

import { PlatformStatusSpacer } from '@/components/platform-status-spacer';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { useAthleteSelf } from '@/features/athlete/AthleteSelfContext';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

/** More tab — athlete persona menu hub (ADR-104/108), mirrors coach/parent's (tabs)/more.tsx. */
export default function AthleteMoreScreen() {
  const theme = useTheme();
  const athleteSelf = useAthleteSelf();

  const items: { icon: keyof typeof Ionicons.glyphMap; label: string; onPress: () => void; disabled?: boolean }[] = [
    { icon: 'people-outline', label: 'My Guardians', onPress: () => router.push('/guardians') },
    {
      icon: 'shield-checkmark-outline',
      label: 'Eligibility',
      onPress: () =>
        router.push({
          pathname: '/eligibility',
          params: { participantId: athleteSelf.participantId ?? '' },
        }),
      disabled: !athleteSelf.participantId,
    },
    { icon: 'megaphone-outline', label: 'Announcements', onPress: () => router.push('/announcements') },
    { icon: 'settings-outline', label: 'Settings', onPress: () => router.push('/settings') },
  ];

  return (
    <ThemedView style={styles.container}>
      <PlatformStatusSpacer />
      <View style={styles.header}>
        <ThemedText type="smallBold">More</ThemedText>
      </View>
      <View style={styles.list}>
        {items.map((item) => (
          <Pressable key={item.label} onPress={item.onPress} disabled={item.disabled}>
            <ThemedView type="backgroundElement" style={[styles.row, item.disabled && styles.rowDisabled]}>
              <Ionicons name={item.icon} size={20} color={theme.text} />
              <ThemedText style={styles.label}>{item.label}</ThemedText>
              <Ionicons name="chevron-forward" size={18} color={theme.textSecondary} />
            </ThemedView>
          </Pressable>
        ))}
      </View>
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  header: {
    paddingHorizontal: Spacing.four,
    paddingVertical: Spacing.two,
  },
  list: {
    paddingHorizontal: Spacing.four,
    gap: Spacing.two,
  },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.three,
    borderRadius: Spacing.three,
    padding: Spacing.three,
  },
  rowDisabled: {
    opacity: 0.5,
  },
  label: {
    flex: 1,
  },
});
