import { router } from 'expo-router';
import { useRef, useState } from 'react';
import {
  FlatList,
  Image,
  Pressable,
  StyleSheet,
  View,
  useWindowDimensions,
  type ImageSourcePropType,
  type NativeScrollEvent,
  type NativeSyntheticEvent,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { Button } from '@/components/button';
import { ThemedText } from '@/components/themed-text';
import { Brand, Spacing } from '@/constants/theme';
import { useOnboarding } from '@/lib/onboarding';

interface Slide {
  image: ImageSourcePropType;
  title: string;
  body: string;
}

/**
 * First-launch-only walkthrough.
 *
 * Layout notes:
 * - The pager fills the entire screen.
 * - The hero illustration + title + body are centered as one visual group.
 * - Bottom controls are overlaid on the same navy canvas instead of taking
 *   vertical space away from the slide.
 * - Artwork uses `contain` so the full illustration remains visible without
 *   the old top-aligned 135% crop workaround.
 */
const SLIDES: Slide[] = [
  {
    image: require('../../assets/images/onboarding-1.png'),
    title: 'Built for Teams',
    body: 'Rally26 brings your team closer with tools that make communication and organization easy.',
  },
  {
    image: require('../../assets/images/onboarding-2.png'),
    title: 'Stay in the Loop',
    body: 'Get updates, reminders, and important info — all in one place.',
  },
  {
    image: require('../../assets/images/onboarding-3.png'),
    title: 'Focus on What Matters',
    body: 'Less admin. More team. Let’s make this your best season yet.',
  },
];

export default function OnboardingScreen() {
  const [index, setIndex] = useState(0);
  const listRef = useRef<FlatList<Slide>>(null);
  const { markSeen } = useOnboarding();
  const { width: screenWidth, height: screenHeight } = useWindowDimensions();

  const compactHeight = screenHeight < 720;

  // Reserve enough space for the overlaid dots / CTA / Skip controls so the
  // centered hero group never sits behind them.
  const footerReserve = compactHeight ? 150 : 185;

  // Keep the illustration substantial on phones, but prevent it from becoming
  // oversized on tablets/foldables.
  const imageFrameHeight = Math.min(
    Math.max(screenHeight * (compactHeight ? 0.33 : 0.38), compactHeight ? 215 : 260),
    420,
  );

  const contentWidth = Math.min(screenWidth, 720);

  async function finish() {
    await markSeen();
    router.replace('/');
  }

  function next() {
    if (index === SLIDES.length - 1) {
      void finish();
      return;
    }

    listRef.current?.scrollToIndex({
      index: index + 1,
      animated: true,
    });
  }

  function onScroll(event: NativeSyntheticEvent<NativeScrollEvent>) {
    const nextIndex = Math.round(event.nativeEvent.contentOffset.x / screenWidth);
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
        style={styles.pager}
        getItemLayout={(_, itemIndex) => ({
          length: screenWidth,
          offset: screenWidth * itemIndex,
          index: itemIndex,
        })}
        renderItem={({ item }) => (
          <View style={[styles.slide, { width: screenWidth }]}>
            <SafeAreaView style={styles.slideSafeArea} edges={['top']}>
              <View style={[styles.slideContent, { paddingBottom: footerReserve }]}>
                <View style={[styles.heroGroup, { width: contentWidth }]}>
                  <View style={[styles.slideImageFrame, { height: imageFrameHeight }]}>
                    <Image source={item.image} resizeMode="contain" style={styles.slideImage} />
                  </View>

                  <View style={styles.slideTextWrap}>
                    <ThemedText type="title" style={styles.slideTitle}>
                      {item.title}
                    </ThemedText>
                    <ThemedText style={styles.slideBody} themeColor="textSecondary">
                      {item.body}
                    </ThemedText>
                  </View>
                </View>
              </View>
            </SafeAreaView>
          </View>
        )}
      />

      <SafeAreaView style={styles.footer} edges={['bottom']}>
        <View style={styles.dots}>
          {SLIDES.map((slide, i) => (
            <View key={slide.title} style={[styles.dot, i === index && styles.dotActive]} />
          ))}
        </View>

        <Button onPress={next} style={styles.primaryButton}>
          {isLast ? 'Get Started' : 'Next'}
        </Button>

        {!isLast && (
          <Pressable onPress={finish} style={styles.skip} accessibilityRole="button">
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
  pager: {
    ...StyleSheet.absoluteFillObject,
  },
  slide: {
    flex: 1,
    backgroundColor: Brand.navy,
  },
  slideSafeArea: {
    flex: 1,
  },
  slideContent: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    paddingHorizontal: Spacing.four,
    paddingTop: Spacing.two,
  },
  heroGroup: {
    alignItems: 'center',
    alignSelf: 'center',
  },
  slideImageFrame: {
    width: '100%',
    alignItems: 'center',
    justifyContent: 'center',
  },
  slideImage: {
    width: '100%',
    height: '100%',
  },
  slideTextWrap: {
    width: '100%',
    maxWidth: 600,
    alignItems: 'center',
    paddingHorizontal: Spacing.two,
    marginTop: Spacing.three,
  },
  slideTitle: {
    textAlign: 'center',
    marginBottom: Spacing.three,
  },
  slideBody: {
    textAlign: 'center',
    lineHeight: 22,
    maxWidth: 560,
  },
  footer: {
    position: 'absolute',
    left: 0,
    right: 0,
    bottom: 0,
    paddingHorizontal: Spacing.five,
    paddingTop: Spacing.two,
    paddingBottom: Spacing.two,
    gap: Spacing.two,
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
  primaryButton: {
    minHeight: 52,
  },
  skip: {
    minHeight: 44,
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: Spacing.two,
  },
});
