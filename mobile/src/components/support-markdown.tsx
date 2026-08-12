import { type ReactNode } from 'react';
import { Linking, StyleSheet, View } from 'react-native';

import { Spacing } from '@/constants/theme';

import { ThemedText } from './themed-text';

/**
 * Ported from frontend/src/features/support/SupportMarkdown.tsx (Phase 37.11, ADR-119)
 * — not a markdown library, web's own hand-rolled small-safe-subset parser (headings,
 * paragraphs, un/ordered lists, bold, links; raw HTML never interpreted). Kept as the
 * exact same block/inline split logic so an article renders identically on both
 * platforms; only the JSX output differs (View/ThemedText instead of div/p/ul/a).
 */
function inline(text: string, keyPrefix: string): ReactNode[] {
  const parts = text.split(/(\[[^\]]+\]\([^)]+\)|\*\*[^*]+\*\*)/g).filter(Boolean);
  return parts.map((part, index) => {
    const key = `${keyPrefix}-${index}`;
    const link = /^\[([^\]]+)\]\(([^)]+)\)$/.exec(part);
    if (link) {
      const safe = link[2].startsWith('/') || link[2].startsWith('https://') ? link[2] : null;
      return (
        <ThemedText key={key} type="linkPrimary" onPress={safe ? () => void Linking.openURL(safe) : undefined}>
          {link[1]}
        </ThemedText>
      );
    }
    if (part.startsWith('**') && part.endsWith('**')) {
      return (
        <ThemedText key={key} type="smallBold">
          {part.slice(2, -2)}
        </ThemedText>
      );
    }
    return part;
  });
}

export function SupportMarkdown({ body }: { body: string }) {
  const blocks = body
    .replace(/\r\n/g, '\n')
    .split(/\n\s*\n/)
    .map((block) => block.trim())
    .filter(Boolean);

  return (
    <View style={styles.container}>
      {blocks.map((block, index) => {
        const key = `block-${index}`;
        if (block.startsWith('### ')) {
          return (
            <ThemedText key={key} type="subtitle">
              {inline(block.slice(4), key)}
            </ThemedText>
          );
        }
        if (block.startsWith('## ')) {
          return (
            <ThemedText key={key} type="title" style={styles.heading}>
              {inline(block.slice(3), key)}
            </ThemedText>
          );
        }
        const lines = block.split('\n');
        if (lines.every((line) => /^[-*] /.test(line))) {
          return (
            <View key={key} style={styles.list}>
              {lines.map((line, lineIndex) => (
                <View key={`${key}-${lineIndex}`} style={styles.listRow}>
                  <ThemedText themeColor="textSecondary">{'•'}</ThemedText>
                  <ThemedText style={styles.listItemText}>{inline(line.slice(2), `${key}-${lineIndex}`)}</ThemedText>
                </View>
              ))}
            </View>
          );
        }
        if (lines.every((line) => /^\d+\. /.test(line))) {
          return (
            <View key={key} style={styles.list}>
              {lines.map((line, lineIndex) => {
                const match = /^(\d+)\. (.*)$/.exec(line);
                return (
                  <View key={`${key}-${lineIndex}`} style={styles.listRow}>
                    <ThemedText themeColor="textSecondary">{match?.[1]}.</ThemedText>
                    <ThemedText style={styles.listItemText}>{inline(match?.[2] ?? '', `${key}-${lineIndex}`)}</ThemedText>
                  </View>
                );
              })}
            </View>
          );
        }
        return <ThemedText key={key}>{inline(block, key)}</ThemedText>;
      })}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    gap: Spacing.three,
  },
  heading: {
    fontSize: 20,
    lineHeight: 26,
  },
  list: {
    gap: Spacing.two,
  },
  listRow: {
    flexDirection: 'row',
    gap: Spacing.two,
  },
  listItemText: {
    flex: 1,
  },
});
