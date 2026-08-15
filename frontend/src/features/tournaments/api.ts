import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiFetch } from "../../lib/apiClient";
import type { CreateTournamentFormValues } from "./schema";
import type { Tournament, TournamentPage } from "./types";

const tournamentsQueryKey = (organizationId: string) => ["organizations", organizationId, "tournaments"] as const;

/**
 * Tournaments are a relatively small organization-level collection, but the backend endpoint is paginated.
 * Load every page through the existing endpoint so the web list can provide honest client-side search/sort
 * without adding a second search service or endpoint.
 */
export function useTournaments(organizationId: string) {
	return useQuery({
		queryKey: tournamentsQueryKey(organizationId),
		queryFn: async () => {
			const pageSize = 100;
			const first = await apiFetch<TournamentPage>(
				`/organizations/${organizationId}/tournaments?page=0&size=${pageSize}`,
			);
			if (first.items.length >= first.totalElements) return first;

			const totalPages = Math.ceil(first.totalElements / pageSize);
			const remaining = await Promise.all(
				Array.from({ length: Math.max(0, totalPages - 1) }, (_, index) =>
					apiFetch<TournamentPage>(
						`/organizations/${organizationId}/tournaments?page=${index + 1}&size=${pageSize}`,
					),
				),
			);

			const items = [first, ...remaining].flatMap((page) => page.items);
			return {
				...first,
				items,
				page: 0,
				size: items.length,
			};
		},
		enabled: !!organizationId,
	});
}

export function useCreateTournament(organizationId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (values: CreateTournamentFormValues) =>
			apiFetch(`/organizations/${organizationId}/tournaments`, {
				method: "POST",
				body: {
					...values,
					sport: values.sport || null,
					startDate: values.startDate || null,
					endDate: values.endDate || null,
					location: values.location || null,
					contactEmail: values.contactEmail || null,
				},
			}),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: tournamentsQueryKey(organizationId) });
		},
	});
}

export function useArchiveTournament(organizationId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (tournamentId: string) =>
			apiFetch(`/organizations/${organizationId}/tournaments/${tournamentId}`, { method: "DELETE" }),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: tournamentsQueryKey(organizationId) });
		},
	});
}

/** Phase 24 slice 24.5 (ADR-071): a null timezone explicitly clears the override back to "inherit organization default." */
export function useUpdateTournamentTimezone(organizationId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: ({ tournamentId, timezone }: { tournamentId: string; timezone: string | null }) =>
			apiFetch<Tournament>(`/organizations/${organizationId}/tournaments/${tournamentId}/timezone`, {
				method: "PUT",
				body: { timezone },
			}),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: tournamentsQueryKey(organizationId) });
		},
	});
}
