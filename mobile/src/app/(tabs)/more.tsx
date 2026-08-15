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

/** Owner More hub. Fundraising and Fees/Collections are native; Swag Shop/Sponsorships retain their current embed paths. */
export default function OwnerMoreScreen() {
  const theme = useTheme();
  const dashboardContext = useDashboardContext(true);
  const organizationId = dashboardContext.data?.organizationId ?? null;
  const items: { icon: keyof typeof Ionicons.glyphMap; label: string; onPress: () => void }[] = [
    { icon: 'megaphone-outline', label: 'Announcements', onPress: () => router.push('/owner/announcements-manage') },
    { icon: 'chatbubbles-outline', label: 'Broadcasts', onPress: () => router.push('/owner/broadcasts-manage') },
    { icon: 'bar-chart-outline', label: 'Reports', onPress: () => router.push('/owner/reports') },
    { icon: 'wallet-outline', label: 'Fees & Collections', onPress: () => router.push('/owner/fees' as any) },
    { icon: 'card-outline', label: 'Payout Account', onPress: () => router.push('/owner/payout') },
    { icon: 'shirt-outline', label: 'Swag Shop', onPress: () => router.push(webEmbedRoute(`/app/organizations/${organizationId}/swag-shop`, 'Swag Shop')) },
    { icon: 'heart-outline', label: 'Fundraising', onPress: () => router.push({ pathname: '/fundraising' as any, params: { organizationId: organizationId ?? '', persona: 'owner' } }) },
    { icon: 'ribbon-outline', label: 'Sponsorships', onPress: () => router.push(webEmbedRoute(`/app/organizations/${organizationId}/sponsorships`, 'Sponsorships')) },
    { icon: 'notifications-outline', label: 'My Announcements', onPress: () => router.push('/announcements') },
    { icon: 'document-text-outline', label: 'Documents', onPress: () => router.push('/owner/documents') },
    { icon: 'checkbox-outline', label: 'Action Center', onPress: () => router.push('/action-center') },
    { icon: 'help-circle-outline', label: 'Help Center', onPress: () => router.push('/help') },
    { icon: 'settings-outline', label: 'Settings', onPress: () => router.push('/settings') },
  ];

  return (
    <ThemedView style={styles.container}>
      <PlatformStatusSpacer />
      <View style={styles.header}><ThemedText type="smallBold">More</ThemedText></View>
      <View style={styles.list}>
        {items.map((item) => (
          <Pressable key={item.label} onPress={item.onPress} disabled={!organizationId && item.label !== 'Settings'}>
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
  row: { flexDirection: 'row', alignItems: 'center', gap: Spacing.three, borderRadius: Spacing.three, padding: Spacing.three },
  label: { flex: 1 },
});
