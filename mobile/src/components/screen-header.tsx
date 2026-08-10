import Ionicons from '@expo/vector-icons/Ionicons';
import { router } from 'expo-router';
import type { ReactNode } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';

import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

import { PlatformStatusSpacer } from './platform-status-spacer';
import { ThemedText } from './themed-text';

/** Shared pushed-screen header (Event Details, Announcements, Settings) — back button + title + optional right action. */
export function ScreenHeader({ title, right }: { title: string; right?: ReactNode }) {
  const theme = useTheme();
  return (
    <View>
      <PlatformStatusSpacer />
      <View style={styles.row}>
        <Pressable hitSlop={8} onPress={() => router.back()}>
          <Ionicons name="chevron-back" size={24} color={theme.text} />
        </Pressable>
        <ThemedText type="smallBold" style={styles.title}>
          {title}
        </ThemedText>
        <View style={styles.right}>{right}</View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: Spacing.four,
    paddingVertical: Spacing.two,
  },
  title: {
    flex: 1,
    textAlign: 'center',
  },
  right: {
    minWidth: 24,
    alignItems: 'flex-end',
  },
});
