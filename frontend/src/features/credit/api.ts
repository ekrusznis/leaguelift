import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiFetch } from "../../lib/apiClient";
import type { AttributionLink, FamilyCreditBalance, FamilyCreditGrant, FamilyCreditTransfer, OrganizationCreditSettings, OrganizationMarkupRule } from "./types";

const balanceKey = (orgId: string, householdId: string) => ["organizations", orgId, "households", householdId, "credits", "balance"] as const;
const grantsKey = (orgId: string, householdId: string) => ["organizations", orgId, "households", householdId, "credits", "grants"] as const;
const attributionLinkKey = (orgId: string, householdId: string, campaignId: string) =>
	["organizations", orgId, "households", householdId, "campaigns", campaignId, "attribution-link"] as const;
const creditSettingsKey = (orgId: string) => ["organizations", orgId, "credit-settings"] as const;
const markupRulesKey = (orgId: string) => ["organizations", orgId, "markup-rules"] as const;
const pendingTransfersKey = (orgId: string) => ["organizations", orgId, "credit-transfers", "pending"] as const;

export function useFamilyCreditBalance(organizationId: string, householdId: string) {
	return useQuery({
		queryKey: balanceKey(organizationId, householdId),
		queryFn: () => apiFetch<FamilyCreditBalance>(`/organizations/${organizationId}/households/${householdId}/credits/balance`),
		enabled: !!organizationId && !!householdId,
	});
}

export function useFamilyCreditGrants(organizationId: string, householdId: string) {
	return useQuery({
		queryKey: grantsKey(organizationId, householdId),
		queryFn: () => apiFetch<FamilyCreditGrant[]>(`/organizations/${organizationId}/households/${householdId}/credits/grants`),
		enabled: !!organizationId && !!householdId,
	});
}

export function useApplyFamilyCredit(organizationId: string, householdId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (values: { feeAssignmentId: string; amountMinor: number }) =>
			apiFetch(`/organizations/${organizationId}/households/${householdId}/credits/apply`, { method: "POST", body: values }),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: balanceKey(organizationId, householdId) });
			queryClient.invalidateQueries({ queryKey: grantsKey(organizationId, householdId) });
			queryClient.invalidateQueries({ queryKey: ["organizations", organizationId, "households", householdId, "fee-assignments"] });
			queryClient.invalidateQueries({ queryKey: ["organizations", organizationId, "households", householdId, "dashboard", "parent", "outstanding-balance"] });
		},
	});
}

export function useTransferFamilyCredit(organizationId: string, householdId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (values: { toHouseholdId: string; amountMinor: number }) =>
			apiFetch<FamilyCreditTransfer>(`/organizations/${organizationId}/households/${householdId}/credits/transfer`, { method: "POST", body: values }),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: balanceKey(organizationId, householdId) });
			queryClient.invalidateQueries({ queryKey: grantsKey(organizationId, householdId) });
		},
	});
}

export function useAttributionLink(organizationId: string, householdId: string, campaignId: string) {
	return useQuery({
		queryKey: attributionLinkKey(organizationId, householdId, campaignId),
		queryFn: () => apiFetch<AttributionLink>(`/organizations/${organizationId}/households/${householdId}/campaigns/${campaignId}/attribution-link`),
		enabled: !!organizationId && !!householdId && !!campaignId,
	});
}

export function useSetAttributionPublicName(organizationId: string, householdId: string, campaignId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (publicDisplayName: string | null) =>
			apiFetch<AttributionLink>(`/organizations/${organizationId}/households/${householdId}/campaigns/${campaignId}/attribution-link`, {
				method: "PATCH",
				body: { publicDisplayName },
			}),
		onSuccess: () => queryClient.invalidateQueries({ queryKey: attributionLinkKey(organizationId, householdId, campaignId) }),
	});
}

export function useOrganizationCreditSettings(organizationId: string) {
	return useQuery({
		queryKey: creditSettingsKey(organizationId),
		queryFn: () => apiFetch<OrganizationCreditSettings>(`/organizations/${organizationId}/credit-settings`),
		enabled: !!organizationId,
	});
}

export function useUpdateOrganizationCreditSettings(organizationId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (values: OrganizationCreditSettings) =>
			apiFetch<OrganizationCreditSettings>(`/organizations/${organizationId}/credit-settings`, { method: "PUT", body: values }),
		onSuccess: () => queryClient.invalidateQueries({ queryKey: creditSettingsKey(organizationId) }),
	});
}

export function useMarkupRules(organizationId: string) {
	return useQuery({
		queryKey: markupRulesKey(organizationId),
		queryFn: () => apiFetch<OrganizationMarkupRule[]>(`/organizations/${organizationId}/markup-rules`),
		enabled: !!organizationId,
	});
}

export function useUpsertMarkupRule(organizationId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (values: { printifyBlueprintId: number | null; markupType: "PERCENTAGE" | "FLAT"; markupValue: number }) =>
			apiFetch<OrganizationMarkupRule>(`/organizations/${organizationId}/markup-rules`, { method: "POST", body: values }),
		onSuccess: () => queryClient.invalidateQueries({ queryKey: markupRulesKey(organizationId) }),
	});
}

export function useDeleteMarkupRule(organizationId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (ruleId: string) => apiFetch(`/organizations/${organizationId}/markup-rules/${ruleId}`, { method: "DELETE" }),
		onSuccess: () => queryClient.invalidateQueries({ queryKey: markupRulesKey(organizationId) }),
	});
}

/** Manager-facing review queue — a guardian-initiated transfer only moves value once approved here. */
export function usePendingCreditTransfers(organizationId: string) {
	return useQuery({
		queryKey: pendingTransfersKey(organizationId),
		queryFn: () => apiFetch<FamilyCreditTransfer[]>(`/organizations/${organizationId}/credit-transfers/pending`),
		enabled: !!organizationId,
	});
}

export function useApproveCreditTransfer(organizationId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (transferId: string) =>
			apiFetch<FamilyCreditTransfer>(`/organizations/${organizationId}/credit-transfers/${transferId}/approve`, { method: "POST" }),
		onSuccess: () => queryClient.invalidateQueries({ queryKey: pendingTransfersKey(organizationId) }),
	});
}

export function useRejectCreditTransfer(organizationId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: ({ transferId, reviewNote }: { transferId: string; reviewNote?: string }) =>
			apiFetch<FamilyCreditTransfer>(`/organizations/${organizationId}/credit-transfers/${transferId}/reject`, {
				method: "POST",
				body: { reviewNote: reviewNote || null },
			}),
		onSuccess: () => queryClient.invalidateQueries({ queryKey: pendingTransfersKey(organizationId) }),
	});
}
