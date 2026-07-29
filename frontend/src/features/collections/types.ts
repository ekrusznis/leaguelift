import type { FeeAssignmentStatus } from "../fees/types";

export interface FeeAssignmentSummary {
	id: string;
	organizationId: string;
	householdId: string;
	householdName: string;
	participantId: string | null;
	participantName: string | null;
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

export interface FeeAssignmentSummaryPage {
	items: FeeAssignmentSummary[];
	page: number;
	size: number;
	totalElements: number;
}
