import { router, useLocalSearchParams } from 'expo-router';
import { ScrollView, StyleSheet } from 'react-native';

import { Button } from '@/components/button';
import { ErrorState } from '@/components/error-state';
import { LoadingState } from '@/components/loading-state';
import { ScreenHeader } from '@/components/screen-header';
import { SupportMarkdown } from '@/components/support-markdown';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { useHelpArticle } from '@/features/support/api';
import { Brand, Spacing } from '@/constants/theme';

/** Help article detail (Phase 37.11, ADR-119) — mirrors frontend/src/features/support/HelpArticlePage.tsx. */
export default function HelpArticleScreen() {
  const { slug } = useLocalSearchParams<{ slug: string }>();
  const articleQuery = useHelpArticle(slug);
  const article = articleQuery.data;

  return (
    <ThemedView style={styles.container}>
      <ScreenHeader title="Help Article" />
      <ScrollView contentContainerStyle={styles.content}>
        {articleQuery.isLoading && <LoadingState label="Loading article…" />}
        {articleQuery.isError && <ErrorState message="Could not load this article." onRetry={() => articleQuery.refetch()} />}
        {article && (
          <>
            <ThemedText type="small" style={{ color: Brand.infoBlue }}>
              {article.category}
            </ThemedText>
            <ThemedText type="title" style={styles.title}>
              {article.title}
            </ThemedText>
            <ThemedText themeColor="textSecondary" style={styles.summary}>
              {article.summary}
            </ThemedText>
            <SupportMarkdown body={article.bodyMarkdown} />
            <Button variant="secondary" style={styles.contactButton} onPress={() => router.push('/support-request')}>
              Contact support
            </Button>
          </>
        )}
      </ScrollView>
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  content: {
    paddingHorizontal: Spacing.four,
    paddingBottom: Spacing.six,
    gap: Spacing.two,
  },
  title: {
    fontSize: 26,
    lineHeight: 32,
  },
  summary: {
    marginBottom: Spacing.two,
  },
  contactButton: {
    marginTop: Spacing.four,
    alignSelf: 'flex-start',
  },
});
