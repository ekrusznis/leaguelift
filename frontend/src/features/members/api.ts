import { useQuery } from "@tanstack/react-query";
import { apiFetch } from "../../lib/apiClient";
import type { MembershipPage } from "./types";

/** Org members with resolved email/display name — the picker source for granting team/tournament roles (see features/authorization). */
export function useOrganizationMembers(organizationId: string, size = 100) {
	return useQuery({
		queryKey: ["organizations", organizationId, "members", size],
		queryFn: () => apiFetch<MembershipPage>(`/organizations/${organizationId}/members?size=${size}`),
		enabled: !!organizationId,
	});
}
