export type FinancialCorrectionTargetType = "CONTRIBUTION" | "SPONSORSHIP" | "ORDER" | "OFFLINE_FINANCIAL_RECORD";

export interface FinancialCorrectionPreview {
	correctionType: "REFUND" | "REVERSAL";
	targetType: FinancialCorrectionTargetType;
	targetId: string;
	targetLabel: string;
	paymentSource: string;
	originalAmountMinor: number;
	previouslyCorrectedMinor: number;
	requestedAmountMinor: number;
	remainingAfterMinor: number;
	currency: string;
	willFullyCorrect: boolean;
	warnings: string[];
	confirmationHash: string;
}

export interface FinancialCorrection {
	id: string;
	organizationId: string;
	correctionType: "REFUND" | "REVERSAL";
	targetType: FinancialCorrectionTargetType;
	targetId: string;
	amountMinor: number;
	currency: string;
	reason: string;
	providerReference: string | null;
	createdByUserId: string;
	createdAt: string;
}

export interface FinancialCorrectionPage {
	items: FinancialCorrection[];
	page: number;
	size: number;
	totalElements: number;
}
