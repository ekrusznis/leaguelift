import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiFetch } from "../../lib/apiClient";
import { organizationQueryKey } from "../organizations/api";
import type { OnboardingLinkResponse, PayoutAccount, PayoutSummary } from "./types";

const payoutAccountKey = (organizationId: string) => ["organizations", organizationId, "payout-account"] as const;
const payoutSummaryKey = (organizationId: string) => ["organizations", organizationId, "payout-account", "summary"] as const;

export function useOwnerPayoutStatus(organizationId: string) {
	return useQuery({
		queryKey: payoutAccountKey(organizationId),
		queryFn: () => apiFetch<PayoutAccount | null>(`/organizations/${organizationId}/payout-account`),
		enabled: !!organizationId,
	});
}

export function useStartPayoutOnboarding(organizationId: string) {
	return useMutation({
		mutationFn: (params: { refreshUrl: string; returnUrl: string }) =>
			apiFetch<OnboardingLinkResponse>(`/organizations/${organizationId}/payout-account/onboarding-link`, {
				method: "POST",
				body: params,
			}),
	});
}

export function useRefreshPayoutStatus(organizationId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: () => apiFetch<PayoutAccount>(`/organizations/${organizationId}/payout-account/refresh`, { method: "POST" }),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: payoutAccountKey(organizationId) });
			queryClient.invalidateQueries({ queryKey: ["organizations", organizationId, "onboarding"] });
			queryClient.invalidateQueries({ queryKey: organizationQueryKey(organizationId) });
		},
	});
}

export function usePayoutSummary(organizationId: string) {
	return useQuery({
		queryKey: payoutSummaryKey(organizationId),
		queryFn: () => apiFetch<PayoutSummary>(`/organizations/${organizationId}/payout-account/summary`),
		enabled: !!organizationId,
	});
}

/** Manual-trigger-only this phase (ADR-017) — backend enforces OWNER/ADMINISTRATOR, no scheduler exists yet. */
export function useTriggerTransfer(organizationId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: () => apiFetch<PayoutSummary>(`/organizations/${organizationId}/payout-account/transfer`, { method: "POST" }),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: payoutSummaryKey(organizationId) });
		},
	});
}
