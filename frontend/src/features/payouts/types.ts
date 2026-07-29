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
