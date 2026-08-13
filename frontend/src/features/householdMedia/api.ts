import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiFetch } from "../../lib/apiClient";
import type { HouseholdMediaItem, HouseholdMediaListResponse } from "./types";

const householdMediaQueryKey = (organizationId: string, householdId: string) =>
	["organizations", organizationId, "households", householdId, "media"] as const;

export function useHouseholdMedia(organizationId: string, householdId: string) {
	return useQuery({
		queryKey: householdMediaQueryKey(organizationId, householdId),
		queryFn: () => apiFetch<HouseholdMediaListResponse>(`/organizations/${organizationId}/households/${householdId}/media`),
		enabled: !!organizationId && !!householdId,
	});
}

export function useAssignHouseholdMedia(organizationId: string, householdId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (assetId: string) =>
			apiFetch<HouseholdMediaItem>(`/organizations/${organizationId}/households/${householdId}/media`, {
				method: "POST",
				body: { assetId },
			}),
		onSuccess: () => queryClient.invalidateQueries({ queryKey: householdMediaQueryKey(organizationId, householdId) }),
	});
}

export function useRemoveHouseholdMedia(organizationId: string, householdId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (assignmentId: string) =>
			apiFetch(`/organizations/${organizationId}/households/${householdId}/media/${assignmentId}`, { method: "DELETE" }),
		onSuccess: () => queryClient.invalidateQueries({ queryKey: householdMediaQueryKey(organizationId, householdId) }),
	});
}

export function useReleaseHouseholdMediaPublicly(organizationId: string, householdId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (assignmentIds: string[]) =>
			apiFetch<HouseholdMediaListResponse>(`/organizations/${organizationId}/households/${householdId}/media/release-publicly`, {
				method: "POST",
				body: { assignmentIds },
			}),
		onSuccess: () => queryClient.invalidateQueries({ queryKey: householdMediaQueryKey(organizationId, householdId) }),
	});
}
