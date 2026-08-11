import * as SplashScreen from 'expo-splash-screen';
import { useState } from 'react';
import { ImageBackground, StyleSheet } from 'react-native';
import Animated, { Easing, Keyframe } from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

const DURATION = 400;

/**
 * Launch transition between the native splash screen and the first rendered route.
 * Real brand art (founder-supplied docs/design/splash1.png, ADR-107) — stadium
 * floodlights/confetti background with the RALLY26 wordmark and tagline baked into
 * the image itself, replacing the earlier Ionicons/color-block approximation from
 * ADR-101 (no photographic asset existed yet at that point).
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
    <ImageBackground
      source={require('../../assets/images/splash1.png')}
      resizeMode="cover"
      style={styles.image}
      accessibilityElementsHidden
      importantForAccessibility="no-hide-descendants"
    />
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
    <ImageBackground
      source={require('../../assets/images/splash1.png')}
      resizeMode="cover"
      style={styles.splashOverlay}
      onLayout={() => {
        SplashScreen.hideAsync().finally(() => {
          setAnimate(true);
        });
      }}
    />
  );
}

const styles = StyleSheet.create({
  image: {
    ...StyleSheet.absoluteFill,
  },
  splashOverlay: {
    ...StyleSheet.absoluteFill,
    zIndex: 1000,
  },
});
