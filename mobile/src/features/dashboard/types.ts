/** Matches backend/src/main/kotlin/com/rally26/dashboard/web/DashboardContextDto.kt (ADR-102). */
export type DashboardRole = 'OWNER' | 'COACH' | 'PARENT' | 'ATHLETE' | 'TOURNAMENT_ADMIN' | 'PLATFORM_ADMIN';

export interface DashboardContext {
  role: DashboardRole;
  organizationId: string | null;
  householdId: string | null;
  tournamentId: string | null;
}
