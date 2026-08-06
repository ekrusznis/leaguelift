export interface FamilyCreditBalance {
	currency: string;
	availableMinor: number;
	pendingMinor: number;
	expiringSoonMinor: number;
	appliedAllTimeMinor: number;
	p2pTransferEnabled: boolean;
}

export interface FamilyCreditGrant {
	id: string;
	amountMinor: number;
	remainingMinor: number;
	currency: string;
	status: "PENDING" | "AVAILABLE" | "EXPIRED" | "REVOKED";
	sourceType: "CAMPAIGN_ATTRIBUTION" | "ORG_PROMO" | "MANUAL" | "P2P_TRANSFER";
	grantedAt: string;
	expiresAt: string | null;
}

export interface FamilyCreditTransfer {
	id: string;
	fromHouseholdId: string;
	toHouseholdId: string;
	amountMinor: number;
	status: "PENDING" | "APPROVED" | "REJECTED";
	reviewNote: string | null;
	createdAt: string;
	reviewedAt: string | null;
}

export interface AttributionLink {
	code: string;
	publicDisplayName: string | null;
}

export interface OrganizationCreditSettings {
	defaultCreditPercent: number;
	expirationPolicy: "ROLLOVER" | "EXPIRES";
	expirationMonths: number | null;
	p2pTransferEnabled: boolean;
}

export interface OrganizationMarkupRule {
	id: string;
	printifyBlueprintId: number | null;
	markupType: "PERCENTAGE" | "FLAT";
	markupValue: number;
	updatedAt: string;
}
