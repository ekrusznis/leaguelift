import { ActivityIndicator, Pressable, StyleSheet, View } from 'react-native';

import { Brand, Spacing } from '@/constants/theme';

import { ThemedText } from './themed-text';

export function ListFooter({
  loadedCount,
  totalCount,
  hasMore,
  loadingMore = false,
  onLoadMore,
}: {
  loadedCount: number;
  totalCount?: number;
  hasMore: boolean;
  loadingMore?: boolean;
  onLoadMore?: () => void;
}) {
  if (loadedCount === 0) return null;
  return (
    <View style={styles.container}>
      {totalCount !== undefined && (
        <ThemedText type="small" themeColor="textSecondary">
          Showing {loadedCount.toLocaleString()} of {totalCount.toLocaleString()}
        </ThemedText>
      )}
      {hasMore && onLoadMore ? (
        <Pressable accessibilityRole="button" disabled={loadingMore} onPress={onLoadMore} style={styles.button}>
          {loadingMore ? <ActivityIndicator color={Brand.navy} /> : <ThemedText type="smallBold" style={styles.buttonText}>Load more</ThemedText>}
        </Pressable>
      ) : (
        <ThemedText type="small" themeColor="textSecondary">End of results</ThemedText>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { alignItems: 'center', gap: Spacing.two, paddingVertical: Spacing.four },
  button: { minHeight: 44, minWidth: 140, borderRadius: Spacing.two, alignItems: 'center', justifyContent: 'center', paddingHorizontal: Spacing.four, backgroundColor: Brand.championshipGold },
  buttonText: { color: Brand.navy },
});
