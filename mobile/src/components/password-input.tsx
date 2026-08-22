import Ionicons from '@expo/vector-icons/Ionicons';
import { useState } from 'react';
import { Pressable, StyleSheet, TextInput, View, type TextInputProps } from 'react-native';

import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

/** TextInput with a show/hide toggle — same field wherever a password is entered (BUG-006). */
export function PasswordInput({ style, ...props }: Omit<TextInputProps, 'secureTextEntry'>) {
  const theme = useTheme();
  const [visible, setVisible] = useState(false);

  return (
    <View style={styles.wrapper}>
      <TextInput
        secureTextEntry={!visible}
        placeholderTextColor={theme.textSecondary}
        style={[styles.input, { color: theme.text, backgroundColor: theme.backgroundElement }, style]}
        {...props}
      />
      <Pressable
        onPress={() => setVisible((v) => !v)}
        hitSlop={8}
        style={styles.toggle}
        accessibilityRole="button"
        accessibilityLabel={visible ? 'Hide password' : 'Show password'}>
        <Ionicons name={visible ? 'eye-off' : 'eye'} size={20} color={theme.textSecondary} />
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  wrapper: {
    justifyContent: 'center',
  },
  input: {
    minHeight: 48,
    borderRadius: Spacing.two,
    paddingHorizontal: Spacing.three,
    paddingRight: 44,
  },
  toggle: {
    position: 'absolute',
    right: Spacing.three,
    height: 44,
    width: 32,
    alignItems: 'center',
    justifyContent: 'center',
  },
});
