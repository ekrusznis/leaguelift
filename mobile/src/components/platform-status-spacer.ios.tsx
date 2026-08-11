import { View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import type { PlatformStatusSpacerProps } from './platform-status-spacer.types';

/**
 * iOS: the safe-area top inset already accounts for the notch/Dynamic Island, so a
 * translucent header only needs that inset with no extra platform padding.
 */
export function PlatformStatusSpacer({ style, ...rest }: PlatformStatusSpacerProps) {
  const insets = useSafeAreaInsets();
  return <View style={[{ height: insets.top }, style]} {...rest} />;
}
