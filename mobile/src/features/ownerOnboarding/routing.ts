import type { OwnerOnboarding } from './types';

const ACCESSIBLE_SUBSCRIPTION_STATUSES = new Set(['ACTIVE', 'TRIALING', 'PAST_DUE']);

/**
 * Owner navigation unlocks only after the signed Stripe subscription webhook has
 * caused the backend to activate the organization and complete onboarding.
 * A browser/WebView Checkout return is intentionally insufficient.
 */
export function isOwnerAccessUnlocked(onboarding: OwnerOnboarding | null | undefined): boolean {
  if (!onboarding?.organization) return false;
  if (onboarding.organization.status !== 'ACTIVE') return false;
  if (onboarding.currentStep !== 'COMPLETE' || !onboarding.completedAt) return false;
  return onboarding.subscriptionStatus != null && ACCESSIBLE_SUBSCRIPTION_STATUSES.has(onboarding.subscriptionStatus);
}

/** Returns the exact persisted step that the web onboarding route should resume. */
export function ownerOnboardingWebPath(onboarding: OwnerOnboarding | null | undefined): string {
  if (!onboarding) return '/app/onboarding';
  switch (onboarding.currentStep) {
    case 'ORGANIZATION':
      return '/app/onboarding/organization';
    case 'PLAN':
      return '/app/onboarding/plan';
    case 'REVIEW':
    case 'CHECKOUT':
      return '/app/onboarding/review';
    case 'COMPLETE':
      return isOwnerAccessUnlocked(onboarding) ? '/app' : '/app/onboarding/review';
  }
}

export type CheckoutSignal = 'success' | 'cancelled' | null;

/**
 * Normalizes both Rally26 onboarding return parameters and the older generic
 * commerce status parameters understood by the shared WebView seam.
 */
export function checkoutSignalFromUrl(url: string): CheckoutSignal {
  try {
    const parsed = new URL(url);
    const checkout = parsed.searchParams.get('checkout');
    if (checkout === 'success') return 'success';
    if (checkout === 'cancelled' || checkout === 'canceled') return 'cancelled';
    const status = parsed.searchParams.get('status');
    if (status === 'success') return 'success';
    if (status === 'cancelled' || status === 'canceled') return 'cancelled';
    return null;
  } catch {
    return null;
  }
}
