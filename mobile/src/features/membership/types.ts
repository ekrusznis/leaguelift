/** Matches backend/src/main/kotlin/com/rally26/membership/web/MembershipDto.kt (ADR-105). */

export type MembershipRole = 'OWNER' | 'ADMINISTRATOR' | 'VIEWER' | 'TEAM_ADMINISTRATOR' | 'TOURNAMENT_ADMINISTRATOR';

export interface MembershipResponse {
  id: string;
  organizationId: string;
  userId: string;
  userEmail: string | null;
  userDisplayName: string | null;
  role: string;
  status: string;
  createdAt: string;
}
