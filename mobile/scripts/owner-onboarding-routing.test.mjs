import test from 'node:test';
import assert from 'node:assert/strict';

import {
  checkoutSignalFromUrl,
  isOwnerAccessUnlocked,
  ownerOnboardingWebPath,
} from '../src/features/ownerOnboarding/routing.ts';

function onboarding(overrides = {}) {
  return {
    id: '11111111-1111-1111-1111-111111111111',
    currentStep: 'ORGANIZATION',
    organization: null,
    selectedPlanCode: null,
    selectedBillingFrequency: null,
    checkoutSessionId: null,
    subscriptionStatus: null,
    completedAt: null,
    ...overrides,
  };
}

function draftOrganization(overrides = {}) {
  return {
    id: '22222222-2222-2222-2222-222222222222',
    name: 'Rally Juniors',
    slug: 'rally-juniors',
    organizationType: 'TRAVEL_CLUB',
    status: 'DRAFT',
    sports: ['Volleyball'],
    contactEmail: 'owner@example.com',
    contactPhone: null,
    addressLine1: null,
    addressLine2: null,
    addressCity: null,
    addressState: null,
    addressPostalCode: null,
    addressCountry: 'US',
    timezone: 'America/New_York',
    ...overrides,
  };
}

test('close after Organization then reopen resumes the Plan step', () => {
  const state = onboarding({ currentStep: 'PLAN', organization: draftOrganization() });
  assert.equal(ownerOnboardingWebPath(state), '/app/onboarding/plan');
  assert.equal(isOwnerAccessUnlocked(state), false);
});

test('close after plan selection then reopen resumes Review and Checkout', () => {
  const state = onboarding({
    currentStep: 'REVIEW',
    organization: draftOrganization(),
    selectedPlanCode: 'FOUNDING_CLUB',
    selectedBillingFrequency: 'MONTHLY',
  });
  assert.equal(ownerOnboardingWebPath(state), '/app/onboarding/review');
  assert.equal(isOwnerAccessUnlocked(state), false);
});

test('checkout cancelled returns to Review and remains gated', () => {
  const state = onboarding({
    currentStep: 'REVIEW',
    organization: draftOrganization(),
    selectedPlanCode: 'STARTER',
  });
  assert.equal(checkoutSignalFromUrl('https://rally26.com/app/onboarding/review?checkout=cancelled'), 'cancelled');
  assert.equal(ownerOnboardingWebPath(state), '/app/onboarding/review');
  assert.equal(isOwnerAccessUnlocked(state), false);
});

test('checkout success while webhook is pending never unlocks Owner', () => {
  const state = onboarding({
    currentStep: 'CHECKOUT',
    organization: draftOrganization(),
    selectedPlanCode: 'STARTER',
    checkoutSessionId: 'cs_test_123',
    subscriptionStatus: 'CHECKOUT_PENDING',
  });
  assert.equal(checkoutSignalFromUrl('https://rally26.com/app/onboarding/review?checkout=success'), 'success');
  assert.equal(ownerOnboardingWebPath(state), '/app/onboarding/review');
  assert.equal(isOwnerAccessUnlocked(state), false);
});

test('signed webhook ACTIVE state unlocks the native Owner dashboard', () => {
  const state = onboarding({
    currentStep: 'COMPLETE',
    organization: draftOrganization({ status: 'ACTIVE' }),
    selectedPlanCode: 'STARTER',
    subscriptionStatus: 'ACTIVE',
    completedAt: new Date().toISOString(),
  });
  assert.equal(isOwnerAccessUnlocked(state), true);
  assert.equal(ownerOnboardingWebPath(state), '/app');
});

test('a DRAFT organization can never reach Owner functionality', () => {
  const state = onboarding({
    currentStep: 'COMPLETE',
    organization: draftOrganization({ status: 'DRAFT' }),
    selectedPlanCode: 'FOUNDING_CLUB',
    subscriptionStatus: 'ACTIVE',
    completedAt: new Date().toISOString(),
  });
  assert.equal(isOwnerAccessUnlocked(state), false);
  assert.equal(ownerOnboardingWebPath(state), '/app/onboarding/review');
});
