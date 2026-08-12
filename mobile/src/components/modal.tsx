import type { ReactNode } from 'react';
import { KeyboardAvoidingView, Modal as RNModal, Platform, Pressable, StyleSheet } from 'react-native';

import { Spacing } from '@/constants/theme';

import { ThemedView } from './themed-view';

/**
 * Bottom-sheet-style modal — RN's built-in Modal, no extra gesture-library dependency
 * at this scaffold stage. RN's Modal renders in its own native window/layer, so
 * KeyboardAvoidingView must live inside it (wrapping from outside the RNModal has no
 * effect) — any caller passing a TextInput as a child now gets the keyboard pushed
 * above the field automatically instead of covering it (Phase 37 slice, founder report).
 */
export function Modal({ visible, onClose, children }: { visible: boolean; onClose: () => void; children: ReactNode }) {
  return (
    <RNModal visible={visible} transparent animationType="slide" onRequestClose={onClose}>
      <KeyboardAvoidingView style={styles.sheetBackdrop} behavior={Platform.OS === 'ios' ? 'padding' : 'height'}>
        <Pressable style={styles.sheetBackdrop} onPress={onClose}>
          <Pressable onPress={(e) => e.stopPropagation()}>
            <ThemedView type="backgroundElement" style={styles.sheet}>
              {children}
            </ThemedView>
          </Pressable>
        </Pressable>
      </KeyboardAvoidingView>
    </RNModal>
  );
}

const styles = StyleSheet.create({
  sheetBackdrop: {
    flex: 1,
    backgroundColor: 'rgba(6,19,33,0.6)',
    justifyContent: 'flex-end',
  },
  sheet: {
    borderTopLeftRadius: Spacing.four,
    borderTopRightRadius: Spacing.four,
    padding: Spacing.four,
    paddingBottom: Spacing.six,
  },
});
