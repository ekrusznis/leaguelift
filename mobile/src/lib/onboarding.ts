import AsyncStorage from '@react-native-async-storage/async-storage';
import { createContext, useContext, useEffect, useState } from 'react';

const STORAGE_KEY = 'rally26.hasSeenOnboarding';

export type OnboardingStatus = 'loading' | 'seen' | 'unseen';

interface OnboardingContextValue {
  status: OnboardingStatus;
  markSeen: () => Promise<void>;
}

export const OnboardingContext = createContext<OnboardingContextValue | null>(null);

/**
 * First-launch-only product walkthrough gate (docs/design/mobile_sample_design.png,
 * ADR-101). A Context, not a bare hook — RootNavigator's gate and onboarding.tsx's
 * "Get Started"/"Skip" need to observe and update the SAME status, not two
 * independent AsyncStorage reads that can't see each other's writes.
 */
export function useOnboardingState(): OnboardingContextValue {
  const [status, setStatus] = useState<OnboardingStatus>('loading');

  useEffect(() => {
    let cancelled = false;
    AsyncStorage.getItem(STORAGE_KEY)
      .then((value) => {
        if (!cancelled) setStatus(value === 'true' ? 'seen' : 'unseen');
      })
      .catch(() => {
        if (!cancelled) setStatus('unseen');
      });
    return () => {
      cancelled = true;
    };
  }, []);

  return {
    status,
    markSeen: async () => {
      await AsyncStorage.setItem(STORAGE_KEY, 'true');
      setStatus('seen');
    },
  };
}

export function useOnboarding(): OnboardingContextValue {
  const context = useContext(OnboardingContext);
  if (!context) throw new Error('useOnboarding must be used within the root layout.');
  return context;
}
