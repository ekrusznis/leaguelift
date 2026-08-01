export type ProfileCorrectionTargetType = "HOUSEHOLD_ADULT" | "PARTICIPANT";

export type ProfileCorrectionField =
	| "ADULT_FIRST_NAME"
	| "ADULT_LAST_NAME"
	| "ADULT_EMAIL"
	| "ADULT_PHONE"
	| "ADULT_RELATIONSHIP"
	| "PARTICIPANT_FIRST_NAME"
	| "PARTICIPANT_LAST_NAME"
	| "PARTICIPANT_DATE_OF_BIRTH";

export type ProfileCorrectionStatus = "PENDING" | "APPROVED" | "REJECTED" | "WITHDRAWN";

export interface ProfileCorrectionRequest {
	id: string;
	organizationId: string;
	householdId: string;
	targetType: ProfileCorrectionTargetType;
	targetId: string;
	field: ProfileCorrectionField;
	targetLabel: string;
	currentValue: string | null;
	proposedValue: string;
	reason: string;
	status: ProfileCorrectionStatus;
	requestedBy: string;
	requesterName: string;
	requesterEmail: string;
	reviewedBy: string | null;
	reviewerName: string | null;
	reviewNote: string | null;
	requestedAt: string;
	reviewedAt: string | null;
	updatedAt: string;
}

export interface ProfileCorrectionPage {
	items: ProfileCorrectionRequest[];
	page: number;
	size: number;
	totalElements: number;
}

export interface CreateProfileCorrectionInput {
	targetType: ProfileCorrectionTargetType;
	targetId: string;
	field: ProfileCorrectionField;
	proposedValue: string;
	reason: string;
}
