import type { ExpoConfig } from 'expo/config';

/**
 * app.config.ts instead of app.json so the EAS project ID, bundle identifiers, and
 * (if ever needed) build-time-only config can be computed rather than hand-duplicated
 * across environments. Per-build-profile runtime env vars (API base URL, environment
 * name) are set separately in eas.json's "env" block per DESIGN-DOC.md §14.1N's
 * "environment separation for local/development/preview/production mobile builds."
 */
const config: ExpoConfig = {
  name: 'Rally26',
  slug: 'rally',
  version: '1.0.0',
  orientation: 'default',
  icon: './assets/images/icon.png',
  scheme: 'rally26',
  userInterfaceStyle: 'automatic',
  ios: {
    bundleIdentifier: 'com.rally26.mobile',
    icon: './assets/expo.icon',
    supportsTablet: true,
  },
  android: {
    package: 'com.rally26.mobile',
    adaptiveIcon: {
      backgroundColor: '#0B1F33',
      foregroundImage: './assets/images/android-icon-foreground.png',
      backgroundImage: './assets/images/android-icon-background.png',
      monochromeImage: './assets/images/android-icon-monochrome.png',
    },
    predictiveBackGestureEnabled: false,
  },
  plugins: [
    'expo-router',
    'expo-secure-store',
    '@react-native-community/datetimepicker',
    [
      'expo-splash-screen',
      {
        backgroundColor: '#0B1F33',
        image: './assets/images/splash-icon.png',
        imageWidth: 76,
      },
    ],
  ],
  experiments: {
    typedRoutes: true,
    reactCompiler: true,
  },
  extra: {
    eas: {
      projectId: '94588650-4ee1-4798-a023-8aab2bf1d7f5',
    },
  },
};

export default config;
