export type DuplicateIdentityKind = "APP_USER" | "GUARDIAN_SHELL";
export type DuplicateMatchType = "EMAIL" | "PHONE";
export type DuplicateResolutionStrategy = "LINK_SHELL_TO_EXISTING_USER" | "MERGE_USER_ACCOUNTS" | "REVIEW_SHELLS_SEPARATELY" | "KEEP_SEPARATE";
export type MergePlanSeverity = "INFO" | "WARNING" | "BLOCKER";

export interface DuplicateIdentityRef { kind: DuplicateIdentityKind; id: string; }
export interface DuplicateMembership { organizationId: string; organizationName: string; role: string; status: string; }
export interface DuplicateIdentity {
	ref: DuplicateIdentityRef;
	displayName: string;
	email: string | null;
	phone: string | null;
	status: string;
	createdAt: string;
	organizationId: string | null;
	organizationName: string | null;
	householdId: string | null;
	householdName: string | null;
	linkedUserId: string | null;
	platformAdministrator: boolean;
	memberships: DuplicateMembership[];
	externalIds: string[];
}
export interface DuplicateCandidateGroup { matchType: DuplicateMatchType; normalizedValue: string; identities: DuplicateIdentity[]; }
export interface DuplicateCandidateListResponse { items: DuplicateCandidateGroup[]; }
export interface IdentityDependency { tableName: string; columnName: string; count: number; historical: boolean; }
export interface MergePlanItem { code: string; severity: MergePlanSeverity; summary: string; }
export interface DuplicateMergePreview {
	source: DuplicateIdentity;
	target: DuplicateIdentity;
	strategy: DuplicateResolutionStrategy;
	canProceedToMutationSlice: boolean;
	dependencies: IdentityDependency[];
	plan: MergePlanItem[];
}
