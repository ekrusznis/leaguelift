import Ionicons from '@expo/vector-icons/Ionicons';
import { NativeTabs } from 'expo-router/unstable-native-tabs';

import { Brand } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

/**
 * Owner persona's bottom tabs — Home/Teams/Members/More (ADR-105). No dedicated
 * Calendar tab (the Dashboard's upcoming-events card covers the common case; a full
 * org calendar screen was left for a later slice) and no Payments tab (Reports and
 * Payout status live under More, alongside Announcements/Broadcasts/Settings — Owner
 * has too many secondary destinations to give each its own tab the way Coach/Parent's
 * single extra domain does).
 */
export default function OwnerTabs() {
  const colors = useTheme();

  return (
    <NativeTabs
      backgroundColor={colors.background}
      indicatorColor={colors.backgroundElement}
      iconColor={{ default: colors.textSecondary, selected: Brand.championshipGold }}
      labelStyle={{ selected: { color: colors.text } }}>
      <NativeTabs.Trigger name="index">
        <NativeTabs.Trigger.Label>Home</NativeTabs.Trigger.Label>
        <NativeTabs.Trigger.Icon src={<NativeTabs.Trigger.VectorIcon family={Ionicons} name="home" />} />
      </NativeTabs.Trigger>

      <NativeTabs.Trigger name="teams">
        <NativeTabs.Trigger.Label>Teams</NativeTabs.Trigger.Label>
        <NativeTabs.Trigger.Icon src={<NativeTabs.Trigger.VectorIcon family={Ionicons} name="shield" />} />
      </NativeTabs.Trigger>

      <NativeTabs.Trigger name="members">
        <NativeTabs.Trigger.Label>Members</NativeTabs.Trigger.Label>
        <NativeTabs.Trigger.Icon src={<NativeTabs.Trigger.VectorIcon family={Ionicons} name="people" />} />
      </NativeTabs.Trigger>

      <NativeTabs.Trigger name="more">
        <NativeTabs.Trigger.Label>More</NativeTabs.Trigger.Label>
        <NativeTabs.Trigger.Icon src={<NativeTabs.Trigger.VectorIcon family={Ionicons} name="ellipsis-horizontal" />} />
      </NativeTabs.Trigger>
    </NativeTabs>
  );
}
