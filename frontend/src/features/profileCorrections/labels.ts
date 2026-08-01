import type { ProfileCorrectionField, ProfileCorrectionStatus } from "./types";

export const PROFILE_CORRECTION_FIELD_LABELS: Record<ProfileCorrectionField, string> = {
	ADULT_FIRST_NAME: "First name",
	ADULT_LAST_NAME: "Last name",
	ADULT_EMAIL: "Email",
	ADULT_PHONE: "Phone",
	ADULT_RELATIONSHIP: "Relationship",
	PARTICIPANT_FIRST_NAME: "First name",
	PARTICIPANT_LAST_NAME: "Last name",
	PARTICIPANT_DATE_OF_BIRTH: "Date of birth",
};

export const PROFILE_CORRECTION_STATUS_LABELS: Record<ProfileCorrectionStatus, string> = {
	PENDING: "Pending review",
	APPROVED: "Approved",
	REJECTED: "Rejected",
	WITHDRAWN: "Withdrawn",
};
