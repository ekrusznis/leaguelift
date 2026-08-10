import { StatusBar, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import type { PlatformStatusSpacerProps } from './platform-status-spacer.types';

/**
 * Android: edge-to-edge status bar height isn't always fully reflected in the safe-area
 * top inset the way it is on iOS, so this falls back to StatusBar.currentHeight when the
 * inset comes back smaller than the actual status bar (seen on some OEM skins/API levels).
 */
export function PlatformStatusSpacer({ style, ...rest }: PlatformStatusSpacerProps) {
  const insets = useSafeAreaInsets();
  const height = Math.max(insets.top, StatusBar.currentHeight ?? 0);
  return <View style={[{ height }, style]} {...rest} />;
}
