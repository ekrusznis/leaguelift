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
	createdAt: string;
	updatedAt: string;
}

export interface FeeAssignmentPage {
	items: FeeAssignment[];
	page: number;
	size: number;
	totalElements: number;
}
