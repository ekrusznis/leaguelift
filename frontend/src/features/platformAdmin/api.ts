import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiFetch } from "../../lib/apiClient";
import { clearStoredSupportAccess, storeSupportAccess } from "./supportAccessStorage";
import type {
	OutboxEvent,
	PageResponse,
	PlatformAthleteListItem,
	PlatformCoachListItem,
	PlatformOrganizationDetail,
	PlatformOrganizationListItem,
	PlatformPaymentListItem,
	PlatformSupportAccess,
	PlatformSupportAccessListItem,
	PlatformSwagShopProductListItem,
	PlatformUserListItem,
} from "./types";

export interface PlatformListFilters {
	query?: string;
	status?: string;
	page?: number;
	size?: number;
}

export interface PlatformSwagShopListFilters extends PlatformListFilters {
	organizationId?: string;
}

function listQuery(filters: PlatformListFilters) {
	const params = new URLSearchParams();
	if (filters.query?.trim()) params.set("query", filters.query.trim());
	if (filters.status) params.set("status", filters.status);
	params.set("page", String(filters.page ?? 0));
	params.set("size", String(filters.size ?? 25));
	return params.toString();
}

export function usePlatformSwagShopProducts(filters: PlatformSwagShopListFilters) {
	const params = new URLSearchParams(listQuery(filters));
	if (filters.organizationId) params.set("organizationId", filters.organizationId);
	return useQuery({
		queryKey: ["platform", "admin", "swag-shop", "products", filters],
		queryFn: () => apiFetch<PageResponse<PlatformSwagShopProductListItem>>(`/platform/admin/swag-shop/products?${params.toString()}`),
	});
}

export interface PlatformAthleteListFilters extends PlatformListFilters {
	organizationId?: string;
	teamId?: string;
	householdId?: string;
	eligibilityStatus?: string;
}

export function usePlatformAthletes(filters: PlatformAthleteListFilters) {
	const params = new URLSearchParams(listQuery(filters));
	if (filters.organizationId) params.set("organizationId", filters.organizationId);
	if (filters.teamId) params.set("teamId", filters.teamId);
	if (filters.householdId) params.set("householdId", filters.householdId);
	if (filters.eligibilityStatus) params.set("eligibilityStatus", filters.eligibilityStatus);
	return useQuery({
		queryKey: ["platform", "admin", "athletes", filters],
		queryFn: () => apiFetch<PageResponse<PlatformAthleteListItem>>(`/platform/admin/athletes?${params.toString()}`),
	});
}

export interface PlatformCoachListFilters extends PlatformListFilters {
	organizationId?: string;
	teamId?: string;
}

export function usePlatformCoaches(filters: PlatformCoachListFilters) {
	const params = new URLSearchParams(listQuery(filters));
	if (filters.organizationId) params.set("organizationId", filters.organizationId);
	if (filters.teamId) params.set("teamId", filters.teamId);
	return useQuery({
		queryKey: ["platform", "admin", "coaches", filters],
		queryFn: () => apiFetch<PageResponse<PlatformCoachListItem>>(`/platform/admin/coaches?${params.toString()}`),
	});
}

export function usePlatformAdminOrganizations(filters: PlatformListFilters) {
	return useQuery({
		queryKey: ["platform", "admin", "organizations", filters],
		queryFn: () => apiFetch<PageResponse<PlatformOrganizationListItem>>(`/platform/admin/organizations?${listQuery(filters)}`),
	});
}

export interface PlatformPaymentListFilters extends PlatformListFilters {
	type?: string;
	organizationId?: string;
	teamId?: string;
	dateFrom?: string;
	dateTo?: string;
}

export function usePlatformPayments(filters: PlatformPaymentListFilters) {
	const params = new URLSearchParams(listQuery(filters));
	if (filters.type) params.set("type", filters.type);
	if (filters.organizationId) params.set("organizationId", filters.organizationId);
	if (filters.teamId) params.set("teamId", filters.teamId);
	if (filters.dateFrom) params.set("dateFrom", filters.dateFrom);
	if (filters.dateTo) params.set("dateTo", filters.dateTo);
	return useQuery({
		queryKey: ["platform", "admin", "payments", filters],
		queryFn: () => apiFetch<PageResponse<PlatformPaymentListItem>>(`/platform/admin/payments?${params.toString()}`),
	});
}

/** Refund/void a payment by calling its own domain's existing org-scoped endpoint directly — no new refund/void logic, this just routes to whichever one applies. */
export function useRefundOrVoidPayment() {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: async (payment: PlatformPaymentListItem) => {
			switch (payment.type) {
				case "ORDER":
					return apiFetch(`/organizations/${payment.organizationId}/orders/${payment.id}/refund`, { method: "POST" });
				case "CONTRIBUTION":
					return apiFetch(`/organizations/${payment.organizationId}/campaigns/${payment.parentId}/contributions/${payment.id}/refund`, { method: "POST" });
				case "SPONSORSHIP":
					return apiFetch(`/organizations/${payment.organizationId}/sponsorships/${payment.id}/refund`, { method: "POST" });
				case "FEE":
					return apiFetch(`/organizations/${payment.organizationId}/fee-assignments/${payment.parentId}/payments/${payment.id}`, {
						method: "DELETE",
						body: { reason: "Voided from Platform Admin Payments" },
					});
			}
		},
		onSuccess: () => queryClient.invalidateQueries({ queryKey: ["platform", "admin", "payments"] }),
	});
}

export function usePlatformAdminOrganization(organizationId: string | undefined) {
	return useQuery({
		queryKey: ["platform", "admin", "organizations", organizationId],
		queryFn: () => apiFetch<PlatformOrganizationDetail>(`/platform/admin/organizations/${organizationId}`),
		enabled: !!organizationId,
	});
}

export function usePlatformAdminUsers(filters: PlatformListFilters) {
	return useQuery({
		queryKey: ["platform", "admin", "users", filters],
		queryFn: () => apiFetch<PageResponse<PlatformUserListItem>>(`/platform/admin/users?${listQuery(filters)}`),
	});
}

export function usePlatformSupportAccessList(filters: Pick<PlatformListFilters, "status" | "page" | "size">) {
	return useQuery({
		queryKey: ["platform", "admin", "support-access", "list", filters],
		queryFn: () => apiFetch<PageResponse<PlatformSupportAccessListItem>>(`/platform/admin/support-access?${listQuery(filters)}`),
	});
}

export function useCurrentSupportAccess(enabled = true) {
	return useQuery({
		queryKey: ["platform", "admin", "support-access", "current"],
		enabled,
		queryFn: async () => {
			const access = await apiFetch<PlatformSupportAccess | null>("/platform/admin/support-access/current");
			if (access) storeSupportAccess(access);
			else clearStoredSupportAccess();
			return access;
		},
	});
}

export function useStartSupportAccess(organizationId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (reason: string) => apiFetch<PlatformSupportAccess>(`/platform/admin/organizations/${organizationId}/support-access`, {
			method: "POST",
			body: { reason },
		}),
		onSuccess: (access) => {
			storeSupportAccess(access);
			// A new organization scope must not reuse cached customer data from the
			// previous session. Platform directory queries will refetch as needed.
			queryClient.removeQueries({ predicate: () => true });
			queryClient.setQueryData(["platform", "admin", "support-access", "current"], access);
		},
	});
}

export function useEndSupportAccess() {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (accessId: string) => apiFetch<void>(`/platform/admin/support-access/${accessId}`, { method: "DELETE" }),
		onSuccess: () => {
			clearStoredSupportAccess();
			// Remove all customer data cached during the support session. The employee
			// stays signed in, but must start a new reasoned session before refetching it.
			queryClient.removeQueries({ predicate: () => true });
			queryClient.setQueryData(["platform", "admin", "support-access", "current"], null);
		},
	});
}

export function useFailedOutboxEvents() {
	return useQuery({
		queryKey: ["platform", "admin", "outbox", "failed"],
		queryFn: () => apiFetch<OutboxEvent[]>("/admin/outbox-events/failed"),
	});
}

export function useDeadLetterOutboxEvents() {
	return useQuery({
		queryKey: ["platform", "admin", "outbox", "dead-letter"],
		queryFn: () => apiFetch<OutboxEvent[]>("/admin/outbox-events/dead-letter"),
	});
}

export function useReprocessOutboxEvent() {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (eventId: string) => apiFetch<void>(`/admin/outbox-events/${eventId}/reprocess`, { method: "POST" }),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: ["platform", "admin", "outbox"] });
			queryClient.invalidateQueries({ queryKey: ["platform", "dashboard", "outbox-health"] });
		},
	});
}
