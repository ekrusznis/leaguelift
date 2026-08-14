import { router } from 'expo-router';
import { useRef, useState } from 'react';
import {
  FlatList,
  Image,
  Pressable,
  StyleSheet,
  View,
  useWindowDimensions,
  type NativeSyntheticEvent,
  type NativeScrollEvent,
  type ImageSourcePropType,
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
  /** 'top' pins the image's top edge to the frame's top edge instead of centering the cover-crop, so illustration content near the top of the source file isn't clipped. */
  imageAlign?: 'center' | 'top';
}

/**
 * First-launch-only walkthrough. Real founder-supplied illustrations
 * (docs/design/splash2-4.png, ADR-107) — replaces the earlier Ionicons/color-block
 * stand-in from ADR-101. Slides 2 and 3 use `imageAlign: 'top'` (Phase 37 slice
 * 37.13) — `resizeMode="cover"` always center-crops in React Native's built-in Image
 * component (no `object-position` equivalent), which was clipping the top of those
 * two illustrations; slide 1's framing already reads correctly centered.
 *
 * The hero frame is sized from the live window height rather than a fixed percentage
 * of the remaining FlatList area. This keeps the illustration and copy visually grouped
 * on phones while capping the artwork on tablets/foldables so it does not push the copy
 * too far down the screen.
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
    imageAlign: 'top',
  },
  {
    image: require('../../assets/images/onboarding-3.png'),
    title: 'Focus on What Matters',
    body: 'Less admin. More team. Let’s make this your best season yet.',
    imageAlign: 'top',
  },
];

export default function OnboardingScreen() {
  const [index, setIndex] = useState(0);
  const listRef = useRef<FlatList<Slide>>(null);
  const { markSeen } = useOnboarding();
  const { width: screenWidth, height: screenHeight } = useWindowDimensions();

  // Pull the artwork closer to the copy on tall phones while keeping a useful minimum
  // on compact devices and preventing oversized hero art on tablets/foldables.
  const imageFrameHeight = Math.min(Math.max(screenHeight * 0.36, 250), 400);

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
        getItemLayout={(_, itemIndex) => ({ length: screenWidth, offset: screenWidth * itemIndex, index: itemIndex })}
        renderItem={({ item }) => (
          <View style={[styles.slide, { width: screenWidth }]}>
            <View style={[styles.slideImageFrame, { height: imageFrameHeight }]}>
              <Image
                source={item.image}
                resizeMode="cover"
                style={item.imageAlign === 'top' ? styles.slideImageTopAligned : styles.slideImage}
              />
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
  },
  slideImageFrame: {
    width: '100%',
    overflow: 'hidden',
  },
  slideImage: {
    width: '100%',
    height: '100%',
  },
  slideImageTopAligned: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    width: '100%',
    height: '135%',
  },
  slideTextWrap: {
    flex: 1,
    paddingHorizontal: Spacing.five,
    paddingTop: Spacing.three,
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
