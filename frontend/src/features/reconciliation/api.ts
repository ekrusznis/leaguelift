import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiFetch } from "../../lib/apiClient";
import type {
	ReconciliationIssuePage,
	ReconciliationResult,
	ReconciliationRunPage,
	ReconciliationRunStatus,
	ReconciliationSeverity,
} from "./types";

const latestKey = (id: string) => ["organizations", id, "reconciliation", "latest"] as const;
const runsKey = (id: string) => ["organizations", id, "reconciliation", "runs"] as const;
const issuesKey = (id: string) => ["organizations", id, "reconciliation", "issues"] as const;

export function useLatestReconciliation(id: string) {
	return useQuery({ queryKey: latestKey(id), queryFn: () => apiFetch<ReconciliationResult | undefined>(`/organizations/${id}/reconciliation-runs/latest`), enabled: !!id });
}

export interface ReconciliationRunListOptions {
	status?: ReconciliationRunStatus | "";
	sort?: "newest" | "oldest";
	page?: number;
	size?: number;
}

export function useReconciliationRuns(id: string, options: ReconciliationRunListOptions = {}) {
	const params = new URLSearchParams({
		page: String(options.page ?? 0),
		size: String(options.size ?? 20),
		sort: options.sort ?? "newest",
	});
	if (options.status) params.set("status", options.status);
	return useQuery({
		queryKey: [...runsKey(id), options] as const,
		queryFn: () => apiFetch<ReconciliationRunPage>(`/organizations/${id}/reconciliation-runs?${params}`),
		enabled: !!id,
	});
}

export interface ReconciliationIssueListOptions {
	query?: string;
	severity?: ReconciliationSeverity | "";
	resourceType?: string;
	sort?: "newest" | "oldest";
	page?: number;
	size?: number;
}

export function useReconciliationIssues(
	id: string,
	runId: string | null,
	options: ReconciliationIssueListOptions = {},
) {
	const params = new URLSearchParams({
		page: String(options.page ?? 0),
		size: String(options.size ?? 20),
		sort: options.sort ?? "newest",
	});
	if (options.query?.trim()) params.set("q", options.query.trim());
	if (options.severity) params.set("severity", options.severity);
	if (options.resourceType?.trim()) params.set("resourceType", options.resourceType.trim());
	return useQuery({
		queryKey: [...issuesKey(id), runId, options] as const,
		queryFn: () => apiFetch<ReconciliationIssuePage>(`/organizations/${id}/reconciliation-runs/${runId}/issues?${params}`),
		enabled: !!id && !!runId,
	});
}

export function useRunReconciliation(id: string) {
	const client = useQueryClient();
	return useMutation({
		mutationFn: () => apiFetch<ReconciliationResult>(`/organizations/${id}/reconciliation-runs`, { method: "POST" }),
		onSuccess: () => {
			void client.invalidateQueries({ queryKey: latestKey(id) });
			void client.invalidateQueries({ queryKey: runsKey(id) });
			void client.invalidateQueries({ queryKey: issuesKey(id) });
		},
	});
}
