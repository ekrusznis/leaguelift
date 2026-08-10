/** Matches backend/src/main/kotlin/com/rally26/dashboard/web/ParentDashboardDto.kt and household/participant DTOs (ADR-103). */

export interface AthleteSummary {
  participantId: string;
  name: string;
  teamNames: string[];
}

export interface ParticipantResponse {
  id: string;
  householdId: string;
  organizationId: string;
  firstName: string;
  lastName: string;
  dateOfBirth: string | null;
  notes: string | null;
  status: string;
  createdAt: string;
  updatedAt: string;
}

export interface ParticipantTeamResponse {
  id: string;
  participantId: string;
  teamId: string;
  status: string;
  joinedAt: string | null;
  createdAt: string;
}

export interface HouseholdResponse {
  id: string;
  organizationId: string;
  displayName: string;
  contactEmail: string | null;
  contactPhone: string | null;
  notes: string | null;
  emailRemindersOptOut: boolean;
  smsRemindersOptIn: boolean;
  status: string;
  createdAt: string;
  updatedAt: string;
}
