export type DisputeSourceType = "CONTRIBUTION" | "ORDER" | "SPONSORSHIP" | "FEE_PAYMENT";

export type DisputeStatus = "NEEDS_RESPONSE" | "UNDER_REVIEW" | "WON" | "LOST";

export interface Dispute {
	id: string;
	sourceType: DisputeSourceType;
	sourceId: string;
	amountMinor: number;
	currency: string;
	reason: string;
	status: DisputeStatus;
	evidenceDueBy: string | null;
	openedAt: string;
	resolvedAt: string | null;
}

export interface DisputePage {
	items: Dispute[];
	page: number;
	size: number;
	totalElements: number;
}
