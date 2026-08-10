/**
 * Matches backend/src/main/kotlin/com/rally26/dashboard/web/OwnerDashboardDto.kt and
 * DashboardCommonDto.kt (Owner persona, ADR-105). Verified directly against
 * OwnerDashboardService.kt — the DTO file's own doc comments are stale (they say
 * fundraising/reports-snapshot stay demo); the actual service code sets
 * isFundraisingDemoData=false and isDemoData=false for reports-snapshot now. Only
 * AttentionItem (getAttentionRequired) and OwnerOnboardingProgress
 * (getOnboardingProgress) are still 100% hardcoded — deliberately not wired here.
 */

export interface OwnerSummaryResponse {
  organizationName: string;
  activeTeams: number;
  participants: number;
  households: number;
  upcomingTournaments: number;
}

export interface FinancialOverviewResponse {
  isFeesDemoData: boolean;
  isFundraisingDemoData: boolean;
  currency: string;
  feesAssignedMinor: number;
  feesCollectedMinor: number;
  outstandingMinor: number;
  fundraisingMinor: number;
  apparelSalesMinor: number;
  pendingPayoutMinor: number;
}

export interface TeamPerformanceRow {
  teamId: string;
  name: string;
  sport: string;
  participants: number;
  status: string;
  isFundraisingDemoData: boolean;
  fundraisingRaisedMinor: number | null;
  fundraisingGoalMinor: number | null;
}

export interface ScheduleItem {
  id: string;
  day: string;
  date: string;
  title: string;
  subtitle: string;
  time: string;
  tag: string | null;
}

export interface ActivityItem {
  id: string;
  action: string;
  entityType: string;
  entityId: string;
  occurredAt: string;
}

export interface ReportMetric {
  isDemoData: boolean;
  label: string;
  valueMinor: number;
  trendPercent: number;
}
