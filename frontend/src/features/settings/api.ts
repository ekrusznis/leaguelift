import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiFetch } from "../../lib/apiClient";
import type { UpdateUserPreferencesRequest, UserPreferences } from "./types";

export const userPreferencesQueryKey = ["me", "preferences"] as const;

export function useUserPreferences() {
	return useQuery({
		queryKey: userPreferencesQueryKey,
		queryFn: () => apiFetch<UserPreferences>("/me/preferences"),
	});
}

export function useUpdateUserPreferences() {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (request: UpdateUserPreferencesRequest) =>
			apiFetch<UserPreferences>("/me/preferences", { method: "PATCH", body: request }),
		onSuccess: (preferences) => {
			queryClient.setQueryData(userPreferencesQueryKey, preferences);
		},
	});
}
