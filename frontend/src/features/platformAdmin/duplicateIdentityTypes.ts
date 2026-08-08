export type DuplicateIdentityKind = "APP_USER" | "GUARDIAN_SHELL";
export type DuplicateMatchType = "EMAIL" | "PHONE";
export type DuplicateResolutionStrategy = "LINK_SHELL_TO_EXISTING_USER" | "MERGE_USER_ACCOUNTS" | "REVIEW_SHELLS_SEPARATELY" | "KEEP_SEPARATE";
export type MergePlanSeverity = "INFO" | "WARNING" | "BLOCKER";
export type IdentityResolutionOperationType = "LINK_GUARDIAN_SHELL" | "MERGE_APP_USERS";
export type IdentityResolutionOperationStatus = "COMPLETED" | "ROLLED_BACK";

export interface DuplicateIdentityRef { kind: DuplicateIdentityKind; id: string; }
export interface DuplicateMembership { organizationId: string; organizationName: string; role: string; status: string; }
export interface DuplicateRoleAssignment { organizationId: string; contextType: string; resourceId: string; role: string; }
export interface DuplicateGuardianLink { organizationId: string; householdId: string; householdAdultId: string; }
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
	roleAssignments: DuplicateRoleAssignment[];
	guardianLinks: DuplicateGuardianLink[];
	mergedIntoUserId: string | null;
}
export interface DuplicateCandidateGroup { matchType: DuplicateMatchType; normalizedValue: string; identities: DuplicateIdentity[]; }
export interface DuplicateCandidateListResponse { items: DuplicateCandidateGroup[]; }
export interface IdentityDependency { tableName: string; columnName: string; count: number; historical: boolean; }
export interface DuplicateMatchEvidence { matchType: DuplicateMatchType; normalizedValue: string; }
export interface MergePlanItem { code: string; severity: MergePlanSeverity; summary: string; }
export interface DuplicateMergePreview {
	source: DuplicateIdentity;
	target: DuplicateIdentity;
	strategy: DuplicateResolutionStrategy;
	canProceedToMutationSlice: boolean;
	dependencies: IdentityDependency[];
	sharedEvidence: DuplicateMatchEvidence[];
	plan: MergePlanItem[];
	requiredSupportOrganizationId: string | null;
	previewHash: string;
}
export interface ResolveDuplicateIdentityRequest {
	sourceKind: DuplicateIdentityKind;
	sourceId: string;
	targetKind: DuplicateIdentityKind;
	targetId: string;
	previewHash: string;
	supportAccessId: string;
	reason: string;
	confirmedTargetEmail: string;
}
export interface IdentityResolutionOutcome {
	membershipsMoved: number;
	membershipsDeduplicated: number;
	roleAssignmentsMoved: number;
	roleAssignmentsDeduplicated: number;
	guardianRelationshipsMoved: number;
	guardianRelationshipsDeduplicated: number;
	messageThreadMembershipsMoved: number;
	messageThreadMembershipsDeduplicated: number;
	messageRecipientAccessMoved: number;
	messageRecipientAccessAlreadyPresent: number;
	announcementRecipientAccessMoved: number;
	announcementRecipientAccessAlreadyPresent: number;
	guardianRelationshipCreated: boolean;
	authTokensInvalidated: number;
}
export interface IdentityResolutionReceipt {
	operationId: string;
	operationType: IdentityResolutionOperationType;
	status: IdentityResolutionOperationStatus;
	source: DuplicateIdentityRef;
	target: DuplicateIdentityRef;
	organizationId: string;
	supportAccessId: string;
	previewHash: string;
	completedAt: string;
	outcome: IdentityResolutionOutcome;
}
