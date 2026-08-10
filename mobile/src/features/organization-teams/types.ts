/** Matches backend/src/main/kotlin/com/rally26/team/web/TeamDto.kt's TeamResponse — the org-management view, distinct from features/teams's CoachTeamSummary. */
export interface OrgTeamResponse {
  id: string;
  organizationId: string;
  name: string;
  sport: string;
  season: string | null;
  status: string;
  contactEmail: string | null;
  timezoneOverride: string | null;
  ageGroup: string | null;
  genderCategory: string | null;
  level: string | null;
  primaryColor: string;
  secondaryColor: string;
  createdAt: string;
  updatedAt: string;
}
