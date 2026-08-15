export type OwnerOnboardingStep = 'ORGANIZATION' | 'PLAN' | 'REVIEW' | 'CHECKOUT' | 'COMPLETE';
export type OrganizationStatus = 'DRAFT' | 'ACTIVE' | 'SUSPENDED' | 'ARCHIVED';
export type OrganizationSubscriptionStatus = 'CHECKOUT_PENDING' | 'TRIALING' | 'ACTIVE' | 'PAST_DUE' | 'CANCELED' | 'INCOMPLETE' | null;

export interface OwnerOnboardingOrganization {
  id: string;
  name: string;
  slug: string;
  organizationType: string;
  status: OrganizationStatus;
  sports: string[];
  contactEmail: string | null;
  contactPhone: string | null;
  addressLine1: string | null;
  addressLine2: string | null;
  addressCity: string | null;
  addressState: string | null;
  addressPostalCode: string | null;
  addressCountry: string | null;
  timezone: string | null;
}

export interface OwnerOnboarding {
  id: string;
  currentStep: OwnerOnboardingStep;
  organization: OwnerOnboardingOrganization | null;
  selectedPlanCode: string | null;
  selectedBillingFrequency: string | null;
  checkoutSessionId: string | null;
  subscriptionStatus: OrganizationSubscriptionStatus;
  completedAt: string | null;
}
