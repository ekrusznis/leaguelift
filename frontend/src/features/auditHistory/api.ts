import { useQuery } from "@tanstack/react-query";
import { apiFetch } from "../../lib/apiClient";
import type { AuditHistoryFilters, AuditHistoryPageResponse } from "./types";

function append(params: URLSearchParams, key: string, value: string | undefined) {
	if (value?.trim()) params.set(key, value.trim());
}

export function auditHistoryQuery(filters: AuditHistoryFilters) {
	const params = new URLSearchParams();
	append(params, "from", filters.from);
	append(params, "to", filters.to);
	append(params, "action", filters.action);
	append(params, "result", filters.result || undefined);
	append(params, "keyword", filters.keyword);
	append(params, "user", filters.user);
	append(params, "organizationId", filters.organizationId);
	append(params, "teamId", filters.teamId);
	params.set("sortBy", filters.sortBy);
	params.set("direction", filters.direction);
	params.set("size", String(filters.size ?? 50));
	append(params, "cursor", filters.cursor);
	return params.toString();
}

export function useAuditHistory(filters: AuditHistoryFilters) {
	return useQuery({
		queryKey: ["audit-history", filters],
		queryFn: () => apiFetch<AuditHistoryPageResponse>(`/audit-history?${auditHistoryQuery(filters)}`),
	});
}
