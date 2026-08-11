/**
 * Rally26 brand tokens, mirrored from frontend/src/styles/tokens.css so the native
 * app reads the same brand palette as the web app rather than inventing a second one.
 * Light/Dark here follow the device's System appearance; System/Light/Dark as an
 * explicit account preference (Phase 28) is wired in at the navigation-shell level.
 */

import { Platform } from 'react-native';

export const Brand = {
  navy: '#0B1F33',
  victoryGreen: '#20B26B',
  championshipGold: '#F2600C',
  iceWhite: '#F7F9FC',
  slateGray: '#526275',
  pureWhite: '#FFFFFF',
  errorRed: '#C93636',
  infoBlue: '#2F6FED',
} as const;

export const Colors = {
  light: {
    text: Brand.navy,
    background: Brand.pureWhite,
    backgroundElement: Brand.iceWhite,
    backgroundSelected: '#E0E1E6',
    textSecondary: Brand.slateGray,
  },
  dark: {
    text: Brand.pureWhite,
    background: '#061321',
    backgroundElement: '#102B46',
    backgroundSelected: '#173B5C',
    textSecondary: '#C9D2DD',
  },
} as const;

export type ThemeColor = keyof typeof Colors.light & keyof typeof Colors.dark;

export const Fonts = Platform.select({
  ios: {
    /** iOS `UIFontDescriptorSystemDesignDefault` */
    sans: 'system-ui',
    /** iOS `UIFontDescriptorSystemDesignSerif` */
    serif: 'ui-serif',
    /** iOS `UIFontDescriptorSystemDesignRounded` */
    rounded: 'ui-rounded',
    /** iOS `UIFontDescriptorSystemDesignMonospaced` */
    mono: 'ui-monospace',
  },
  default: {
    sans: 'normal',
    serif: 'serif',
    rounded: 'normal',
    mono: 'monospace',
  },
});

export const Spacing = {
  half: 2,
  one: 4,
  two: 8,
  three: 16,
  four: 24,
  five: 32,
  six: 64,
} as const;

export const BottomTabInset = Platform.select({ ios: 50, android: 80 }) ?? 0;
export const MaxContentWidth = 800;
