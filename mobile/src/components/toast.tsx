import { createContext, useCallback, useContext, useRef, useState, type ReactNode } from 'react';
import { StyleSheet, View } from 'react-native';
import Animated, { FadeInDown, FadeOutDown } from 'react-native-reanimated';
import { SafeAreaView } from 'react-native-safe-area-context';

import { Brand, Spacing } from '@/constants/theme';

import { ThemedText } from './themed-text';

type ToastVariant = 'success' | 'error' | 'info';
interface ToastItem {
  id: number;
  message: string;
  variant: ToastVariant;
}

const VARIANT_COLOR: Record<ToastVariant, string> = {
  success: Brand.victoryGreen,
  error: Brand.errorRed,
  info: Brand.infoBlue,
};

interface ToastContextValue {
  show: (message: string, variant?: ToastVariant) => void;
}

const ToastContext = createContext<ToastContextValue | null>(null);

const AUTO_DISMISS_MS = 3000;

/** Lightweight app-wide toast — no external dependency, matches the app's own brand accents. */
export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<ToastItem[]>([]);
  const nextId = useRef(0);

  const show = useCallback((message: string, variant: ToastVariant = 'info') => {
    const id = nextId.current++;
    setToasts((prev) => [...prev, { id, message, variant }]);
    setTimeout(() => {
      setToasts((prev) => prev.filter((t) => t.id !== id));
    }, AUTO_DISMISS_MS);
  }, []);

  return (
    <ToastContext.Provider value={{ show }}>
      {children}
      <SafeAreaView pointerEvents="none" style={styles.overlay}>
        {toasts.map((toast) => (
          <Animated.View
            key={toast.id}
            entering={FadeInDown}
            exiting={FadeOutDown}
            style={[styles.toast, { borderLeftColor: VARIANT_COLOR[toast.variant] }]}>
            <View>
              <ThemedText type="small">{toast.message}</ThemedText>
            </View>
          </Animated.View>
        ))}
      </SafeAreaView>
    </ToastContext.Provider>
  );
}

export function useToast(): ToastContextValue {
  const context = useContext(ToastContext);
  if (!context) throw new Error('useToast must be used within a ToastProvider.');
  return context;
}

const styles = StyleSheet.create({
  overlay: {
    position: 'absolute',
    left: 0,
    right: 0,
    bottom: 0,
    alignItems: 'center',
    paddingHorizontal: Spacing.four,
    gap: Spacing.two,
  },
  toast: {
    backgroundColor: '#102B46',
    borderLeftWidth: 4,
    borderRadius: Spacing.two,
    paddingVertical: Spacing.two,
    paddingHorizontal: Spacing.three,
    marginBottom: Spacing.two,
    minWidth: '80%',
  },
});
