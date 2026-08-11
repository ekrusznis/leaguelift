import { useQuery } from '@tanstack/react-query';

import { apiFetch } from '@/lib/apiClient';

import type {
  ActivityItem,
  FinancialOverviewResponse,
  OwnerSummaryResponse,
  ReportMetric,
  ScheduleItem,
  TeamPerformanceRow,
} from './types';

/**
 * GET /organizations/{organizationId}/dashboard/owner/* — org-scoped, not self-
 * resolving like Athlete's /me/dashboard/athlete/* (an Owner/Administrator/Viewer all
 * share DashboardRole.OWNER, so organizationId must come from dashboard-context, not
 * be assumed). attention-required and onboarding-progress cards are deliberately not
 * wired here — both are unconditionally hardcoded server-side (ADR-105).
 */
export function useOwnerSummary(organizationId: string | null) {
  return useQuery({
    queryKey: ['organizations', organizationId, 'dashboard', 'owner', 'summary'],
    queryFn: ({ signal }) => apiFetch<OwnerSummaryResponse>(`/organizations/${organizationId}/dashboard/owner/summary`, { signal }),
    enabled: !!organizationId,
  });
}

export function useOwnerFinancialOverview(organizationId: string | null) {
  return useQuery({
    queryKey: ['organizations', organizationId, 'dashboard', 'owner', 'financial-overview'],
    queryFn: ({ signal }) =>
      apiFetch<FinancialOverviewResponse>(`/organizations/${organizationId}/dashboard/owner/financial-overview`, { signal }),
    enabled: !!organizationId,
  });
}

export function useOwnerTeamPerformance(organizationId: string | null) {
  return useQuery({
    queryKey: ['organizations', organizationId, 'dashboard', 'owner', 'team-performance'],
    queryFn: ({ signal }) =>
      apiFetch<TeamPerformanceRow[]>(`/organizations/${organizationId}/dashboard/owner/team-performance`, { signal }),
    enabled: !!organizationId,
  });
}

export function useOwnerUpcomingEvents(organizationId: string | null) {
  return useQuery({
    queryKey: ['organizations', organizationId, 'dashboard', 'owner', 'upcoming-events'],
    queryFn: ({ signal }) => apiFetch<ScheduleItem[]>(`/organizations/${organizationId}/dashboard/owner/upcoming-events`, { signal }),
    enabled: !!organizationId,
  });
}

export function useOwnerRecentActivity(organizationId: string | null) {
  return useQuery({
    queryKey: ['organizations', organizationId, 'dashboard', 'owner', 'recent-activity'],
    queryFn: ({ signal }) => apiFetch<ActivityItem[]>(`/organizations/${organizationId}/dashboard/owner/recent-activity`, { signal }),
    enabled: !!organizationId,
  });
}

export function useOwnerReportsSnapshot(organizationId: string | null) {
  return useQuery({
    queryKey: ['organizations', organizationId, 'dashboard', 'owner', 'reports-snapshot'],
    queryFn: ({ signal }) => apiFetch<ReportMetric[]>(`/organizations/${organizationId}/dashboard/owner/reports-snapshot`, { signal }),
    enabled: !!organizationId,
  });
}
