export type OnboardingRecordType = "TEAM" | "HOUSEHOLD" | "GUARDIAN" | "PARTICIPANT";
export type OnboardingPreviewAction = "CREATE" | "UPDATE" | "MATCH_EXISTING" | "ERROR";

export interface OnboardingPreviewRow {
	rowNumber: number;
	recordType: OnboardingRecordType | null;
	externalId: string | null;
	label: string;
	action: OnboardingPreviewAction;
	warnings: string[];
	errors: string[];
}

export interface OnboardingImportPreview {
	fileName: string | null;
	contentHash: string;
	totalRows: number;
	validRows: number;
	errorRows: number;
	createCount: number;
	updateCount: number;
	matchCount: number;
	rows: OnboardingPreviewRow[];
}

export interface OnboardingImportRowError {
	rowNumber: number;
	recordType: OnboardingRecordType | null;
	externalId: string | null;
	message: string;
}

export interface ImportedOnboardingEntity {
	recordType: OnboardingRecordType;
	externalId: string;
	entityId: string;
	label: string;
	action: OnboardingPreviewAction;
}

export interface OnboardingImportResult {
	fileName: string | null;
	contentHash: string;
	createdCount: number;
	updatedCount: number;
	matchedCount: number;
	teamAssignmentsCreated: number;
	errors: OnboardingImportRowError[];
	entities: ImportedOnboardingEntity[];
}

export type BulkActionStatus = "SUCCEEDED" | "SKIPPED" | "FAILED";

export interface BulkActionItem {
	reference: string;
	status: BulkActionStatus;
	entityId: string | null;
	message: string | null;
}

export interface BulkActionResult {
	requestedCount: number;
	succeededCount: number;
	skippedCount: number;
	failedCount: number;
	items: BulkActionItem[];
}

export interface OrganizationParticipant {
	id: string;
	householdId: string;
	organizationId: string;
	firstName: string;
	lastName: string;
	dateOfBirth: string | null;
	notes: string | null;
	status: "ACTIVE" | "INACTIVE" | "ARCHIVED";
	createdAt: string;
	updatedAt: string;
}

export interface OrganizationParticipantPage {
	items: OrganizationParticipant[];
	page: number;
	size: number;
	totalElements: number;
}
