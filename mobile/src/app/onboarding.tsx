import { router } from 'expo-router';
import { useRef, useState } from 'react';
import {
  Dimensions,
  FlatList,
  Image,
  Pressable,
  StyleSheet,
  View,
  type NativeSyntheticEvent,
  type NativeScrollEvent,
  type ImageSourcePropType,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { Button } from '@/components/button';
import { ThemedText } from '@/components/themed-text';
import { Brand, Spacing } from '@/constants/theme';
import { useOnboarding } from '@/lib/onboarding';

const { width: SCREEN_WIDTH } = Dimensions.get('window');

interface Slide {
  image: ImageSourcePropType;
  title: string;
  body: string;
}

/** First-launch-only walkthrough. Real founder-supplied illustrations (docs/design/splash2-4.png, ADR-107) — replaces the earlier Ionicons/color-block stand-in from ADR-101. */
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
            <Image source={item.image} resizeMode="cover" style={styles.slideImage} />
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
  slideImage: {
    width: '100%',
    height: '55%',
  },
  slideTextWrap: {
    flex: 1,
    paddingHorizontal: Spacing.five,
    paddingTop: Spacing.five,
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
