export interface PayoutAccount {
	stripeAccountId: string;
	detailsSubmitted: boolean;
	chargesEnabled: boolean;
	payoutsEnabled: boolean;
	isFullyConnected: boolean;
	updatedAt: string;
}

export interface OnboardingLinkResponse {
	onboardingUrl: string;
}

/** netAvailableMinor can be negative — a prior refund can exceed what's currently eligible (ADR-017). */
export interface PayoutSummary {
	eligibleMinor: number;
	heldMinor: number;
	pendingDebitsMinor: number;
	netAvailableMinor: number;
}
