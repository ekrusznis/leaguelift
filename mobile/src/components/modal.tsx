import type { ReactNode } from 'react';
import { Modal as RNModal, Pressable, StyleSheet } from 'react-native';

import { Spacing } from '@/constants/theme';

import { ThemedView } from './themed-view';

/** Bottom-sheet-style modal — RN's built-in Modal, no extra gesture-library dependency at this scaffold stage. */
export function Modal({ visible, onClose, children }: { visible: boolean; onClose: () => void; children: ReactNode }) {
  return (
    <RNModal visible={visible} transparent animationType="slide" onRequestClose={onClose}>
      <Pressable style={styles.sheetBackdrop} onPress={onClose}>
        <Pressable onPress={(e) => e.stopPropagation()}>
          <ThemedView type="backgroundElement" style={styles.sheet}>
            {children}
          </ThemedView>
        </Pressable>
      </Pressable>
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
