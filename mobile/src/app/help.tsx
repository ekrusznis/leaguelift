import Ionicons from '@expo/vector-icons/Ionicons';
import { router } from 'expo-router';
import { useState } from 'react';
import { Pressable, ScrollView, StyleSheet, TextInput, View } from 'react-native';

import { EmptyState } from '@/components/empty-state';
import { ErrorState } from '@/components/error-state';
import { LoadingState } from '@/components/loading-state';
import { ScreenHeader } from '@/components/screen-header';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { useHelpArticles } from '@/features/support/api';
import { HELP_CATEGORIES } from '@/features/support/types';
import { Brand, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

/**
 * Cross-persona Help Center (Phase 37.11, ADR-119) — mirrors
 * frontend/src/features/support/HelpCenterPage.tsx's authenticated variant
 * (search + category filter over GET /help/articles).
 */
export default function HelpCenterScreen() {
  const theme = useTheme();
  const [query, setQuery] = useState('');
  const [category, setCategory] = useState<string | null>(null);
  const articlesQuery = useHelpArticles(query, category ?? undefined);

  return (
    <ThemedView style={styles.container}>
      <ScreenHeader
        title="Help Center"
        right={
          <Pressable hitSlop={8} onPress={() => router.push('/support-request')}>
            <Ionicons name="chatbox-ellipses-outline" size={22} color={theme.text} />
          </Pressable>
        }
      />
      <View style={styles.searchRow}>
        <TextInput
          value={query}
          onChangeText={setQuery}
          placeholder="Search help articles"
          placeholderTextColor={theme.textSecondary}
          style={[styles.searchInput, { color: theme.text, backgroundColor: theme.backgroundElement }]}
        />
      </View>
      <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.categoryRow}>
        <CategoryChip label="All" active={category === null} onPress={() => setCategory(null)} />
        {HELP_CATEGORIES.map((item) => (
          <CategoryChip key={item} label={item} active={category === item} onPress={() => setCategory(item)} />
        ))}
      </ScrollView>
      <ScrollView contentContainerStyle={styles.list}>
        {articlesQuery.isLoading && <LoadingState label="Loading help articles…" />}
        {articlesQuery.isError && <ErrorState message="Could not load help articles." onRetry={() => articlesQuery.refetch()} />}
        {articlesQuery.data && articlesQuery.data.length === 0 && (
          <EmptyState title="No articles found" description="Try a different search or category." />
        )}
        {articlesQuery.data?.map((article) => (
          <Pressable key={article.id} onPress={() => router.push({ pathname: '/help/[slug]', params: { slug: article.slug } })}>
            <ThemedView type="backgroundElement" style={styles.card}>
              <ThemedText type="smallBold">{article.title}</ThemedText>
              <ThemedText type="small" themeColor="textSecondary" numberOfLines={2}>
                {article.summary}
              </ThemedText>
              <ThemedText type="small" style={{ color: Brand.infoBlue }}>
                {article.category}
              </ThemedText>
            </ThemedView>
          </Pressable>
        ))}
      </ScrollView>
    </ThemedView>
  );
}

function CategoryChip({ label, active, onPress }: { label: string; active: boolean; onPress: () => void }) {
  return (
    <Pressable onPress={onPress} style={[styles.chip, active && { backgroundColor: Brand.championshipGold }]}>
      <ThemedText type="small" themeColor={active ? undefined : 'textSecondary'}>
        {label}
      </ThemedText>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  searchRow: {
    paddingHorizontal: Spacing.four,
    paddingBottom: Spacing.two,
  },
  searchInput: {
    minHeight: 44,
    borderRadius: Spacing.two,
    paddingHorizontal: Spacing.three,
  },
  categoryRow: {
    paddingHorizontal: Spacing.four,
    gap: Spacing.two,
    paddingBottom: Spacing.three,
  },
  chip: {
    paddingHorizontal: Spacing.three,
    paddingVertical: Spacing.one,
    borderRadius: Spacing.four,
    backgroundColor: '#102B46',
  },
  list: {
    paddingHorizontal: Spacing.four,
    paddingBottom: Spacing.six,
    gap: Spacing.two,
  },
  card: {
    borderRadius: Spacing.three,
    padding: Spacing.three,
    gap: Spacing.one,
  },
});
