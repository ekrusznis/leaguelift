/** Matches backend/src/main/kotlin/com/rally26/dashboard/web/CoachDashboardDto.kt (ADR-102). */
export interface CoachTeamSummary {
  teamId: string;
  name: string;
  sport: string;
  participants: number;
}
