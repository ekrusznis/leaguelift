import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiFetch } from "../../lib/apiClient";
import type {
	DuplicateCandidateListResponse,
	DuplicateIdentityRef,
	DuplicateMergePreview,
	IdentityResolutionReceipt,
	ResolveDuplicateIdentityRequest,
} from "./duplicateIdentityTypes";

export function useDuplicateIdentityCandidates(query: string, size = 50) {
	const params = new URLSearchParams({ size: String(size) });
	if (query.trim()) params.set("query", query.trim());
	return useQuery({
		queryKey: ["platform-duplicate-identities", query, size],
		queryFn: () => apiFetch<DuplicateCandidateListResponse>(`/platform/admin/data-integrity/duplicates?${params.toString()}`),
	});
}

export function useDuplicateMergePreview(source: DuplicateIdentityRef | null, target: DuplicateIdentityRef | null, enabled: boolean) {
	return useQuery({
		queryKey: ["platform-duplicate-merge-preview", source, target],
		enabled: enabled && Boolean(source && target),
		queryFn: () => {
			if (!source || !target) throw new Error("Source and target are required.");
			const params = new URLSearchParams({ sourceKind: source.kind, sourceId: source.id, targetKind: target.kind, targetId: target.id });
			return apiFetch<DuplicateMergePreview>(`/platform/admin/data-integrity/duplicates/preview?${params.toString()}`);
		},
	});
}

export function useResolveDuplicateIdentity() {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (request: ResolveDuplicateIdentityRequest) =>
			apiFetch<IdentityResolutionReceipt>("/platform/admin/data-integrity/duplicates/resolve", { method: "POST", body: request }),
		onSuccess: () => {
			// Mark the scan and preview stale without immediately replacing the completed
			// receipt on screen. The next navigation/refetch rebuilds them from live data.
			queryClient.invalidateQueries({ queryKey: ["platform-duplicate-identities"], refetchType: "none" });
			queryClient.invalidateQueries({ queryKey: ["platform-duplicate-merge-preview"], refetchType: "none" });
			queryClient.invalidateQueries({ queryKey: ["platform", "admin", "users"] });
			queryClient.invalidateQueries({ queryKey: ["audit-history"] });
		},
	});
}
