import type { ReactNode } from 'react';
import { Pressable, StyleSheet, type StyleProp, type ViewStyle } from 'react-native';

import { Brand, Spacing } from '@/constants/theme';

import { ThemedText } from './themed-text';

type Variant = 'primary' | 'secondary';

/** Matches the orange-primary / outlined-secondary buttons throughout docs/design/mobile_sample_design.png (ADR-101). */
export function Button({
  children,
  onPress,
  variant = 'primary',
  disabled,
  style,
}: {
  children: ReactNode;
  onPress: () => void;
  variant?: Variant;
  disabled?: boolean;
  style?: StyleProp<ViewStyle>;
}) {
  return (
    <Pressable
      onPress={onPress}
      disabled={disabled}
      style={({ pressed }) => [
        styles.base,
        variant === 'primary' ? styles.primary : styles.secondary,
        disabled && styles.disabled,
        pressed && !disabled && styles.pressed,
        style,
      ]}>
      <ThemedText type="smallBold" style={variant === 'primary' ? styles.primaryText : undefined}>
        {children}
      </ThemedText>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  base: {
    minHeight: 44,
    borderRadius: Spacing.two,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: Spacing.four,
  },
  primary: {
    backgroundColor: Brand.championshipGold,
  },
  primaryText: {
    color: '#0B1F33',
  },
  secondary: {
    backgroundColor: 'transparent',
    borderWidth: 1,
    borderColor: Brand.slateGray,
  },
  disabled: {
    opacity: 0.5,
  },
  pressed: {
    opacity: 0.85,
  },
});
