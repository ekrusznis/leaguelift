import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiFetch } from "../../lib/apiClient";
import type { FinancialCorrection, FinancialCorrectionPage, FinancialCorrectionPreview, FinancialCorrectionTargetType } from "./types";

const key = (organizationId: string) => ["organizations", organizationId, "financial-corrections"] as const;
export interface CorrectionInput { targetType: FinancialCorrectionTargetType; targetId: string; amountMinor: number | null; reason: string }

export function useFinancialCorrections(organizationId: string) {
	return useQuery({ queryKey: key(organizationId), queryFn: () => apiFetch<FinancialCorrectionPage>(`/organizations/${organizationId}/financial-corrections`), enabled: !!organizationId });
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
