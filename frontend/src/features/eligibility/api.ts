import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiFetch } from "../../lib/apiClient";
import type {
	ClearanceStatus,
	CreateEligibilityRequirementRequest,
	CreateEligibilityRequirementVersionRequest,
	EligibilityClearance,
	EligibilityRequirement,
	ParticipantRequirement,
	SubmitGuardianEvidenceRequest,
} from "./types";

const organizationRequirementsQueryKey = (organizationId: string) =>
	["organizations", organizationId, "eligibility", "requirements"] as const;
const participantRequirementsQueryKey = (organizationId: string, participantId: string) =>
	["organizations", organizationId, "eligibility", "participants", participantId, "requirements"] as const;

export function useEligibilityRequirements(organizationId: string) {
	return useQuery({
		queryKey: organizationRequirementsQueryKey(organizationId),
		queryFn: () => apiFetch<EligibilityRequirement[]>(`/organizations/${organizationId}/eligibility/requirements`),
		enabled: !!organizationId,
	});
}

export function useCreateEligibilityRequirement(organizationId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (values: CreateEligibilityRequirementRequest) =>
			apiFetch<EligibilityRequirement>(`/organizations/${organizationId}/eligibility/requirements`, {
				method: "POST",
				body: values,
			}),
		onSuccess: () => queryClient.invalidateQueries({ queryKey: organizationRequirementsQueryKey(organizationId) }),
	});
}

export function useCreateEligibilityRequirementVersion(organizationId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: ({ requirementId, ...values }: CreateEligibilityRequirementVersionRequest & { requirementId: string }) =>
			apiFetch<EligibilityRequirement>(`/organizations/${organizationId}/eligibility/requirements/${requirementId}/versions`, {
				method: "POST",
				body: values,
			}),
		onSuccess: () => queryClient.invalidateQueries({ queryKey: organizationRequirementsQueryKey(organizationId) }),
	});
}

export function useArchiveEligibilityRequirement(organizationId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (requirementId: string) =>
			apiFetch(`/organizations/${organizationId}/eligibility/requirements/${requirementId}/archive`, { method: "POST" }),
		onSuccess: () => queryClient.invalidateQueries({ queryKey: organizationRequirementsQueryKey(organizationId) }),
	});
}

export function useParticipantEligibilityRequirements(organizationId: string, participantId: string) {
	return useQuery({
		queryKey: participantRequirementsQueryKey(organizationId, participantId),
		queryFn: () =>
			apiFetch<ParticipantRequirement[]>(
				`/organizations/${organizationId}/eligibility/participants/${participantId}/requirements`,
			),
		enabled: !!organizationId && !!participantId,
	});
}

export function useSubmitGuardianEvidence(organizationId: string, participantId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: ({ requirementId, ...values }: SubmitGuardianEvidenceRequest & { requirementId: string }) =>
			apiFetch(
				`/organizations/${organizationId}/eligibility/participants/${participantId}/requirements/${requirementId}/evidence`,
				{ method: "POST", body: values },
			),
		onSuccess: () => queryClient.invalidateQueries({ queryKey: participantRequirementsQueryKey(organizationId, participantId) }),
	});
}

/** GET .../teams/{teamId}/eligibility/clearance?status= — coach roster clearance view (Phase 31 slice 31.1, wired to a real UI in Phase 37 slice 37.1). */
export function useTeamEligibilityClearance(organizationId: string, teamId: string, statusFilter?: ClearanceStatus | null) {
	return useQuery({
		queryKey: ["organizations", organizationId, "teams", teamId, "eligibility", "clearance", statusFilter ?? null],
		queryFn: () =>
			apiFetch<EligibilityClearance[]>(
				`/organizations/${organizationId}/teams/${teamId}/eligibility/clearance${statusFilter ? `?status=${statusFilter}` : ""}`,
			),
		enabled: !!organizationId && !!teamId,
	});
}
