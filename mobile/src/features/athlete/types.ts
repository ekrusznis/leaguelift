/** Matches backend/src/main/kotlin/com/rally26/dashboard/web/AthleteDashboardDto.kt (Athlete persona). */

export interface NextEventSummary {
  title: string;
  subtitle: string;
  dateLabel: string;
  location: string;
}

export interface AthleteOverviewResponse {
  displayName: string;
  isDemoData: boolean;
  nextEvent: NextEventSummary | null;
}

export interface AthleteTeamSummary {
  name: string;
  detail: string;
  coachName: string;
}

export interface GuardianSummary {
  name: string;
  role: string;
  email: string;
  phone: string;
}
