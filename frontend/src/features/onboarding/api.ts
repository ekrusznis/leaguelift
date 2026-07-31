import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiFetch } from "../../lib/apiClient";
import type { FeeTemplatePage } from "../fees/types";
import type { HouseholdPage } from "../households/types";
import type { TeamPage } from "../teams/types";
import type {
	BulkActionResult,
	OnboardingImportPreview,
	OnboardingImportResult,
	OrganizationParticipantPage,
} from "./types";

const onboardingKey = (organizationId: string) => ["organizations", organizationId, "onboarding"] as const;

export function usePreviewOnboardingImport(organizationId: string) {
	return useMutation({
		mutationFn: (request: { fileName: string | null; csvContent: string }) =>
			apiFetch<OnboardingImportPreview>(`/organizations/${organizationId}/onboarding/imports/preview`, {
				method: "POST",
				body: request,
			}),
	});
}

export function useExecuteOnboardingImport(organizationId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (request: { fileName: string | null; csvContent: string; expectedContentHash: string }) =>
			apiFetch<OnboardingImportResult>(`/organizations/${organizationId}/onboarding/imports/execute`, {
				method: "POST",
				body: request,
			}),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: ["organizations", organizationId] });
			queryClient.invalidateQueries({ queryKey: onboardingKey(organizationId) });
		},
	});
}

export function useBulkStaffInvitations(organizationId: string) {
	return useMutation({
		mutationFn: (request: { items: Array<{ email: string; role: string }> }) =>
			apiFetch<BulkActionResult>(`/organizations/${organizationId}/onboarding/bulk-invitations`, {
				method: "POST",
				body: request,
			}),
	});
}

export function useOnboardingTeams(organizationId: string) {
	return useQuery({
		queryKey: [...onboardingKey(organizationId), "teams"],
		queryFn: () => apiFetch<TeamPage>(`/organizations/${organizationId}/teams?size=500`),
		enabled: !!organizationId,
	});
}

export function useOnboardingHouseholds(organizationId: string) {
	return useQuery({
		queryKey: [...onboardingKey(organizationId), "households"],
		queryFn: () => apiFetch<HouseholdPage>(`/organizations/${organizationId}/households?size=500`),
		enabled: !!organizationId,
	});
}

export function useOnboardingFeeTemplates(organizationId: string) {
	return useQuery({
		queryKey: [...onboardingKey(organizationId), "fee-templates"],
		queryFn: () => apiFetch<FeeTemplatePage>(`/organizations/${organizationId}/fee-templates?size=500`),
		enabled: !!organizationId,
	});
}

export function useOrganizationParticipants(organizationId: string) {
	return useQuery({
		queryKey: [...onboardingKey(organizationId), "participants"],
		queryFn: () => apiFetch<OrganizationParticipantPage>(`/organizations/${organizationId}/participants?size=500`),
		enabled: !!organizationId,
	});
}

export function useBulkTeamAssignments(organizationId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (request: { teamId: string; participantIds: string[] }) =>
			apiFetch<BulkActionResult>(`/organizations/${organizationId}/onboarding/bulk-team-assignments`, {
				method: "POST",
				body: request,
			}),
		onSuccess: () => queryClient.invalidateQueries({ queryKey: onboardingKey(organizationId) }),
	});
}

export function useBulkFeeAssignments(organizationId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (request: { feeTemplateId: string; householdIds: string[]; dueDate: string | null }) =>
			apiFetch<BulkActionResult>(`/organizations/${organizationId}/onboarding/bulk-fee-assignments`, {
				method: "POST",
				body: request,
			}),
		onSuccess: () => queryClient.invalidateQueries({ queryKey: ["organizations", organizationId] }),
	});
}

export function useBulkDocumentAssignments(organizationId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (request: { assetId: string; householdIds: string[]; title: string | null }) =>
			apiFetch<BulkActionResult>(`/organizations/${organizationId}/onboarding/bulk-document-assignments`, {
				method: "POST",
				body: request,
			}),
		onSuccess: () => queryClient.invalidateQueries({ queryKey: ["organizations", organizationId] }),
	});
}
