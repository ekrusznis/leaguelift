import * as SplashScreen from 'expo-splash-screen';
import { useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import Animated, { Easing, Keyframe } from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

import { Brand } from '@/constants/theme';

const DURATION = 400;

/** A handful of static "confetti" accents approximating the mockup's photographic
 * splash background — real generated brand assets replace this later. */
const CONFETTI = [
  { top: '12%', left: '18%', size: 10, rotate: '20deg' },
  { top: '20%', left: '78%', size: 8, rotate: '-15deg' },
  { top: '68%', left: '12%', size: 9, rotate: '45deg' },
  { top: '75%', left: '82%', size: 11, rotate: '-30deg' },
  { top: '40%', left: '6%', size: 7, rotate: '10deg' },
  { top: '55%', left: '90%', size: 8, rotate: '25deg' },
] as const;

/**
 * Launch transition between the native splash screen and the first rendered route.
 * Approximates docs/design/mobile_sample_design.png's "SPLASH SCREEN" (ADR-101) —
 * wordmark + tagline + confetti accents in Rally26 brand colors. No photographic
 * stadium-lights background asset exists yet.
 */
export function AnimatedSplashOverlay() {
  const [animate, setAnimate] = useState(false);
  const [visible, setVisible] = useState(true);

  if (!visible) return null;

  const splashKeyframe = new Keyframe({
    0: {
      opacity: 1,
    },
    70: {
      opacity: 0,
      easing: Easing.elastic(0.7),
    },
    100: {
      opacity: 0,
      easing: Easing.elastic(0.7),
    },
  });

  const content = (
    <View style={styles.content} accessibilityElementsHidden importantForAccessibility="no">
      {CONFETTI.map((c, i) => (
        <View
          key={i}
          style={[
            styles.confetti,
            {
              top: c.top,
              left: c.left,
              width: c.size,
              height: c.size,
              transform: [{ rotate: c.rotate }],
              backgroundColor: i % 2 === 0 ? Brand.championshipGold : Brand.infoBlue,
            },
          ]}
        />
      ))}
      <Text style={styles.wordmark}>
        RALLY<Text style={styles.wordmarkAccent}>26</Text>
      </Text>
      <Text style={styles.tagline}>
        UNITE. COMMUNICATE. <Text style={styles.taglineAccent}>WIN TOGETHER.</Text>
      </Text>
    </View>
  );

  return animate ? (
    <Animated.View
      entering={splashKeyframe.duration(DURATION).withCallback((finished) => {
        'worklet';
        if (finished) {
          scheduleOnRN(setVisible, false);
        }
      })}
      style={styles.splashOverlay}>
      {content}
    </Animated.View>
  ) : (
    <View
      onLayout={() => {
        SplashScreen.hideAsync().finally(() => {
          setAnimate(true);
        });
      }}
      style={styles.splashOverlay}>
      {content}
    </View>
  );
}

const styles = StyleSheet.create({
  content: {
    alignItems: 'center',
    justifyContent: 'center',
  },
  confetti: {
    position: 'absolute',
    borderRadius: 2,
  },
  wordmark: {
    color: Brand.pureWhite,
    fontSize: 40,
    fontWeight: '800',
    letterSpacing: 1,
  },
  wordmarkAccent: {
    color: Brand.championshipGold,
  },
  tagline: {
    marginTop: 12,
    color: Brand.pureWhite,
    fontSize: 13,
    fontWeight: '600',
    letterSpacing: 1,
  },
  taglineAccent: {
    color: Brand.championshipGold,
  },
  splashOverlay: {
    ...StyleSheet.absoluteFill,
    backgroundColor: Brand.navy,
    alignItems: 'center',
    justifyContent: 'center',
    zIndex: 1000,
  },
});
