import { useQuery } from "@tanstack/react-query";
import { apiFetch, apiFetchBlob } from "../../lib/apiClient";
import type {
	CampaignRevenue,
	FeeCollectionsReport,
	PlatformReport,
	ProductPerformance,
	RefundsReport,
	RevenueReport,
} from "./types";

export interface ReportRange {
	from?: string;
	to?: string;
}

function rangeQuery(range: ReportRange) {
	const params = new URLSearchParams();
	if (range.from) params.set("from", range.from);
	if (range.to) params.set("to", range.to);
	return params.size > 0 ? `?${params.toString()}` : "";
}

export function useRevenueReport(organizationId: string, range: ReportRange) {
	return useQuery({
		queryKey: ["organizations", organizationId, "reports", "revenue", range],
		queryFn: () => apiFetch<RevenueReport>(`/organizations/${organizationId}/reports/revenue${rangeQuery(range)}`),
		enabled: !!organizationId,
	});
}

export function useCampaignReport(organizationId: string, range: ReportRange) {
	return useQuery({
		queryKey: ["organizations", organizationId, "reports", "campaigns", range],
		queryFn: () => apiFetch<CampaignRevenue[]>(`/organizations/${organizationId}/reports/campaigns${rangeQuery(range)}`),
		enabled: !!organizationId,
	});
}

export function useProductReport(organizationId: string, range: ReportRange) {
	return useQuery({
		queryKey: ["organizations", organizationId, "reports", "products", range],
		queryFn: () => apiFetch<ProductPerformance[]>(`/organizations/${organizationId}/reports/products${rangeQuery(range)}`),
		enabled: !!organizationId,
	});
}

export function useRefundsReport(organizationId: string, range: ReportRange) {
	return useQuery({
		queryKey: ["organizations", organizationId, "reports", "refunds", range],
		queryFn: () => apiFetch<RefundsReport>(`/organizations/${organizationId}/reports/refunds${rangeQuery(range)}`),
		enabled: !!organizationId,
	});
}

export function useFeeCollectionsReport(organizationId: string, range: ReportRange) {
	return useQuery({
		queryKey: ["organizations", organizationId, "reports", "fee-collections", range],
		queryFn: () => apiFetch<FeeCollectionsReport>(`/organizations/${organizationId}/reports/fee-collections${rangeQuery(range)}`),
		enabled: !!organizationId,
	});
}

export function exportRevenueReport(organizationId: string, range: ReportRange) {
	return apiFetchBlob(`/organizations/${organizationId}/reports/revenue/export${rangeQuery(range)}`);
}

export function usePlatformReport(range: ReportRange) {
	return useQuery({
		queryKey: ["platform", "reports", "overview", range],
		queryFn: () => apiFetch<PlatformReport>(`/platform/reports/overview${rangeQuery(range)}`),
	});
}
