export type OrganizationSubscriptionStatus =
	| "CHECKOUT_PENDING"
	| "TRIALING"
	| "ACTIVE"
	| "PAST_DUE"
	| "CANCELED"
	| "INCOMPLETE";

export type BillingRecoveryState = "CHECKOUT_REQUIRED" | "CURRENT" | "PAYMENT_ACTION_REQUIRED" | "ENDED";

export interface OrganizationSubscription {
	id: string;
	organizationId: string;
	planCode: string;
	planName: string | null;
	amountMinor: number | null;
	currency: string | null;
	billingInterval: string | null;
	status: OrganizationSubscriptionStatus;
	recoveryState: BillingRecoveryState;
	lastPaymentFailureAt: string | null;
	lastPaymentSuccessAt: string | null;
	billingPortalAvailable: boolean;
	cancelAtPeriodEnd: boolean;
}

export interface BillingPortalResponse {
	url: string;
}

export interface PlatformOrganizationSubscription {
	organizationId: string;
	organizationName: string;
	organizationStatus: string;
	planCode: string | null;
	planName: string | null;
	amountMinor: number | null;
	currency: string | null;
	status: OrganizationSubscriptionStatus | null;
	recoveryState: BillingRecoveryState | "NOT_STARTED";
	lastPaymentFailureAt: string | null;
	lastPaymentSuccessAt: string | null;
	hasStripeCustomer: boolean;
	hasStripeSubscription: boolean;
	cancelAtPeriodEnd: boolean;
	updatedAt: string;
}

export interface PageResponse<T> {
	items: T[];
	page: number;
	size: number;
	totalElements: number;
}
