export interface SourceTypeRevenue {
	sourceType: string;
	amountMinor: number;
}

export interface TeamRevenue {
	teamId: string | null;
	teamName: string | null;
	amountMinor: number;
}

export interface RevenueReport {
	from: string;
	to: string;
	totalMinor: number;
	bySourceType: SourceTypeRevenue[];
	byTeam: TeamRevenue[];
}

export interface CampaignRevenue {
	campaignId: string;
	campaignName: string;
	amountMinor: number;
}

export interface ProductPerformance {
	productId: string;
	productName: string;
	quantitySold: number;
	revenueMinor: number;
}

export interface RefundItem {
	sourceType: string;
	sourceId: string;
	amountMinor: number;
	effectiveAt: string;
}

export interface RefundsReport {
	from: string;
	to: string;
	count: number;
	totalMinor: number;
	refunds: RefundItem[];
}

export interface FeeCollection {
	feePaymentId: string;
	householdId: string;
	householdName: string;
	amountMinor: number;
	paidAt: string;
}

export interface FeeCollectionsReport {
	from: string;
	to: string;
	collectedMinor: number;
	outstandingMinor: number;
	payments: FeeCollection[];
}

export interface PlatformReport {
	from: string;
	to: string;
	newOrganizations: number;
	activeOrganizations: number;
	newCustomers: number;
	grossTransactionVolumeMinor: number;
	refundedMinor: number;
	refundRatePercent: number | null;
	webhookProcessed: number;
	webhookFailed: number;
	outboxPending: number;
	outboxDeadLetter: number;
}
