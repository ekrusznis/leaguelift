import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiFetch } from "../../lib/apiClient";
import type {
	CreateProfileCorrectionInput,
	ProfileCorrectionPage,
	ProfileCorrectionRequest,
	ProfileCorrectionStatus,
} from "./types";

const organizationKey = (organizationId: string) => ["organizations", organizationId, "profile-corrections"] as const;
const householdKey = (organizationId: string, householdId: string) => [
	"organizations",
	organizationId,
	"households",
	householdId,
	"profile-corrections",
] as const;

export function useOrganizationProfileCorrections(organizationId: string, status?: ProfileCorrectionStatus) {
	return useQuery({
		queryKey: [...organizationKey(organizationId), status ?? "ALL"],
		queryFn: () => apiFetch<ProfileCorrectionPage>(
			`/organizations/${organizationId}/profile-correction-requests?size=100${status ? `&status=${status}` : ""}`,
		),
		enabled: !!organizationId,
	});
}

export function useHouseholdProfileCorrections(organizationId: string, householdId: string) {
	return useQuery({
		queryKey: householdKey(organizationId, householdId),
		queryFn: () => apiFetch<ProfileCorrectionRequest[]>(
			`/organizations/${organizationId}/households/${householdId}/profile-correction-requests`,
		),
		enabled: !!organizationId && !!householdId,
	});
}

export function useCreateProfileCorrection(organizationId: string, householdId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (input: CreateProfileCorrectionInput) =>
			apiFetch<ProfileCorrectionRequest>(`/organizations/${organizationId}/profile-correction-requests`, {
				method: "POST",
				body: input,
			}),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: organizationKey(organizationId) });
			queryClient.invalidateQueries({ queryKey: householdKey(organizationId, householdId) });
		},
	});
}

export function useReviewProfileCorrection(organizationId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: ({ requestId, decision, reviewNote }: { requestId: string; decision: "approve" | "reject"; reviewNote: string }) =>
			apiFetch<ProfileCorrectionRequest>(`/organizations/${organizationId}/profile-correction-requests/${requestId}/${decision}`, {
				method: "POST",
				body: { reviewNote: reviewNote || null },
			}),
		onSuccess: (request) => {
			queryClient.invalidateQueries({ queryKey: organizationKey(organizationId) });
			queryClient.invalidateQueries({ queryKey: householdKey(organizationId, request.householdId) });
			queryClient.invalidateQueries({ queryKey: ["organizations", organizationId, "households", request.householdId] });
		},
	});
}

export function useWithdrawProfileCorrection(organizationId: string, householdId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (requestId: string) =>
			apiFetch<void>(`/organizations/${organizationId}/profile-correction-requests/${requestId}/withdraw`, { method: "POST" }),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: organizationKey(organizationId) });
			queryClient.invalidateQueries({ queryKey: householdKey(organizationId, householdId) });
		},
	});
}
