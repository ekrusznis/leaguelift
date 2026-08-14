import { useQuery } from "@tanstack/react-query";
import { apiFetch } from "../../lib/apiClient";
import type { FulfillmentStatus, Order, OrderStatus } from "./types";

export type OrderSearchSort = "NEWEST" | "OLDEST" | "SUPPORTER_ASC" | "STATUS_ASC" | "FULFILLMENT_ASC";

export interface OrderSearchItem extends Order {
	fulfillmentStatus: FulfillmentStatus | null;
}

export interface OrderSearchPage {
	items: OrderSearchItem[];
	page: number;
	size: number;
	totalElements: number;
}

export interface OrderSearchParams {
	page?: number;
	size?: number;
	q?: string;
	status?: Extract<OrderStatus, "CONFIRMED" | "REFUNDED"> | "";
	paymentSource?: "STRIPE" | "OFFLINE" | "";
	fulfillmentStatus?: FulfillmentStatus | "";
	sort?: OrderSearchSort;
}

export function useOrderSearch(organizationId: string, storeId: string, params: OrderSearchParams) {
	const search = new URLSearchParams({
		page: String(params.page ?? 0),
		size: String(params.size ?? 25),
		sort: params.sort ?? "NEWEST",
	});
	if (params.q?.trim()) search.set("q", params.q.trim());
	if (params.status) search.set("status", params.status);
	if (params.paymentSource) search.set("paymentSource", params.paymentSource);
	if (params.fulfillmentStatus) search.set("fulfillmentStatus", params.fulfillmentStatus);

	return useQuery({
		queryKey: [
			"organizations",
			organizationId,
			"stores",
			storeId,
			"orders",
			"search",
			Object.fromEntries(search.entries()),
		],
		queryFn: () =>
			apiFetch<OrderSearchPage>(
				`/organizations/${organizationId}/stores/${storeId}/orders/search?${search.toString()}`,
			),
		enabled: !!organizationId && !!storeId,
	});
}
