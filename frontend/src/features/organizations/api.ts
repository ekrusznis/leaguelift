import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiFetch } from "../../lib/apiClient";
import type { CreateOrganizationFormValues } from "./schema";
import type { OrganizationPage } from "./types";

const organizationsQueryKey = (page: number, size: number) => ["organizations", { page, size }] as const;

export function useOrganizations(page = 0, size = 20) {
	return useQuery({
		queryKey: organizationsQueryKey(page, size),
		queryFn: () => apiFetch<OrganizationPage>(`/organizations?page=${page}&size=${size}`),
	});
}

export function useCreateOrganization() {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (values: CreateOrganizationFormValues) =>
			apiFetch("/organizations", { method: "POST", body: values }),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: ["organizations"] });
		},
	});
}
