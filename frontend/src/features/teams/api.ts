import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiFetch } from "../../lib/apiClient";
import type { Participant } from "../households/types";
import type { CreateTeamFormValues } from "./schema";
import type { Team, TeamPage } from "./types";

const teamsQueryKey = (organizationId: string) => ["organizations", organizationId, "teams"] as const;

export function useTeams(organizationId: string) {
	return useQuery({
		queryKey: teamsQueryKey(organizationId),
		queryFn: () => apiFetch<TeamPage>(`/organizations/${organizationId}/teams`),
		enabled: !!organizationId,
	});
}

/** GET .../teams/{teamId}/participants — "a coach's team roster picker" (Swag Shop, DESIGN-DOC.md section 13), reused here for the real roster list. */
export function useTeamRoster(organizationId: string, teamId: string) {
	return useQuery({
		queryKey: ["organizations", organizationId, "teams", teamId, "participants"],
		queryFn: () => apiFetch<Participant[]>(`/organizations/${organizationId}/teams/${teamId}/participants`),
		enabled: !!organizationId && !!teamId,
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
					ageGroup: values.ageGroup || null,
					genderCategory: values.genderCategory || null,
					level: values.level || null,
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

/** Phase 24 slice 24.5 (ADR-071): a null timezone explicitly clears the override back to "inherit organization default." */
export function useUpdateTeamTimezone(organizationId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: ({ teamId, timezone }: { teamId: string; timezone: string | null }) =>
			apiFetch<Team>(`/organizations/${organizationId}/teams/${teamId}/timezone`, {
				method: "PUT",
				body: { timezone },
			}),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: teamsQueryKey(organizationId) });
		},
	});
}

/** Phase 35 (ADR-099): a null color explicitly clears that color back to Rally26's default brand color. */
export function useUpdateTeamColors(organizationId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: ({ teamId, primaryColor, secondaryColor }: { teamId: string; primaryColor: string | null; secondaryColor: string | null }) =>
			apiFetch<Team>(`/organizations/${organizationId}/teams/${teamId}/colors`, {
				method: "PATCH",
				body: { primaryColor, secondaryColor },
			}),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: teamsQueryKey(organizationId) });
		},
	});
}
