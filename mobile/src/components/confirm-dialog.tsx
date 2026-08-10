import { Modal as RNModal, Pressable, StyleSheet, View } from 'react-native';

import { Spacing } from '@/constants/theme';

import { Button } from './button';
import { ThemedText } from './themed-text';
import { ThemedView } from './themed-view';

/** Centered confirm/destructive-action dialog — logout, archive, delete, etc. */
export function ConfirmDialog({
  visible,
  title,
  message,
  confirmLabel = 'Confirm',
  cancelLabel = 'Cancel',
  destructive,
  onConfirm,
  onCancel,
}: {
  visible: boolean;
  title: string;
  message?: string;
  confirmLabel?: string;
  cancelLabel?: string;
  destructive?: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}) {
  return (
    <RNModal visible={visible} transparent animationType="fade" onRequestClose={onCancel}>
      <Pressable style={styles.backdrop} onPress={onCancel}>
        <Pressable onPress={(e) => e.stopPropagation()} style={styles.cardWrap}>
          <ThemedView type="backgroundElement" style={styles.card}>
            <ThemedText type="smallBold">{title}</ThemedText>
            {message && (
              <ThemedText type="small" themeColor="textSecondary">
                {message}
              </ThemedText>
            )}
            <View style={styles.actions}>
              <Button variant="secondary" onPress={onCancel} style={styles.actionButton}>
                {cancelLabel}
              </Button>
              <Button
                variant={destructive ? 'secondary' : 'primary'}
                onPress={onConfirm}
                style={[styles.actionButton, destructive && styles.destructiveButton]}>
                {confirmLabel}
              </Button>
            </View>
          </ThemedView>
        </Pressable>
      </Pressable>
    </RNModal>
  );
}

const styles = StyleSheet.create({
  backdrop: {
    flex: 1,
    backgroundColor: 'rgba(6,19,33,0.6)',
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: Spacing.five,
  },
  cardWrap: {
    width: '100%',
  },
  card: {
    borderRadius: Spacing.three,
    padding: Spacing.four,
    gap: Spacing.three,
  },
  actions: {
    flexDirection: 'row',
    gap: Spacing.three,
    marginTop: Spacing.two,
  },
  actionButton: {
    flex: 1,
  },
  destructiveButton: {
    borderColor: '#C93636',
  },
});
