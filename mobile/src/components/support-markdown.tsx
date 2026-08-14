import { type ReactNode } from 'react';
import { Image, Linking, Pressable, StyleSheet, View } from 'react-native';
import { WebView } from 'react-native-webview';

import { Spacing } from '@/constants/theme';
import { env } from '@/lib/env';

import { ThemedText } from './themed-text';

const VIDEO_EXTENSIONS = ['.mp4', '.mov', '.webm'];

function resolveSafeUrl(url: string): string | null {
  if (url.startsWith('https://')) return url;
  if (url.startsWith('/')) return `${env.frontendBaseUrl.replace(/\/$/, '')}${url}`;
  return null;
}

function cleanPath(url: string): string {
  return url.split(/[?#]/)[0]?.toLowerCase() ?? '';
}

function isVideoUrl(url: string): boolean {
  const path = cleanPath(url);
  return VIDEO_EXTENSIONS.some((extension) => path.endsWith(extension));
}

function isGifUrl(url: string): boolean {
  return cleanPath(url).endsWith('.gif');
}

function isPdfUrl(url: string): boolean {
  return cleanPath(url).endsWith('.pdf');
}

function escapeHtmlAttribute(value: string): string {
  return value
    .replaceAll('&', '&amp;')
    .replaceAll('"', '&quot;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;');
}

function richMediaHtml(url: string, alt: string, video: boolean): string {
  const safeUrl = escapeHtmlAttribute(url);
  const safeAlt = escapeHtmlAttribute(alt || (video ? 'Help video' : 'Help animation'));
  const media = video
    ? `<video src="${safeUrl}" aria-label="${safeAlt}" controls playsinline preload="metadata"></video>`
    : `<img src="${safeUrl}" alt="${safeAlt}" />`;
  return `<!doctype html>
<html><head><meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1" />
<style>
html,body{margin:0;padding:0;background:transparent;width:100%;height:100%;overflow:hidden}
body{display:flex;align-items:center;justify-content:center}
video,img{display:block;width:100%;height:100%;object-fit:contain;border-radius:12px}
</style></head><body>${media}</body></html>`;
}

function MediaEmbed({ alt, rawUrl }: { alt: string; rawUrl: string }) {
  const url = resolveSafeUrl(rawUrl);
  if (!url) return null;

  if (isPdfUrl(url)) {
    return (
      <Pressable accessibilityRole="link" onPress={() => void Linking.openURL(url)} style={styles.documentLink}>
        <ThemedText type="smallBold">{alt || 'Open attached PDF'}</ThemedText>
        <ThemedText type="linkPrimary">Open document</ThemedText>
      </Pressable>
    );
  }

  if (isVideoUrl(url) || isGifUrl(url)) {
    return (
      <View style={styles.richMediaFrame} accessibilityLabel={alt || undefined}>
        <WebView
          source={{ html: richMediaHtml(url, alt, isVideoUrl(url)) }}
          originWhitelist={['about:blank', 'https://*']}
          scrollEnabled={false}
          allowsInlineMediaPlayback
          mediaPlaybackRequiresUserAction
          javaScriptEnabled={false}
          style={styles.webMedia}
        />
      </View>
    );
  }

  return <Image source={{ uri: url }} accessibilityLabel={alt || undefined} resizeMode="contain" style={styles.image} />;
}

/**
 * Native counterpart to frontend/src/features/support/SupportMarkdown.tsx.
 * It deliberately implements only Rally26's safe Help-Center Markdown subset; raw HTML
 * is never interpreted. Standalone ![alt](url) blocks render image/GIF/video/PDF media.
 */
function inline(text: string, keyPrefix: string): ReactNode[] {
  const parts = text.split(/(\[[^\]]+\]\([^)]+\)|\*\*[^*]+\*\*)/g).filter(Boolean);
  return parts.map((part, index) => {
    const key = `${keyPrefix}-${index}`;
    const link = /^\[([^\]]+)\]\(([^)]+)\)$/.exec(part);
    if (link) {
      const safe = resolveSafeUrl(link[2]);
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
        const standaloneEmbed = /^!\[([^\]]*)\]\(([^)]+)\)$/.exec(block);
        if (standaloneEmbed) {
          return <MediaEmbed key={key} alt={standaloneEmbed[1]} rawUrl={standaloneEmbed[2]} />;
        }
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
  image: {
    width: '100%',
    height: 260,
    borderRadius: 12,
  },
  richMediaFrame: {
    width: '100%',
    height: 260,
    borderRadius: 12,
    overflow: 'hidden',
  },
  webMedia: {
    flex: 1,
    backgroundColor: 'transparent',
  },
  documentLink: {
    gap: Spacing.one,
    paddingVertical: Spacing.two,
  },
});
