import { useQuery } from "@tanstack/react-query";
import { apiFetch } from "../../lib/apiClient";
import type { Dispute } from "./types";

export function useDisputes(organizationId: string) {
	return useQuery({
		queryKey: ["organizations", organizationId, "disputes"] as const,
		queryFn: () => apiFetch<Dispute[]>(`/organizations/${organizationId}/disputes`),
		enabled: !!organizationId,
	});
}
