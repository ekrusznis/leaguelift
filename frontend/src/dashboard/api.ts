import { useQuery } from "@tanstack/react-query";
import { apiFetch } from "../lib/apiClient";
import type {
	AnnouncementItem,
	AthleteOverview,
	AthleteSummary,
	AthleteTeamSummary,
	AttentionItem,
	CoachTeamSummary,
	DashboardContext,
	FamilyCredits,
	FinancialOverview,
	FundraiserSummary,
	FundraisingProgress,
	GuardianSummary,
	HistoryItem,
	HouseholdOverview,
	OrderSummary,
	OutstandingBalance,
	OwnerOnboardingProgress,
	OwnerSummary,
	ReportMetric,
	RequiredActionItem,
	RosterSummary,
	ScheduleItem,
	TeamPageStatusItem,
	TeamPerformanceRow,
	ActivityItem,
	UpdateItem,
} from "./types";

/**
 * All dashboard API calls live here — components never call `apiFetch` directly (see
 * the per-feature `api.ts` convention used across `features/*`). One hook per card, to
 * match the backend's one-endpoint-per-card design (DESIGN-DOC.md section 10.1/10.2):
 * every call is authorized server-side off the caller's JWT-resolved identity on every
 * request; no user ID is ever passed by the client.
 */

export function useDashboardContext() {
	return useQuery({
		queryKey: ["me", "dashboard-context"],
		queryFn: () => apiFetch<DashboardContext>("/me/dashboard-context"),
	});
}

// --- Athlete ---

export function useAthleteOverview() {
	return useQuery({ queryKey: ["me", "dashboard", "athlete", "overview"], queryFn: () => apiFetch<AthleteOverview>("/me/dashboard/athlete/overview") });
}
export function useAthleteTeams() {
	return useQuery({ queryKey: ["me", "dashboard", "athlete", "teams"], queryFn: () => apiFetch<AthleteTeamSummary[]>("/me/dashboard/athlete/teams") });
}
export function useAthleteWeekEvents() {
	return useQuery({ queryKey: ["me", "dashboard", "athlete", "week-events"], queryFn: () => apiFetch<ScheduleItem[]>("/me/dashboard/athlete/week-events") });
}
export function useAthleteRecentHistory() {
	return useQuery({ queryKey: ["me", "dashboard", "athlete", "recent-history"], queryFn: () => apiFetch<HistoryItem[]>("/me/dashboard/athlete/recent-history") });
}
export function useAthleteGuardians() {
	return useQuery({ queryKey: ["me", "dashboard", "athlete", "guardians"], queryFn: () => apiFetch<GuardianSummary[]>("/me/dashboard/athlete/guardians") });
}
export function useAthleteOrders() {
	return useQuery({ queryKey: ["me", "dashboard", "athlete", "orders"], queryFn: () => apiFetch<OrderSummary[]>("/me/dashboard/athlete/orders") });
}

// --- Owner ---

export function useOwnerSummary(organizationId: string | null | undefined) {
	return useQuery({
		queryKey: ["organizations", organizationId, "dashboard", "owner", "summary"],
		queryFn: () => apiFetch<OwnerSummary>(`/organizations/${organizationId}/dashboard/owner/summary`),
		enabled: !!organizationId,
	});
}
export function useOwnerFinancialOverview(organizationId: string | null | undefined) {
	return useQuery({
		queryKey: ["organizations", organizationId, "dashboard", "owner", "financial-overview"],
		queryFn: () => apiFetch<FinancialOverview>(`/organizations/${organizationId}/dashboard/owner/financial-overview`),
		enabled: !!organizationId,
	});
}
export function useOwnerAttentionRequired(organizationId: string | null | undefined) {
	return useQuery({
		queryKey: ["organizations", organizationId, "dashboard", "owner", "attention-required"],
		queryFn: () => apiFetch<AttentionItem[]>(`/organizations/${organizationId}/dashboard/owner/attention-required`),
		enabled: !!organizationId,
	});
}
export function useOwnerTeamPerformance(organizationId: string | null | undefined) {
	return useQuery({
		queryKey: ["organizations", organizationId, "dashboard", "owner", "team-performance"],
		queryFn: () => apiFetch<TeamPerformanceRow[]>(`/organizations/${organizationId}/dashboard/owner/team-performance`),
		enabled: !!organizationId,
	});
}
export function useOwnerUpcomingEvents(organizationId: string | null | undefined) {
	return useQuery({
		queryKey: ["organizations", organizationId, "dashboard", "owner", "upcoming-events"],
		queryFn: () => apiFetch<ScheduleItem[]>(`/organizations/${organizationId}/dashboard/owner/upcoming-events`),
		enabled: !!organizationId,
	});
}
export function useOwnerRecentActivity(organizationId: string | null | undefined) {
	return useQuery({
		queryKey: ["organizations", organizationId, "dashboard", "owner", "recent-activity"],
		queryFn: () => apiFetch<ActivityItem[]>(`/organizations/${organizationId}/dashboard/owner/recent-activity`),
		enabled: !!organizationId,
	});
}
export function useOwnerOnboardingProgress(organizationId: string | null | undefined) {
	return useQuery({
		queryKey: ["organizations", organizationId, "dashboard", "owner", "onboarding-progress"],
		queryFn: () => apiFetch<OwnerOnboardingProgress>(`/organizations/${organizationId}/dashboard/owner/onboarding-progress`),
		enabled: !!organizationId,
	});
}
export function useOwnerReportsSnapshot(organizationId: string | null | undefined) {
	return useQuery({
		queryKey: ["organizations", organizationId, "dashboard", "owner", "reports-snapshot"],
		queryFn: () => apiFetch<ReportMetric[]>(`/organizations/${organizationId}/dashboard/owner/reports-snapshot`),
		enabled: !!organizationId,
	});
}

// --- Coach ---

export function useCoachTeams(organizationId: string | null | undefined) {
	return useQuery({
		queryKey: ["organizations", organizationId, "dashboard", "coach", "teams"],
		queryFn: () => apiFetch<CoachTeamSummary[]>(`/organizations/${organizationId}/dashboard/coach/teams`),
		enabled: !!organizationId,
	});
}
export function useCoachTeamSchedule(organizationId: string | null | undefined) {
	return useQuery({
		queryKey: ["organizations", organizationId, "dashboard", "coach", "team-schedule"],
		queryFn: () => apiFetch<ScheduleItem[]>(`/organizations/${organizationId}/dashboard/coach/team-schedule`),
		enabled: !!organizationId,
	});
}
export function useCoachRosterSummary(organizationId: string | null | undefined) {
	return useQuery({
		queryKey: ["organizations", organizationId, "dashboard", "coach", "roster-summary"],
		queryFn: () => apiFetch<RosterSummary>(`/organizations/${organizationId}/dashboard/coach/roster-summary`),
		enabled: !!organizationId,
	});
}
export function useCoachTeamPageStatus(organizationId: string | null | undefined) {
	return useQuery({
		queryKey: ["organizations", organizationId, "dashboard", "coach", "team-page-status"],
		queryFn: () => apiFetch<TeamPageStatusItem | null>(`/organizations/${organizationId}/dashboard/coach/team-page-status`),
		enabled: !!organizationId,
	});
}
export function useCoachFundraisingProgress(organizationId: string | null | undefined) {
	return useQuery({
		queryKey: ["organizations", organizationId, "dashboard", "coach", "fundraising-progress"],
		queryFn: () => apiFetch<FundraisingProgress | null>(`/organizations/${organizationId}/dashboard/coach/fundraising-progress`),
		enabled: !!organizationId,
	});
}
export function useCoachAnnouncements(organizationId: string | null | undefined) {
	return useQuery({
		queryKey: ["organizations", organizationId, "dashboard", "coach", "announcements"],
		queryFn: () => apiFetch<AnnouncementItem[]>(`/organizations/${organizationId}/dashboard/coach/announcements`),
		enabled: !!organizationId,
	});
}
export function useCoachRequiredActions(organizationId: string | null | undefined) {
	return useQuery({
		queryKey: ["organizations", organizationId, "dashboard", "coach", "required-actions"],
		queryFn: () => apiFetch<RequiredActionItem[]>(`/organizations/${organizationId}/dashboard/coach/required-actions`),
		enabled: !!organizationId,
	});
}

// --- Parent ---

function parentDashboardPath(organizationId: string | null | undefined, householdId: string | null | undefined, card: string) {
	return `/organizations/${organizationId}/households/${householdId}/dashboard/parent/${card}`;
}

export function useParentOverview(organizationId: string | null | undefined, householdId: string | null | undefined) {
	return useQuery({
		queryKey: ["organizations", organizationId, "households", householdId, "dashboard", "parent", "overview"],
		queryFn: () => apiFetch<HouseholdOverview>(parentDashboardPath(organizationId, householdId, "overview")),
		enabled: !!organizationId && !!householdId,
	});
}
export function useParentAthletes(organizationId: string | null | undefined, householdId: string | null | undefined) {
	return useQuery({
		queryKey: ["organizations", organizationId, "households", householdId, "dashboard", "parent", "athletes"],
		queryFn: () => apiFetch<AthleteSummary[]>(parentDashboardPath(organizationId, householdId, "athletes")),
		enabled: !!organizationId && !!householdId,
	});
}
export function useParentFamilySchedule(organizationId: string | null | undefined, householdId: string | null | undefined) {
	return useQuery({
		queryKey: ["organizations", organizationId, "households", householdId, "dashboard", "parent", "family-schedule"],
		queryFn: () => apiFetch<ScheduleItem[]>(parentDashboardPath(organizationId, householdId, "family-schedule")),
		enabled: !!organizationId && !!householdId,
	});
}
export function useParentOutstandingBalance(organizationId: string | null | undefined, householdId: string | null | undefined) {
	return useQuery({
		queryKey: ["organizations", organizationId, "households", householdId, "dashboard", "parent", "outstanding-balance"],
		queryFn: () => apiFetch<OutstandingBalance>(parentDashboardPath(organizationId, householdId, "outstanding-balance")),
		enabled: !!organizationId && !!householdId,
	});
}
export function useParentFamilyCredits(organizationId: string | null | undefined, householdId: string | null | undefined) {
	return useQuery({
		queryKey: ["organizations", organizationId, "households", householdId, "dashboard", "parent", "family-credits"],
		queryFn: () => apiFetch<FamilyCredits>(parentDashboardPath(organizationId, householdId, "family-credits")),
		enabled: !!organizationId && !!householdId,
	});
}
export function useParentActiveFundraisers(organizationId: string | null | undefined, householdId: string | null | undefined) {
	return useQuery({
		queryKey: ["organizations", organizationId, "households", householdId, "dashboard", "parent", "active-fundraisers"],
		queryFn: () => apiFetch<FundraiserSummary[]>(parentDashboardPath(organizationId, householdId, "active-fundraisers")),
		enabled: !!organizationId && !!householdId,
	});
}
export function useParentRecentOrders(organizationId: string | null | undefined, householdId: string | null | undefined) {
	return useQuery({
		queryKey: ["organizations", organizationId, "households", householdId, "dashboard", "parent", "recent-orders"],
		queryFn: () => apiFetch<OrderSummary[]>(parentDashboardPath(organizationId, householdId, "recent-orders")),
		enabled: !!organizationId && !!householdId,
	});
}
export function useParentRequiredActions(organizationId: string | null | undefined, householdId: string | null | undefined) {
	return useQuery({
		queryKey: ["organizations", organizationId, "households", householdId, "dashboard", "parent", "required-actions"],
		queryFn: () => apiFetch<RequiredActionItem[]>(parentDashboardPath(organizationId, householdId, "required-actions")),
		enabled: !!organizationId && !!householdId,
	});
}
export function useParentOrganizationUpdates(organizationId: string | null | undefined, householdId: string | null | undefined) {
	return useQuery({
		queryKey: ["organizations", organizationId, "households", householdId, "dashboard", "parent", "organization-updates"],
		queryFn: () => apiFetch<UpdateItem[]>(parentDashboardPath(organizationId, householdId, "organization-updates")),
		enabled: !!organizationId && !!householdId,
	});
}
