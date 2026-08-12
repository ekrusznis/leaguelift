import Ionicons from '@expo/vector-icons/Ionicons';
import { router } from 'expo-router';
import { Pressable, StyleSheet, View } from 'react-native';

import { PlatformStatusSpacer } from '@/components/platform-status-spacer';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { useCoach } from '@/features/teams/CoachContext';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { webEmbedRoute } from '@/lib/webEmbed';

/** More tab — a menu hub for less-frequent destinations, matching the 5-tab structure in docs/design/mobile_sample_design.png (ADR-101). Swag Shop added ADR-106 — real frontend/ order flow via WebView, not rebuilt natively. */
export default function MoreScreen() {
  const theme = useTheme();
  const { organizationId } = useCoach();

  const items: { icon: keyof typeof Ionicons.glyphMap; label: string; onPress: () => void }[] = [
    {
      icon: 'shirt-outline',
      label: 'Swag Shop',
      onPress: () => router.push(webEmbedRoute(`/app/organizations/${organizationId}/swag-shop/order`, 'Swag Shop')),
    },
    { icon: 'megaphone-outline', label: 'Announcements', onPress: () => router.push('/announcements') },
    { icon: 'checkbox-outline', label: 'Action Center', onPress: () => router.push('/action-center') },
    { icon: 'help-circle-outline', label: 'Help Center', onPress: () => router.push('/help') },
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
          <Pressable key={item.label} onPress={item.onPress} disabled={item.label === 'Swag Shop' && !organizationId}>
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
