import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiFetch } from "../../lib/apiClient";
import type {
	CreateOfflineContributionInput,
	CreateOfflineOrderInput,
	CreateOfflineSponsorshipInput,
	OfflineFinancialRecord,
	OfflineFinancialRecordPage,
	OfflineFinancialRecordType,
	OfflinePaymentMethod,
	OfflineVerificationStatus,
} from "./types";

const recordsKey = (organizationId: string) => ["organizations", organizationId, "offline-financial-records"] as const;

export interface OfflineFinancialRecordListOptions {
	query?: string;
	paymentMethod?: OfflinePaymentMethod | "";
	sort?: "newest" | "oldest";
	page?: number;
	size?: number;
}

export function useOfflineFinancialRecords(
	organizationId: string,
	verificationStatus: OfflineVerificationStatus | "" = "",
	recordType: OfflineFinancialRecordType | "" = "",
	options: OfflineFinancialRecordListOptions = {},
) {
	const params = new URLSearchParams({
		page: String(options.page ?? 0),
		size: String(options.size ?? 25),
		sort: options.sort ?? "newest",
	});
	if (options.query?.trim()) params.set("q", options.query.trim());
	if (verificationStatus) params.set("verificationStatus", verificationStatus);
	if (recordType) params.set("recordType", recordType);
	if (options.paymentMethod) params.set("paymentMethod", options.paymentMethod);
	return useQuery({
		queryKey: [...recordsKey(organizationId), verificationStatus, recordType, options] as const,
		queryFn: () => apiFetch<OfflineFinancialRecordPage>(`/organizations/${organizationId}/offline-financial-records?${params}`),
		enabled: !!organizationId,
	});
}
function useCreateRecord<TInput>(organizationId: string, suffix: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (input: TInput) => apiFetch<OfflineFinancialRecord>(`/organizations/${organizationId}/offline-financial-records/${suffix}`, { method: "POST", body: input }),
		onSuccess: () => {
			void queryClient.invalidateQueries({ queryKey: recordsKey(organizationId) });
			void queryClient.invalidateQueries({ queryKey: ["me", "action-center"] });
		},
	});
}
export function useCreateOfflineContribution(organizationId: string) {
	return useCreateRecord<CreateOfflineContributionInput>(organizationId, "contributions");
}

export function useCreateOfflineSponsorship(organizationId: string) {
	return useCreateRecord<CreateOfflineSponsorshipInput>(organizationId, "sponsorships");
}

export function useCreateOfflineOrder(organizationId: string) {
	return useCreateRecord<CreateOfflineOrderInput>(organizationId, "orders");
}
export function useVerifyOfflineFinancialRecord(organizationId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (recordId: string) => apiFetch<OfflineFinancialRecord>(`/organizations/${organizationId}/offline-financial-records/${recordId}/verify`, { method: "POST" }),
		onSuccess: () => {
			void queryClient.invalidateQueries({ queryKey: recordsKey(organizationId) });
			void queryClient.invalidateQueries({ queryKey: ["me", "action-center"] });
			void queryClient.invalidateQueries({ queryKey: ["organizations", organizationId, "campaigns"] });
			void queryClient.invalidateQueries({ queryKey: ["organizations", organizationId, "sponsorship-packages"] });
			void queryClient.invalidateQueries({ queryKey: ["organizations", organizationId, "stores"] });
		},
	});
}
