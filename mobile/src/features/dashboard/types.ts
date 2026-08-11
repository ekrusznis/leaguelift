/** Matches backend/src/main/kotlin/com/rally26/dashboard/web/DashboardContextDto.kt (ADR-102). */
export type DashboardRole = 'OWNER' | 'COACH' | 'PARENT' | 'ATHLETE' | 'TOURNAMENT_ADMIN' | 'PLATFORM_ADMIN';

export interface DashboardContext {
  role: DashboardRole;
  organizationId: string | null;
  householdId: string | null;
  tournamentId: string | null;
}

/**
 * Matches backend/src/main/kotlin/com/rally26/authorization/web/AuthorizationDto.kt's
 * ContextResponse and .../authorization/domain/ContextType.kt's 7-value enum.
 */
export type ContextType = 'PERSONAL' | 'ATHLETE' | 'HOUSEHOLD' | 'TEAM' | 'ORGANIZATION' | 'TOURNAMENT' | 'PLATFORM_ADMIN';

export interface ContextResponse {
  contextType: ContextType;
  resourceId: string | null;
  organizationId: string | null;
  label: string;
  role: string;
  capabilities: string[];
}
