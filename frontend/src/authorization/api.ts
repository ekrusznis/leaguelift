import { useQuery } from "@tanstack/react-query";
import { apiFetch } from "../lib/apiClient";
import type { AuthorizationContext } from "./types";

/**
 * `useContexts` — the frontend half of DESIGN-DOC.md section 10.3's build order
 * (`useCurrentUser`/`useContexts`/`Can`/`RequireCapability`). Every dashboard/nav/widget
 * capability check ultimately reads from this one query.
 */
export function useContexts() {
	return useQuery({
		queryKey: ["me", "contexts"],
		queryFn: () => apiFetch<AuthorizationContext[]>("/me/contexts"),
		staleTime: 60_000,
	});
}
