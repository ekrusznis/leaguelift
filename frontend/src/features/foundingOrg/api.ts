import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiFetch } from "../../lib/apiClient";

export interface FoundingCodeValidation {
	valid: boolean;
	reason: string | null;
}

export interface FoundingOrgPromoCode {
	id: string;
	code: string;
	organizationId: string | null;
	redeemedAt: string | null;
	pilotEndsAt: string | null;
	pilotStatus: "UNREDEEMED" | "RESERVED" | "ACTIVE" | "CONVERTED" | "EXPIRED";
}

export function useValidateFoundingPromoCode(code: string | null) {
	return useQuery({
		queryKey: ["founding-promo-codes", "validate", code],
		queryFn: ({ signal }) => apiFetch<FoundingCodeValidation>(`/founding-promo-codes/${encodeURIComponent(code!)}/validate`, { signal }),
		enabled: !!code,
	});
}

export function usePlatformFoundingPromoCodes() {
	return useQuery({
		queryKey: ["platform", "founding-promo-codes"],
		queryFn: ({ signal }) => apiFetch<FoundingOrgPromoCode[]>("/platform/admin/founding-promo-codes", { signal }),
	});
}

export function useGenerateFoundingPromoCode() {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: () => apiFetch<FoundingOrgPromoCode>("/platform/admin/founding-promo-codes", { method: "POST" }),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: ["platform", "founding-promo-codes"] });
		},
	});
}
