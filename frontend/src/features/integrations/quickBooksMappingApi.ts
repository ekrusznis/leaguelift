import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiFetch } from "../../lib/apiClient";
import type {
	OwnerQuickBooksAccount,
	OwnerQuickBooksAccountMapping,
	OwnerQuickBooksMappingType,
	OwnerQuickBooksMappingValidation,
	QuickBooksMappingDefinition,
} from "./quickBooksMappingTools";

const quickBooksKey = (organizationId: string) => ["organizations", organizationId, "integrations", "quickbooks"] as const;

export function useQuickBooksMappingRules(organizationId: string) {
	return useQuery({
		queryKey: [...quickBooksKey(organizationId), "mapping-rules"],
		queryFn: () =>
			apiFetch<QuickBooksMappingDefinition[]>(`/organizations/${organizationId}/integrations/quickbooks/mapping-rules`),
		enabled: !!organizationId,
	});
}

export function useOwnerQuickBooksAccounts(organizationId: string, connectionId: string | null, enabled: boolean) {
	return useQuery({
		queryKey: [...quickBooksKey(organizationId), connectionId, "accounts"],
		queryFn: () => {
			if (!connectionId) throw new Error("QuickBooks is not connected.");
			return apiFetch<OwnerQuickBooksAccount[]>(
				`/organizations/${organizationId}/integrations/quickbooks/connections/${connectionId}/accounts`,
			);
		},
		enabled: enabled && !!connectionId,
	});
}

export function useSaveOwnerQuickBooksMapping(organizationId: string, connectionId: string | null) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (input: {
			mappingType: OwnerQuickBooksMappingType;
			accountId: string;
			acknowledgeWarning: boolean;
		}) => {
			if (!connectionId) throw new Error("QuickBooks is not connected.");
			return apiFetch<OwnerQuickBooksAccountMapping>(
				`/organizations/${organizationId}/integrations/quickbooks/connections/${connectionId}/mappings`,
				{ method: "PUT", body: input },
			);
		},
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: quickBooksKey(organizationId) });
			queryClient.invalidateQueries({
				queryKey: [...quickBooksKey(organizationId), connectionId, "mapping-validation"],
			});
		},
	});
}

export function useQuickBooksMappingValidation(
	organizationId: string,
	connectionId: string | null,
	enabled: boolean,
) {
	return useQuery({
		queryKey: [...quickBooksKey(organizationId), connectionId, "mapping-validation"],
		queryFn: () => {
			if (!connectionId) throw new Error("QuickBooks is not connected.");
			return apiFetch<OwnerQuickBooksMappingValidation[]>(
				`/organizations/${organizationId}/integrations/quickbooks/connections/${connectionId}/mappings/validation`,
			);
		},
		enabled: enabled && !!connectionId,
	});
}
