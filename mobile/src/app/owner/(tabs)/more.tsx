import Ionicons from '@expo/vector-icons/Ionicons';
import { router } from 'expo-router';
import { Pressable, StyleSheet, View } from 'react-native';
import { PlatformStatusSpacer } from '@/components/platform-status-spacer';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { useDashboardContext } from '@/features/dashboard/api';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { webEmbedRoute } from '@/lib/webEmbed';

/**
 * Owner More hub.
 * Native management: Fundraising, Financial Operations, Fees/Collections, Swag Orders,
 * Sponsorships, Payout Account, Documents, and announcements. Org-wide message oversight
 * (formerly a "Broadcasts" entry here) moved to the Messages tab's oversight link —
 * having two differently-labeled paths to the same renamed "Organization Messages"
 * screen was confusing, and the tab is the more discoverable entry point anyway.
 * Complex organization configuration that already has a complete responsive web surface
 * uses the existing authenticated WebView seam instead of duplicating domain logic natively.
 */
export default function OwnerMoreScreen() {
  const theme = useTheme();
  const dashboardContext = useDashboardContext(true);
  const organizationId = dashboardContext.data?.organizationId ?? null;

  const items: { icon: keyof typeof Ionicons.glyphMap; label: string; onPress: () => void }[] = [
    { icon: 'megaphone-outline', label: 'Announcements', onPress: () => router.push('/owner/announcements-manage') },
    { icon: 'bar-chart-outline', label: 'Reports', onPress: () => router.push('/owner/reports') },
    { icon: 'wallet-outline', label: 'Fees & Collections', onPress: () => router.push('/owner/fees' as any) },
    { icon: 'cash-outline', label: 'Financial Operations', onPress: () => router.push('/owner/financial-operations' as any) },
    { icon: 'card-outline', label: 'Payout Account', onPress: () => router.push('/owner/payout') },
    { icon: 'receipt-outline', label: 'Swag Orders', onPress: () => router.push('/owner/orders' as any) },
    {
      icon: 'shirt-outline',
      label: 'Swag Shop',
      onPress: () =>
        router.push(
          webEmbedRoute(`/app/organizations/${organizationId}/swag-shop`, 'Swag Shop'),
        ),
    },
    {
      icon: 'heart-outline',
      label: 'Fundraising',
      onPress: () =>
        router.push({
          pathname: '/fundraising' as any,
          params: { organizationId: organizationId ?? '', persona: 'owner' },
        }),
    },
    {
      icon: 'ribbon-outline',
      label: 'Sponsorships',
      onPress: () => router.push('/owner/sponsorships' as any),
    },
    {
      icon: 'people-outline',
      label: 'Team Management',
      onPress: () =>
        router.push(
          webEmbedRoute(`/app/organizations/${organizationId}/teams`, 'Team Management'),
        ),
    },
    {
      icon: 'trophy-outline',
      label: 'Tournament Management',
      onPress: () =>
        router.push(
          webEmbedRoute(`/app/organizations/${organizationId}/tournaments`, 'Tournament Management'),
        ),
    },
    {
      icon: 'people-circle-outline',
      label: 'Households & Media',
      onPress: () =>
        router.push(
          webEmbedRoute(`/app/organizations/${organizationId}/households`, 'Households & Media'),
        ),
    },
    {
      icon: 'business-outline',
      label: 'Organization Settings',
      onPress: () =>
        router.push(
          webEmbedRoute(`/app/organizations/${organizationId}/settings`, 'Organization Settings'),
        ),
    },
    { icon: 'notifications-outline', label: 'My Announcements', onPress: () => router.push('/announcements') },
    { icon: 'document-text-outline', label: 'Documents', onPress: () => router.push('/owner/documents') },
    { icon: 'checkbox-outline', label: 'Action Center', onPress: () => router.push('/action-center') },
    { icon: 'help-circle-outline', label: 'Help Center', onPress: () => router.push('/help') },
    { icon: 'settings-outline', label: 'My Settings', onPress: () => router.push('/settings') },
  ];

  return (
    <ThemedView style={styles.container}>
      <PlatformStatusSpacer />
      <View style={styles.header}>
        <ThemedText type="smallBold">More</ThemedText>
      </View>
      <View style={styles.list}>
        {items.map((item) => (
          <Pressable
            key={item.label}
            onPress={item.onPress}
            disabled={!organizationId && item.label !== 'My Settings'}>
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
  label: { flex: 1 },
});
