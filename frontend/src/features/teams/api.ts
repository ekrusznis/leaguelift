import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiFetch } from "../../lib/apiClient";
import type { CreateTeamFormValues } from "./schema";
import type { TeamPage } from "./types";

const teamsQueryKey = (organizationId: string) => ["organizations", organizationId, "teams"] as const;

export function useTeams(organizationId: string) {
	return useQuery({
		queryKey: teamsQueryKey(organizationId),
		queryFn: () => apiFetch<TeamPage>(`/organizations/${organizationId}/teams`),
		enabled: !!organizationId,
	});
}

export function useCreateTeam(organizationId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (values: CreateTeamFormValues) =>
			apiFetch(`/organizations/${organizationId}/teams`, {
				method: "POST",
				body: {
					...values,
					season: values.season || null,
					contactEmail: values.contactEmail || null,
				},
			}),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: teamsQueryKey(organizationId) });
		},
	});
}

export function useArchiveTeam(organizationId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (teamId: string) =>
			apiFetch(`/organizations/${organizationId}/teams/${teamId}`, { method: "DELETE" }),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: teamsQueryKey(organizationId) });
		},
	});
}
