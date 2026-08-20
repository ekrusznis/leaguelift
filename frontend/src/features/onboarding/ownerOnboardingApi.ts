import { apiFetch } from "../../lib/apiClient";

export interface OnboardingOrganization {
	id: string;
	name: string;
	slug: string;
	organizationType: string;
	status: string;
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
	currentStep: string;
	organization: OnboardingOrganization | null;
	selectedPlanCode: string | null;
	selectedBillingFrequency: string | null;
	checkoutSessionId: string | null;
	subscriptionStatus: string | null;
	completedAt: string | null;
}

export interface SubscriptionPlan {
	code: string;
	name: string;
	description: string;
	amountMinor: number | null;
	currency: string | null;
	billingInterval: string | null;
	contactOnly: boolean;
	requiresCheckout: boolean;
}

export interface SaveOrganizationInput {
	name: string;
	slug: string;
	organizationType: string;
	sports: string[];
	contactEmail: string;
	contactPhone: string;
	addressLine1: string;
	addressLine2: string;
	addressCity: string;
	addressState: string;
	addressPostalCode: string;
	addressCountry: string;
	timezone: string;
}

export function getOwnerOnboarding() {
	return apiFetch<OwnerOnboarding>("/owner-onboarding");
}

export function listOnboardingPlans() {
	return apiFetch<SubscriptionPlan[]>("/owner-onboarding/plans");
}

export function suggestOnboardingTimezone(country: string, state: string) {
	const params = new URLSearchParams();
	if (country) params.set("country", country);
	if (state) params.set("state", state);
	return apiFetch<{ timezone: string | null }>(`/owner-onboarding/timezone-suggestion?${params.toString()}`);
}

export function saveOnboardingOrganization(input: SaveOrganizationInput) {
	return apiFetch<OwnerOnboarding>("/owner-onboarding/organization", {
		method: "PUT",
		body: input,
	});
}

export function selectOnboardingPlan(planCode: string, billingFrequency = "MONTHLY") {
	return apiFetch<OwnerOnboarding>("/owner-onboarding/plan", {
		method: "PUT",
		body: { planCode, billingFrequency },
	});
}

export function startSubscriptionCheckout() {
	return apiFetch<{ sessionId: string; checkoutUrl: string }>("/owner-onboarding/checkout", {
		method: "POST",
	});
}

/** FREE-tier bypass of {@link selectOnboardingPlan}/{@link startSubscriptionCheckout} — no Stripe Checkout to wait on. */
export function activateFreeOnboardingPlan() {
	return apiFetch<OwnerOnboarding>("/owner-onboarding/free-activate", {
		method: "POST",
	});
}

export function createBillingPortal(organizationId: string) {
	return apiFetch<{ url: string }>(`/organizations/${organizationId}/subscription/portal`, {
		method: "POST",
	});
}
