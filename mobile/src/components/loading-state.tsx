import { ActivityIndicator, StyleSheet, View } from 'react-native';

import { Brand, Spacing } from '@/constants/theme';

import { ThemedText } from './themed-text';

export function LoadingState({ label }: { label: string }) {
  return (
    <View style={styles.container}>
      <ActivityIndicator color={Brand.championshipGold} />
      <ThemedText type="small" themeColor="textSecondary">
        {label}
      </ThemedText>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    alignItems: 'center',
    justifyContent: 'center',
    gap: Spacing.two,
    paddingVertical: Spacing.five,
  },
});
