export type RequirementMode =
	| "GUARDIAN_ESIGN_ACKNOWLEDGMENT"
	| "GUARDIAN_LEGAL_NAME_SIGNATURE"
	| "FILE_UPLOAD"
	| "STAFF_REVIEWED_EXTERNAL"
	| "INFORMATIONAL";

export type ExpirationRule = "NEVER" | "SEASON_END" | "FIXED_DAYS_FROM_ACCEPTANCE" | "ORG_CONFIGURED";
export type RequirementStatus = "ACTIVE" | "ARCHIVED";
export type EvidenceClassification =
	| "VERIFIED_EXTERNAL_DOCUMENT"
	| "EXTERNAL_ACKNOWLEDGMENT"
	| "EXTERNAL_STATUS_ONLY"
	| "MANUALLY_VERIFIED"
	| "MISSING"
	| "EXPIRED"
	| "REJECTED"
	| "REVOKED";
export type AcceptanceMethod = "ESIGN_LEGAL_NAME" | "ACKNOWLEDGMENT_CHECKBOX" | "FILE_UPLOAD" | "EXTERNAL_IMPORT" | "STAFF_MANUAL_VERIFICATION";
export type EvidenceStatus = "ACTIVE" | "SUPERSEDED" | "REVOKED" | "REJECTED" | "EXPIRED";

export interface EligibilityRequirement {
	id: string;
	organizationId: string;
	requirementGroupId: string;
	version: number;
	title: string;
	mode: RequirementMode;
	content: string;
	contentHash: string;
	sport: string | null;
	program: string | null;
	season: string | null;
	teamId: string | null;
	guardianActionMode: "ANY_ONE_GUARDIAN" | "ALL_GUARDIANS";
	athleteSelfSignAllowed: boolean;
	externalEvidenceAccepted: boolean;
	acceptedEvidenceLevels: EvidenceClassification[];
	effectiveStart: string;
	effectiveEnd: string | null;
	expirationRule: ExpirationRule;
	expirationDays: number | null;
	status: RequirementStatus;
	createdAt: string;
	createdByUserId: string;
}

export interface EligibilityEvidence {
	id: string;
	requirementId: string;
	classification: EvidenceClassification;
	acceptanceMethod: AcceptanceMethod;
	enteredLegalName: string | null;
	documentAssetId: string | null;
	status: EvidenceStatus;
	acceptedAt: string;
	expiresAt: string | null;
}

export interface ParticipantRequirement {
	requirement: EligibilityRequirement;
	evidence: EligibilityEvidence | null;
}

export interface CreateEligibilityRequirementRequest {
	title: string;
	mode: RequirementMode;
	content: string;
	sport?: string;
	season?: string;
	teamId?: string;
	effectiveStart: string;
	expirationRule?: ExpirationRule;
	expirationDays?: number;
}

export interface CreateEligibilityRequirementVersionRequest {
	title: string;
	content: string;
	effectiveStart: string;
}

export interface SubmitGuardianEvidenceRequest {
	acceptanceMethod: AcceptanceMethod;
	enteredLegalName?: string;
	documentAssetId?: string;
}
