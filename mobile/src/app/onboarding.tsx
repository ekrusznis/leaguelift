import Ionicons from '@expo/vector-icons/Ionicons';
import { router } from 'expo-router';
import { useRef, useState } from 'react';
import { Dimensions, FlatList, Pressable, StyleSheet, View, type NativeSyntheticEvent, type NativeScrollEvent } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { Button } from '@/components/button';
import { ThemedText } from '@/components/themed-text';
import { Brand, Spacing } from '@/constants/theme';
import { useOnboarding } from '@/lib/onboarding';

const { width: SCREEN_WIDTH } = Dimensions.get('window');

interface Slide {
  icon: keyof typeof Ionicons.glyphMap;
  title: string;
  body: string;
}

/**
 * First-launch-only walkthrough. Copy/icons approximate docs/design/mobile_sample_design.png's
 * "ONBOARDING SCREENS" (ADR-101) — real illustrations aren't generated yet, using
 * Ionicons + brand color blocks as a faithful stand-in for the mockup's hero art.
 */
const SLIDES: Slide[] = [
  {
    icon: 'people',
    title: 'Built for Teams',
    body: 'Rally26 brings your team closer with tools that make communication and organization easy.',
  },
  {
    icon: 'megaphone',
    title: 'Stay in the Loop',
    body: 'Get updates, reminders, and important info — all in one place.',
  },
  {
    icon: 'trophy',
    title: 'Focus on What Matters',
    body: 'Less admin. More team. Let’s make this your best season yet.',
  },
];

export default function OnboardingScreen() {
  const [index, setIndex] = useState(0);
  const listRef = useRef<FlatList<Slide>>(null);
  const { markSeen } = useOnboarding();

  async function finish() {
    await markSeen();
    router.replace('/');
  }

  function next() {
    if (index === SLIDES.length - 1) {
      void finish();
      return;
    }
    listRef.current?.scrollToIndex({ index: index + 1 });
  }

  function onScroll(event: NativeSyntheticEvent<NativeScrollEvent>) {
    const nextIndex = Math.round(event.nativeEvent.contentOffset.x / SCREEN_WIDTH);
    if (nextIndex !== index) setIndex(nextIndex);
  }

  const isLast = index === SLIDES.length - 1;

  return (
    <View style={styles.container}>
      <FlatList
        ref={listRef}
        data={SLIDES}
        keyExtractor={(slide) => slide.title}
        horizontal
        pagingEnabled
        showsHorizontalScrollIndicator={false}
        onMomentumScrollEnd={onScroll}
        renderItem={({ item }) => (
          <View style={[styles.slide, { width: SCREEN_WIDTH }]}>
            <View style={styles.iconWrap}>
              <Ionicons name={item.icon} size={72} color={Brand.championshipGold} />
            </View>
            <ThemedText type="title" style={styles.slideTitle}>
              {item.title}
            </ThemedText>
            <ThemedText style={styles.slideBody} themeColor="textSecondary">
              {item.body}
            </ThemedText>
          </View>
        )}
      />

      <SafeAreaView style={styles.footer} edges={['bottom']}>
        <View style={styles.dots}>
          {SLIDES.map((slide, i) => (
            <View key={slide.title} style={[styles.dot, i === index && styles.dotActive]} />
          ))}
        </View>

        <Button onPress={next}>{isLast ? 'Get Started' : 'Next'}</Button>

        {!isLast && (
          <Pressable onPress={finish} style={styles.skip}>
            <ThemedText themeColor="textSecondary">Skip</ThemedText>
          </Pressable>
        )}
      </SafeAreaView>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: Brand.navy,
  },
  slide: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: Spacing.five,
  },
  iconWrap: {
    width: 140,
    height: 140,
    borderRadius: 70,
    backgroundColor: '#102B46',
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: Spacing.five,
  },
  slideTitle: {
    textAlign: 'center',
    marginBottom: Spacing.three,
  },
  slideBody: {
    textAlign: 'center',
    lineHeight: 22,
  },
  footer: {
    paddingHorizontal: Spacing.five,
    paddingBottom: Spacing.three,
    gap: Spacing.three,
  },
  dots: {
    flexDirection: 'row',
    justifyContent: 'center',
    gap: Spacing.two,
    marginBottom: Spacing.two,
  },
  dot: {
    width: 8,
    height: 8,
    borderRadius: 4,
    backgroundColor: '#173B5C',
  },
  dotActive: {
    backgroundColor: Brand.championshipGold,
  },
  skip: {
    alignItems: 'center',
    paddingVertical: Spacing.two,
  },
});
