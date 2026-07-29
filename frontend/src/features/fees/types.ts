export interface FeeTemplate {
	id: string;
	organizationId: string;
	name: string;
	description: string | null;
	amountMinor: number;
	currency: string;
	status: "ACTIVE" | "ARCHIVED";
	createdAt: string;
	updatedAt: string;
}

export interface FeeTemplatePage {
	items: FeeTemplate[];
	page: number;
	size: number;
	totalElements: number;
}

export type FeeAssignmentStatus = "OPEN" | "PARTIALLY_PAID" | "PAID" | "WAIVED" | "CANCELLED";

export interface FeeAssignment {
	id: string;
	organizationId: string;
	householdId: string;
	participantId: string | null;
	feeTemplateId: string | null;
	description: string;
	originalAmountMinor: number;
	currency: string;
	dueDate: string | null;
	status: FeeAssignmentStatus;
	paidMinor: number;
	adjustedMinor: number;
	balanceMinor: number;
	createdAt: string;
	updatedAt: string;
}

export interface FeeAssignmentPage {
	items: FeeAssignment[];
	page: number;
	size: number;
	totalElements: number;
}

export type PaymentMethod = "CASH" | "CHECK" | "VENMO" | "ZELLE" | "OTHER";

export interface FeePayment {
	id: string;
	feeAssignmentId: string;
	amountMinor: number;
	currency: string;
	method: PaymentMethod;
	paidAt: string;
	note: string | null;
	recordedByUserId: string;
	voidedAt: string | null;
	voidReason: string | null;
	createdAt: string;
}

export type AdjustmentType = "DISCOUNT" | "CREDIT" | "CORRECTION";

export interface FeeAdjustment {
	id: string;
	feeAssignmentId: string;
	adjustmentType: AdjustmentType;
	amountMinor: number;
	currency: string;
	reason: string | null;
	createdByUserId: string;
	voidedAt: string | null;
	voidReason: string | null;
	createdAt: string;
}
