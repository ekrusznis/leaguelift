/**
 * Matches backend/src/main/kotlin/com/rally26/participant/web/ParticipantDto.kt (ADR-102).
 * No "position"/jersey-number field exists anywhere on the backend Participant model —
 * that was invented for docs/design/mobile_sample_design.png's mockup and is
 * deliberately not modeled here (ADR-102 founder decision: use the real domain).
 */
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
