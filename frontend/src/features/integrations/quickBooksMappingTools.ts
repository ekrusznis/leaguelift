import type { QuickBooksExportPreview, QuickBooksMappingType } from "./types";

export type OwnerQuickBooksMappingType = "PROGRAM_FEE_INCOME" | QuickBooksMappingType;
export type QuickBooksMappingCompatibility = "RECOMMENDED" | "ALLOWED_WITH_WARNING" | "BLOCKED";
export type QuickBooksMappingValidationStatus =
	| "MISSING"
	| "VALID"
	| "VALID_WITH_WARNING"
	| "NEEDS_REVIEW"
	| "INACTIVE"
	| "ACCOUNT_NOT_FOUND"
	| "INCOMPATIBLE";

export interface OwnerQuickBooksAccount {
	id: string;
	name: string;
	fullyQualifiedName: string | null;
	accountType: string;
	accountSubType: string | null;
	classification: string | null;
	active: boolean;
}

export interface OwnerQuickBooksAccountMapping {
	id: string;
	mappingType: OwnerQuickBooksMappingType;
	externalAccountId: string;
	externalAccountName: string;
	externalAccountFullyQualifiedName: string | null;
	externalAccountType: string | null;
	externalAccountSubType: string | null;
	compatibilityAtSelection: QuickBooksMappingCompatibility;
	warningAcknowledged: boolean;
	updatedAt: string;
}

export interface QuickBooksMappingDefinition {
	mappingType: OwnerQuickBooksMappingType;
	label: string;
	description: string;
	recommendedAccountTypes: string[];
	warningAccountTypes: string[];
}

export interface OwnerQuickBooksMappingValidation {
	mappingType: OwnerQuickBooksMappingType;
	mapping: OwnerQuickBooksAccountMapping | null;
	currentAccount: OwnerQuickBooksAccount | null;
	status: QuickBooksMappingValidationStatus;
	message: string;
}

export type OwnerQuickBooksExportPreview = Omit<QuickBooksExportPreview, "missingMappings"> & {
	missingMappings: OwnerQuickBooksMappingType[];
	mappingDiagnostics: OwnerQuickBooksMappingValidation[];
};

export function classifyQuickBooksAccount(
	account: OwnerQuickBooksAccount,
	definition: QuickBooksMappingDefinition,
): QuickBooksMappingCompatibility {
	if (!account.active) return "BLOCKED";
	if (definition.recommendedAccountTypes.includes(account.accountType)) return "RECOMMENDED";
	if (definition.warningAccountTypes.includes(account.accountType)) return "ALLOWED_WITH_WARNING";
	return "BLOCKED";
}

export function compatibilityLabel(compatibility: QuickBooksMappingCompatibility): string {
	switch (compatibility) {
		case "RECOMMENDED":
			return "Recommended";
		case "ALLOWED_WITH_WARNING":
			return "Review required";
		case "BLOCKED":
			return "Unavailable";
	}
}

export function validationLabel(status: QuickBooksMappingValidationStatus): string {
	switch (status) {
		case "VALID":
			return "Valid";
		case "VALID_WITH_WARNING":
			return "Valid with review";
		case "MISSING":
			return "Missing";
		case "NEEDS_REVIEW":
			return "Needs review";
		case "INACTIVE":
			return "Inactive";
		case "ACCOUNT_NOT_FOUND":
			return "Account not found";
		case "INCOMPATIBLE":
			return "Incompatible";
	}
}
