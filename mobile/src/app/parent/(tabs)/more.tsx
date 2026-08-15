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

/**
 * Parent More hub.
 * Fundraising, announcements, documents, messaging safety, Action Center, and Help
 * remain native. Household media and family-credit/fee operations reuse the complete
 * authenticated household web surfaces until those workflows justify dedicated native UI.
 */
export default function ParentMoreScreen() {
  const theme = useTheme();
  const { organizationId, householdId } = useHouseholdCtx();

  const items: {
    icon: keyof typeof Ionicons.glyphMap;
    label: string;
    onPress: () => void;
    requiresHousehold?: boolean;
  }[] = [
    {
      icon: 'shirt-outline',
      label: 'Swag Shop',
      onPress: () =>
        router.push(
          webEmbedRoute(`/app/organizations/${organizationId}/swag-shop/order`, 'Swag Shop'),
        ),
    },
    {
      icon: 'heart-outline',
      label: 'Fundraising',
      onPress: () =>
        router.push({
          pathname: '/fundraising' as any,
          params: { organizationId: organizationId ?? '', persona: 'parent' },
        }),
    },
    {
      icon: 'images-outline',
      label: 'Household Media',
      requiresHousehold: true,
      onPress: () =>
        router.push(
          webEmbedRoute(
            `/app/organizations/${organizationId}/households/${householdId}/media`,
            'Household Media',
          ),
        ),
    },
    {
      icon: 'wallet-outline',
      label: 'Fees & Family Credits',
      requiresHousehold: true,
      onPress: () =>
        router.push(
          webEmbedRoute(
            `/app/organizations/${organizationId}/households/${householdId}/fees`,
            'Fees & Family Credits',
          ),
        ),
    },
    { icon: 'megaphone-outline', label: 'Announcements', onPress: () => router.push('/announcements') },
    { icon: 'document-text-outline', label: 'Documents', onPress: () => router.push('/documents') },
    { icon: 'shield-checkmark-outline', label: 'Messaging Safety', onPress: () => router.push('/safety-controls') },
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
        {items.map((item) => {
          const disabled =
            (item.requiresHousehold && (!organizationId || !householdId)) ||
            ((item.label === 'Swag Shop' || item.label === 'Fundraising') && !organizationId);

          return (
            <Pressable key={item.label} onPress={item.onPress} disabled={disabled}>
              <ThemedView type="backgroundElement" style={[styles.row, disabled && styles.disabled]}>
                <Ionicons name={item.icon} size={20} color={theme.text} />
                <ThemedText style={styles.label}>{item.label}</ThemedText>
                <Ionicons name="chevron-forward" size={18} color={theme.textSecondary} />
              </ThemedView>
            </Pressable>
          );
        })}
      </View>
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  header: { paddingHorizontal: Spacing.four, paddingVertical: Spacing.two },
  list: { paddingHorizontal: Spacing.four, gap: Spacing.two },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.three,
    borderRadius: Spacing.three,
    padding: Spacing.three,
  },
  disabled: { opacity: 0.5 },
  label: { flex: 1 },
});
