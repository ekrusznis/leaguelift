import Ionicons from '@expo/vector-icons/Ionicons';
import { router } from 'expo-router';
import { Pressable, StyleSheet, View } from 'react-native';

import { PlatformStatusSpacer } from '@/components/platform-status-spacer';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

const ITEMS: { icon: keyof typeof Ionicons.glyphMap; label: string; href: '/announcements' | '/settings' }[] = [
  { icon: 'megaphone-outline', label: 'Announcements', href: '/announcements' },
  { icon: 'settings-outline', label: 'Settings', href: '/settings' },
];

/** More tab — a menu hub for less-frequent destinations, matching the 5-tab structure in docs/design/mobile_sample_design.png (ADR-101). */
export default function MoreScreen() {
  const theme = useTheme();
  return (
    <ThemedView style={styles.container}>
      <PlatformStatusSpacer />
      <View style={styles.header}>
        <ThemedText type="smallBold">More</ThemedText>
      </View>
      <View style={styles.list}>
        {ITEMS.map((item) => (
          <Pressable key={item.href} onPress={() => router.push(item.href)}>
            <ThemedView type="backgroundElement" style={styles.row}>
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
  label: {
    flex: 1,
  },
});
