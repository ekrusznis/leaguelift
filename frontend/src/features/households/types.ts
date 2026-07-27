export interface Household {
	id: string;
	organizationId: string;
	displayName: string;
	contactEmail: string | null;
	contactPhone: string | null;
	notes: string | null;
	status: "ACTIVE" | "ARCHIVED";
	createdAt: string;
	updatedAt: string;
}

export interface HouseholdPage {
	items: Household[];
	page: number;
	size: number;
	totalElements: number;
}

export interface HouseholdAdult {
	id: string;
	householdId: string;
	organizationId: string;
	firstName: string;
	lastName: string;
	email: string | null;
	phone: string | null;
	relationship: string | null;
	isPrimary: boolean;
	status: "ACTIVE" | "ARCHIVED";
	createdAt: string;
	updatedAt: string;
}

export interface Participant {
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

export interface ParticipantTeamAssignment {
	id: string;
	participantId: string;
	teamId: string;
	status: string;
	joinedAt: string | null;
	createdAt: string;
}
