import { useQuery } from "@tanstack/react-query";
import { apiFetch } from "../../lib/apiClient";
import type { SearchResponse } from "./types";

export type SearchScope = { kind: "organization"; organizationId: string } | { kind: "platform" };

function searchPath(scope: SearchScope, query: string): string {
	const q = encodeURIComponent(query);
	return scope.kind === "organization" ? `/organizations/${scope.organizationId}/search?q=${q}` : `/platform/search?q=${q}`;
}

/** Global search (DESIGN-DOC.md section 13, Phase 7 completion). Only enabled once the query is at least 2 characters — matches the backend's own minimum. */
export function useSearch(scope: SearchScope, query: string) {
	return useQuery({
		queryKey: ["search", scope, query],
		queryFn: () => apiFetch<SearchResponse>(searchPath(scope, query)),
		enabled: query.trim().length >= 2,
	});
}
