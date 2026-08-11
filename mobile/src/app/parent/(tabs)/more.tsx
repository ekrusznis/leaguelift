import Ionicons from '@expo/vector-icons/Ionicons';
import { router } from 'expo-router';
import { Pressable, StyleSheet, View } from 'react-native';

import { PlatformStatusSpacer } from '@/components/platform-status-spacer';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { useHouseholdCtx } from '@/features/household/HouseholdContext';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { webEmbedRoute } from '@/lib/webEmbed';

/** More tab — parent persona menu hub (ADR-103/106). Swag Shop added ADR-106 — real frontend/ order flow via WebView, not rebuilt natively. */
export default function ParentMoreScreen() {
  const theme = useTheme();
  const { organizationId } = useHouseholdCtx();

  const items: { icon: keyof typeof Ionicons.glyphMap; label: string; onPress: () => void }[] = [
    {
      icon: 'shirt-outline',
      label: 'Swag Shop',
      onPress: () => router.push(webEmbedRoute(`/app/organizations/${organizationId}/swag-shop/order`, 'Swag Shop')),
    },
    { icon: 'megaphone-outline', label: 'Announcements', onPress: () => router.push('/announcements') },
    { icon: 'document-text-outline', label: 'Documents', onPress: () => router.push('/documents') },
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
