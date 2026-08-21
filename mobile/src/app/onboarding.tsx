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

const SLIDES: Slide[] = [
  {
    image: require('../../assets/images/onboarding-1-art.png'),
    title: 'Built for Teams',
    body: 'Rally26 brings your team closer with tools that make communication and organization easy.',
  },
  {
    image: require('../../assets/images/onboarding-2-art.png'),
    title: 'Stay in the Loop',
    body: 'Get updates, reminders, and important info — all in one place.',
  },
  {
    image: require('../../assets/images/onboarding-3-art.png'),
    title: 'Focus on What Matters',
    body: 'Less admin. More team. Let’s make this your best season yet.',
  },
];

/**
 * First-launch walkthrough.
 *
 * The artwork files are transparent, tightly-cropped illustrations rather than
 * full-screen screenshots. The slide owns the title/body and places the artwork
 * immediately above the fixed bottom controls. This avoids the visible image
 * rectangle/crop that occurred when a full onboarding composition was rendered
 * inside an Image frame.
 */
export default function OnboardingScreen() {
  const [index, setIndex] = useState(0);
  const listRef = useRef<FlatList<Slide>>(null);
  const { markSeen } = useOnboarding();
  const { width: screenWidth, height: screenHeight } = useWindowDimensions();

  const compactHeight = screenHeight < 720;
  const largeHeight = screenHeight > 900;

  // Space reserved for dots + CTA + Skip + bottom safe area.
  // The final slide has no Skip action, but using one consistent reserve prevents
  // a noticeable vertical jump while swiping between slides.
  const footerReserve = compactHeight ? 148 : 178;

  // Art is intentionally smaller than the previous full-width screenshot image.
  // It scales with device height but remains bounded on tablets/foldables.
  const imageFrameHeight = Math.min(
    Math.max(screenHeight * (compactHeight ? 0.28 : largeHeight ? 0.35 : 0.32), compactHeight ? 190 : 230),
    360,
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
        bounces={false}
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
              <View
                style={[
                  styles.slideContent,
                  {
                    width: contentWidth,
                    paddingBottom: footerReserve,
                    paddingTop: compactHeight ? Spacing.three : Spacing.five,
                  },
                ]}>
                <View style={styles.slideTextWrap}>
                  <ThemedText type="title" style={styles.slideTitle}>
                    {item.title}
                  </ThemedText>
                  <ThemedText style={styles.slideBody} themeColor="textSecondary">
                    {item.body}
                  </ThemedText>
                </View>

                <View style={styles.slideImageZone}>
                  <View style={[styles.slideImageFrame, { height: imageFrameHeight }]}>
                    <Image
                      source={item.image}
                      resizeMode="contain"
                      style={styles.slideImage}
                      accessible
                      accessibilityLabel={`${item.title} illustration`}
                    />
                  </View>
                </View>
              </View>
            </SafeAreaView>
          </View>
        )}
      />

      <SafeAreaView style={styles.footer} edges={['bottom']}>
        <View style={styles.dots} accessibilityRole="adjustable" accessibilityLabel={`Onboarding page ${index + 1} of ${SLIDES.length}`}>
          {SLIDES.map((slide, i) => (
            <View key={slide.title} style={[styles.dot, i === index && styles.dotActive]} />
          ))}
        </View>

        <Button onPress={next} style={styles.primaryButton}>
          {isLast ? 'Get Started' : 'Next'}
        </Button>

        {!isLast && (
          <Pressable
            onPress={finish}
            style={styles.skip}
            accessibilityRole="button"
            accessibilityLabel="Skip onboarding">
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
    alignItems: 'center',
  },
  slideContent: {
    flex: 1,
    maxWidth: 720,
    alignItems: 'center',
    paddingHorizontal: Spacing.five,
  },
  slideTextWrap: {
    width: '100%',
    maxWidth: 600,
    alignItems: 'center',
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
  slideImageZone: {
    flex: 1,
    width: '100%',
    alignItems: 'center',
    justifyContent: 'flex-end',
    paddingTop: Spacing.three,
  },
  slideImageFrame: {
    width: '100%',
    maxWidth: 620,
    alignItems: 'center',
    justifyContent: 'flex-end',
  },
  slideImage: {
    width: '100%',
    height: '100%',
  },
  footer: {
    position: 'absolute',
    left: 0,
    right: 0,
    bottom: 0,
    backgroundColor: Brand.navy,
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
