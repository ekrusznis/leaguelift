import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiFetch } from "../../lib/apiClient";
import type { TeamPage } from "../teams/types";
import type { SeasonRolloverPreview, SeasonRolloverRequest, SeasonRolloverResult } from "./types";


export function useSeasonRolloverTeams(organizationId: string) {
	return useQuery({
		queryKey: ["organizations", organizationId, "teams", "season-rollover"],
		queryFn: () => apiFetch<TeamPage>(`/organizations/${organizationId}/teams?size=500`),
		enabled: !!organizationId,
	});
}

export function usePreviewSeasonRollover(organizationId: string) {
	return useMutation({
		mutationFn: (request: SeasonRolloverRequest) =>
			apiFetch<SeasonRolloverPreview>(`/organizations/${organizationId}/season-rollovers/preview`, {
				method: "POST",
				body: request,
			}),
	});
}

export function useExecuteSeasonRollover(organizationId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (request: SeasonRolloverRequest & { expectedConfirmationHash: string }) =>
			apiFetch<SeasonRolloverResult>(`/organizations/${organizationId}/season-rollovers/execute`, {
				method: "POST",
				body: request,
			}),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: ["organizations", organizationId, "teams"] });
			queryClient.invalidateQueries({ queryKey: ["organizations", organizationId, "onboarding"] });
			queryClient.invalidateQueries({ queryKey: ["me", "activity"] });
		},
	});
}
