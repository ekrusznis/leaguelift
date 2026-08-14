import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiFetch } from "../../lib/apiClient";
import type { FinancialCorrection, FinancialCorrectionPage, FinancialCorrectionPreview, FinancialCorrectionTargetType } from "./types";

const key = (organizationId: string) => ["organizations", organizationId, "financial-corrections"] as const;

export interface CorrectionInput { targetType: FinancialCorrectionTargetType; targetId: string; amountMinor: number | null; reason: string }

export interface FinancialCorrectionListOptions {
	query?: string;
	targetType?: FinancialCorrectionTargetType | "";
	correctionType?: FinancialCorrection["correctionType"] | "";
	sort?: "newest" | "oldest";
	page?: number;
	size?: number;
}

export function useFinancialCorrections(
	organizationId: string,
	options: FinancialCorrectionListOptions = {},
) {
	const params = new URLSearchParams({
		page: String(options.page ?? 0),
		size: String(options.size ?? 20),
		sort: options.sort ?? "newest",
	});
	if (options.query?.trim()) params.set("q", options.query.trim());
	if (options.targetType) params.set("targetType", options.targetType);
	if (options.correctionType) params.set("correctionType", options.correctionType);
	return useQuery({
		queryKey: [...key(organizationId), options] as const,
		queryFn: () => apiFetch<FinancialCorrectionPage>(`/organizations/${organizationId}/financial-corrections?${params}`),
		enabled: !!organizationId,
	});
}
export function usePreviewFinancialCorrection(organizationId: string) {
	return useMutation({ mutationFn: (input: CorrectionInput) => apiFetch<FinancialCorrectionPreview>(`/organizations/${organizationId}/financial-corrections/preview`, { method: "POST", body: input }) });
}
export function useExecuteFinancialCorrection(organizationId: string) {
	const client = useQueryClient();
	return useMutation({
		mutationFn: (input: CorrectionInput & { confirmationHash: string; idempotencyKey: string }) => apiFetch<FinancialCorrection>(`/organizations/${organizationId}/financial-corrections/execute`, { method: "POST", body: input }),
		onSuccess: () => client.invalidateQueries({ queryKey: key(organizationId) }),
	});
}
