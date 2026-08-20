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
	/** Set only while a downgrade to this plan is scheduled to take effect at the end of the current billing period. */
	downgradeToPlanCode: string | null;
	currentPeriodEnd: string | null;
}

export interface BillingPortalResponse {
	url: string;
}

export interface SubscriptionPlanOption {
	code: string;
	name: string;
	description: string;
	amountMinor: number | null;
	currency: string | null;
	billingInterval: string | null;
}

export type PlanChangeDirection = "UPGRADE" | "DOWNGRADE";

export interface PlanChangeViolation {
	code: string;
	message: string;
	actionLink: string | null;
}

export interface PlanChangePreview {
	currentPlanCode: string;
	targetPlanCode: string;
	direction: PlanChangeDirection;
	violations: PlanChangeViolation[];
}

export type PlanChangeOutcome = "BLOCKED" | "CHECKOUT_REQUIRED" | "APPLIED" | "SCHEDULED_DOWNGRADE";

export interface PlanChangeResult {
	outcome: PlanChangeOutcome;
	violations: PlanChangeViolation[] | null;
	checkoutUrl: string | null;
	effectiveAt: string | null;
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
