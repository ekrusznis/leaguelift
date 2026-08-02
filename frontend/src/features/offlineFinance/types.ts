export type OfflineFinancialRecordType = "CONTRIBUTION" | "SPONSORSHIP" | "ORDER";
export type OfflinePaymentMethod = "CASH" | "CHECK" | "ACH" | "EXTERNAL_CARD" | "VENMO" | "ZELLE" | "OTHER";
export type OfflineVerificationStatus = "PENDING_VERIFICATION" | "VERIFIED";

export interface OfflineFinancialRecord {
	id: string;
	organizationId: string;
	recordType: OfflineFinancialRecordType;
	recordId: string;
	displayLabel: string;
	paymentMethod: OfflinePaymentMethod;
	verificationStatus: OfflineVerificationStatus;
	amountMinor: number;
	currency: string;
	payerName: string | null;
	payerEmail: string | null;
	paymentReference: string | null;
	receivedAt: string;
	internalNotes: string | null;
	sendAcknowledgement: boolean;
	recordedByUserId: string;
	verifiedByUserId: string | null;
	verifiedAt: string | null;
	createdAt: string;
	updatedAt: string;
}

export interface OfflineFinancialRecordPage {
	items: OfflineFinancialRecord[];
	page: number;
	size: number;
	totalElements: number;
}

export interface OfflineOrderLineInput {
	productVariantId: string;
	quantity: number;
}

export interface OfflineShippingAddressInput {
	name?: string | null;
	line1?: string | null;
	line2?: string | null;
	city?: string | null;
	state?: string | null;
	postalCode?: string | null;
	country?: string | null;
}

export interface OfflineCommonInput {
	paymentMethod: OfflinePaymentMethod;
	paymentReference?: string | null;
	receivedAt: string;
	internalNotes?: string | null;
	idempotencyKey: string;
	markVerified: boolean;
	sendAcknowledgement: boolean;
}

export interface CreateOfflineContributionInput extends OfflineCommonInput {
	campaignId: string;
	amountMinor: number;
	supporterName?: string | null;
	isAnonymous: boolean;
	supporterEmail?: string | null;
}

export interface CreateOfflineSponsorshipInput extends OfflineCommonInput {
	packageId: string;
	sponsorName: string;
	sponsorContactEmail?: string | null;
	sponsorPhone?: string | null;
	sponsorCompanyName?: string | null;
}

export interface CreateOfflineOrderInput extends OfflineCommonInput {
	storeId: string;
	items: OfflineOrderLineInput[];
	supporterName?: string | null;
	supporterEmail?: string | null;
	shippingAddress?: OfflineShippingAddressInput | null;
}
