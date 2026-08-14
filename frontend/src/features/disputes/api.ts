import { useQuery } from "@tanstack/react-query";
import { apiFetch } from "../../lib/apiClient";
import type { Dispute, DisputePage, DisputeSourceType, DisputeStatus } from "./types";

const disputesKey = (organizationId: string) => ["organizations", organizationId, "disputes"] as const;

export function useDisputes(organizationId: string) {
	return useQuery({
		queryKey: disputesKey(organizationId),
		queryFn: () => apiFetch<Dispute[]>(`/organizations/${organizationId}/disputes`),
		enabled: !!organizationId,
	});
}

export interface DisputeSearchOptions {
	query?: string;
	status?: DisputeStatus | "";
	sourceType?: DisputeSourceType | "";
	sort?: "newest" | "oldest";
	page?: number;
	size?: number;
}

export function useDisputeSearch(organizationId: string, options: DisputeSearchOptions = {}) {
	const params = new URLSearchParams({
		page: String(options.page ?? 0),
		size: String(options.size ?? 20),
		sort: options.sort ?? "newest",
	});
	if (options.query?.trim()) params.set("q", options.query.trim());
	if (options.status) params.set("status", options.status);
	if (options.sourceType) params.set("sourceType", options.sourceType);
	return useQuery({
		queryKey: [...disputesKey(organizationId), "search", options] as const,
		queryFn: () => apiFetch<DisputePage>(`/organizations/${organizationId}/disputes/search?${params}`),
		enabled: !!organizationId,
	});
}
