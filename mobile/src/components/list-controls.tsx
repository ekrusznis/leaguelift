import Ionicons from '@expo/vector-icons/Ionicons';
import { Pressable, ScrollView, StyleSheet, TextInput, View } from 'react-native';

import { Brand, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

import { ThemedText } from './themed-text';

export function ListControls({
  query,
  onChangeQuery,
  searchPlaceholder = 'Search',
  resultCount,
  activeFilters = [],
  onRemoveFilter,
  onClearFilters,
  onPressFilter,
  onPressSort,
  sortLabel,
}: {
  query: string;
  onChangeQuery: (value: string) => void;
  searchPlaceholder?: string;
  resultCount?: number;
  activeFilters?: string[];
  onRemoveFilter?: (index: number) => void;
  onClearFilters?: () => void;
  onPressFilter?: () => void;
  onPressSort?: () => void;
  sortLabel?: string;
}) {
  const theme = useTheme();
  const hasQueryOrFilters = query.trim().length > 0 || activeFilters.length > 0;
  return (
    <View style={styles.container}>
      <View style={[styles.search, { backgroundColor: theme.backgroundElement }]}> 
        <Ionicons name="search-outline" size={18} color={theme.textSecondary} />
        <TextInput
          accessibilityLabel="Search"
          value={query}
          onChangeText={onChangeQuery}
          placeholder={searchPlaceholder}
          placeholderTextColor={theme.textSecondary}
          style={[styles.searchInput, { color: theme.text }]}
          returnKeyType="search"
        />
        {query.length > 0 && (
          <Pressable accessibilityRole="button" accessibilityLabel="Clear search" hitSlop={8} onPress={() => onChangeQuery('')}>
            <Ionicons name="close-circle" size={20} color={theme.textSecondary} />
          </Pressable>
        )}
      </View>

      <View style={styles.actionRow}>
        {onPressFilter && <ControlButton icon="options-outline" label="Filter" onPress={onPressFilter} />}
        {onPressSort && <ControlButton icon="swap-vertical-outline" label={sortLabel ? `Sort: ${sortLabel}` : 'Sort'} onPress={onPressSort} />}
        {hasQueryOrFilters && onClearFilters && (
          <Pressable accessibilityRole="button" onPress={onClearFilters} style={styles.clearButton}>
            <ThemedText type="small" style={{ color: Brand.infoBlue }}>Clear</ThemedText>
          </Pressable>
        )}
        {resultCount !== undefined && (
          <ThemedText type="small" themeColor="textSecondary" style={styles.resultCount}>
            {resultCount.toLocaleString()} {resultCount === 1 ? 'result' : 'results'}
          </ThemedText>
        )}
      </View>

      {activeFilters.length > 0 && (
        <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.chips}>
          {activeFilters.map((label, index) => (
            <Pressable
              accessibilityRole={onRemoveFilter ? 'button' : undefined}
              accessibilityLabel={onRemoveFilter ? `Remove filter ${label}` : undefined}
              key={`${label}-${index}`}
              onPress={onRemoveFilter ? () => onRemoveFilter(index) : undefined}
              style={[styles.chip, { backgroundColor: theme.backgroundSelected }]}
            >
              <ThemedText type="small">{label}</ThemedText>
              {onRemoveFilter && <Ionicons name="close" size={14} color={theme.text} />}
            </Pressable>
          ))}
        </ScrollView>
      )}
    </View>
  );
}

function ControlButton({ icon, label, onPress }: { icon: keyof typeof Ionicons.glyphMap; label: string; onPress: () => void }) {
  const theme = useTheme();
  return (
    <Pressable accessibilityRole="button" onPress={onPress} style={[styles.controlButton, { borderColor: theme.textSecondary }]}> 
      <Ionicons name={icon} size={16} color={theme.text} />
      <ThemedText type="smallBold">{label}</ThemedText>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  container: { gap: Spacing.two },
  search: { minHeight: 44, borderRadius: Spacing.two, flexDirection: 'row', alignItems: 'center', gap: Spacing.two, paddingHorizontal: Spacing.three },
  searchInput: { flex: 1, minWidth: 0, paddingVertical: 0 },
  actionRow: { flexDirection: 'row', flexWrap: 'wrap', alignItems: 'center', gap: Spacing.two },
  controlButton: { minHeight: 40, borderWidth: 1, borderRadius: Spacing.two, flexDirection: 'row', alignItems: 'center', gap: Spacing.one, paddingHorizontal: Spacing.three },
  clearButton: { minHeight: 40, justifyContent: 'center', paddingHorizontal: Spacing.two },
  resultCount: { marginLeft: 'auto' },
  chips: { gap: Spacing.two, paddingRight: Spacing.four },
  chip: { minHeight: 34, borderRadius: Spacing.four, flexDirection: 'row', alignItems: 'center', gap: Spacing.one, paddingHorizontal: Spacing.three },
});
