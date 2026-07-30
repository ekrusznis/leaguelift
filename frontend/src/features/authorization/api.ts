import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiFetch } from "../../lib/apiClient";
import type { RoleAssignment } from "./types";

/**
 * Grant/revoke UI for the team/tournament role-assignment endpoints ADR-020 shipped
 * API-only (no admin screen existed to call them). Kept separate from
 * `authorization/api.ts` (the `/me/contexts` self-service hooks) — this is the
 * org-manager-facing management side, not the caller's own capability data.
 */

const teamRoleAssignmentsQueryKey = (organizationId: string, teamId: string) =>
	["organizations", organizationId, "teams", teamId, "role-assignments"] as const;

export function useTeamRoleAssignments(organizationId: string, teamId: string) {
	return useQuery({
		queryKey: teamRoleAssignmentsQueryKey(organizationId, teamId),
		queryFn: () => apiFetch<RoleAssignment[]>(`/organizations/${organizationId}/teams/${teamId}/role-assignments`),
		enabled: !!organizationId && !!teamId,
	});
}

export function useGrantTeamRole(organizationId: string, teamId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (values: { userId: string; role: string }) =>
			apiFetch<RoleAssignment>(`/organizations/${organizationId}/teams/${teamId}/role-assignments`, {
				method: "POST",
				body: values,
			}),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: teamRoleAssignmentsQueryKey(organizationId, teamId) });
		},
	});
}

export function useRevokeTeamRole(organizationId: string, teamId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (assignmentId: string) =>
			apiFetch(`/organizations/${organizationId}/teams/${teamId}/role-assignments/${assignmentId}`, { method: "DELETE" }),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: teamRoleAssignmentsQueryKey(organizationId, teamId) });
		},
	});
}

const tournamentRoleAssignmentsQueryKey = (organizationId: string, tournamentId: string) =>
	["organizations", organizationId, "tournaments", tournamentId, "role-assignments"] as const;

export function useTournamentRoleAssignments(organizationId: string, tournamentId: string) {
	return useQuery({
		queryKey: tournamentRoleAssignmentsQueryKey(organizationId, tournamentId),
		queryFn: () => apiFetch<RoleAssignment[]>(`/organizations/${organizationId}/tournaments/${tournamentId}/role-assignments`),
		enabled: !!organizationId && !!tournamentId,
	});
}

export function useGrantTournamentRole(organizationId: string, tournamentId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (values: { userId: string; role: string }) =>
			apiFetch<RoleAssignment>(`/organizations/${organizationId}/tournaments/${tournamentId}/role-assignments`, {
				method: "POST",
				body: values,
			}),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: tournamentRoleAssignmentsQueryKey(organizationId, tournamentId) });
		},
	});
}

export function useRevokeTournamentRole(organizationId: string, tournamentId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (assignmentId: string) =>
			apiFetch(`/organizations/${organizationId}/tournaments/${tournamentId}/role-assignments/${assignmentId}`, { method: "DELETE" }),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: tournamentRoleAssignmentsQueryKey(organizationId, tournamentId) });
		},
	});
}
