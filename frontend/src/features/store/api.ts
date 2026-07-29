import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiFetch } from "../../lib/apiClient";
import type { CreateProductFormValues, CreateProductVariantFormValues, CreateStoreFormValues } from "./schema";
import type {
	CartLine,
	EligiblePrintProvider,
	Fulfillment,
	MediaAssignmentDescriptor,
	Order,
	OrderCheckout,
	OrderPage,
	OrderStatusResult,
	Product,
	ProductPage,
	ProductVariant,
	PrintifyBlueprint,
	PrintifyCatalogVariant,
	PublicStore,
	Store,
	StorePage,
} from "./types";

const storesKey = (organizationId: string) => ["organizations", organizationId, "stores"] as const;
const productsKey = (organizationId: string, storeId: string) => ["organizations", organizationId, "stores", storeId, "products"] as const;
const variantsKey = (organizationId: string, productId: string) => ["organizations", organizationId, "products", productId, "variants"] as const;
const ordersKey = (organizationId: string, storeId: string) => ["organizations", organizationId, "stores", storeId, "orders"] as const;

export function useStores(organizationId: string) {
	return useQuery({
		queryKey: storesKey(organizationId),
		queryFn: () => apiFetch<StorePage>(`/organizations/${organizationId}/stores`),
		enabled: !!organizationId,
	});
}

export function useCreateStore(organizationId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (values: CreateStoreFormValues) =>
			apiFetch<Store>(`/organizations/${organizationId}/stores`, {
				method: "POST",
				body: { ...values, teamId: values.teamId || null },
			}),
		onSuccess: () => queryClient.invalidateQueries({ queryKey: storesKey(organizationId) }),
	});
}

export function useUpdateStoreStatus(organizationId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: ({ storeId, status }: { storeId: string; status: string }) =>
			apiFetch<Store>(`/organizations/${organizationId}/stores/${storeId}/status`, { method: "PATCH", body: { status } }),
		onSuccess: () => queryClient.invalidateQueries({ queryKey: storesKey(organizationId) }),
	});
}

export function usePrintifyBlueprints(organizationId: string) {
	return useQuery({
		queryKey: ["organizations", organizationId, "printify", "blueprints"] as const,
		queryFn: () => apiFetch<PrintifyBlueprint[]>(`/organizations/${organizationId}/printify/blueprints`),
		enabled: !!organizationId,
	});
}

export function usePrintifyPrintProviders(organizationId: string, blueprintId: number | null) {
	return useQuery({
		queryKey: ["organizations", organizationId, "printify", "blueprints", blueprintId, "print-providers"] as const,
		queryFn: () =>
			apiFetch<EligiblePrintProvider[]>(`/organizations/${organizationId}/printify/blueprints/${blueprintId}/print-providers`),
		enabled: !!organizationId && blueprintId != null,
	});
}

export function usePrintifyCatalogVariants(organizationId: string, blueprintId: number | null, printProviderId: number | null) {
	return useQuery({
		queryKey: ["organizations", organizationId, "printify", "blueprints", blueprintId, "print-providers", printProviderId, "variants"] as const,
		queryFn: () =>
			apiFetch<PrintifyCatalogVariant[]>(
				`/organizations/${organizationId}/printify/blueprints/${blueprintId}/print-providers/${printProviderId}/variants`,
			),
		enabled: !!organizationId && blueprintId != null && printProviderId != null,
	});
}

export function useProducts(organizationId: string, storeId: string) {
	return useQuery({
		queryKey: productsKey(organizationId, storeId),
		queryFn: () => apiFetch<ProductPage>(`/organizations/${organizationId}/stores/${storeId}/products`),
		enabled: !!organizationId && !!storeId,
	});
}

export function useCreateProduct(organizationId: string, storeId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (values: CreateProductFormValues) =>
			apiFetch<Product>(`/organizations/${organizationId}/stores/${storeId}/products`, {
				method: "POST",
				body: { ...values, description: values.description || null },
			}),
		onSuccess: () => queryClient.invalidateQueries({ queryKey: productsKey(organizationId, storeId) }),
	});
}

export function useUpdateProductStatus(organizationId: string, storeId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: ({ productId, status }: { productId: string; status: string }) =>
			apiFetch<Product>(`/organizations/${organizationId}/products/${productId}/status`, { method: "PATCH", body: { status } }),
		onSuccess: () => queryClient.invalidateQueries({ queryKey: productsKey(organizationId, storeId) }),
	});
}

export function useAssignProductDesign(organizationId: string, storeId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: ({ productId, assetId, altText }: { productId: string; assetId: string; altText?: string }) =>
			apiFetch<MediaAssignmentDescriptor>(`/organizations/${organizationId}/products/${productId}/design`, {
				method: "PUT",
				body: { assetId, altText: altText ?? null },
			}),
		onSuccess: () => queryClient.invalidateQueries({ queryKey: productsKey(organizationId, storeId) }),
	});
}

export function useVariants(organizationId: string, productId: string) {
	return useQuery({
		queryKey: variantsKey(organizationId, productId),
		queryFn: () => apiFetch<ProductVariant[]>(`/organizations/${organizationId}/products/${productId}/variants`),
		enabled: !!organizationId && !!productId,
	});
}

export function useCreateProductVariant(organizationId: string, productId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (values: CreateProductVariantFormValues) =>
			apiFetch<ProductVariant>(`/organizations/${organizationId}/products/${productId}/variants`, {
				method: "POST",
				body: values,
			}),
		onSuccess: () => queryClient.invalidateQueries({ queryKey: variantsKey(organizationId, productId) }),
	});
}

export function useOrders(organizationId: string, storeId: string) {
	return useQuery({
		queryKey: ordersKey(organizationId, storeId),
		queryFn: () => apiFetch<OrderPage>(`/organizations/${organizationId}/stores/${storeId}/orders`),
		enabled: !!organizationId && !!storeId,
	});
}

export function useOrderFulfillment(organizationId: string, orderId: string | null) {
	return useQuery({
		queryKey: ["organizations", organizationId, "orders", orderId, "fulfillment"] as const,
		queryFn: () => apiFetch<Fulfillment | null>(`/organizations/${organizationId}/orders/${orderId}/fulfillment`),
		enabled: !!organizationId && !!orderId,
	});
}

/** Org-admin-initiated, within a 14-day window of confirmation (ADR-017) — the backend enforces both. */
export function useRefundOrder(organizationId: string, storeId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (orderId: string) => apiFetch<Order>(`/organizations/${organizationId}/orders/${orderId}/refund`, { method: "POST" }),
		onSuccess: () => queryClient.invalidateQueries({ queryKey: ordersKey(organizationId, storeId) }),
	});
}

export function usePublicStore(slug: string) {
	return useQuery({
		queryKey: ["public", "stores", slug] as const,
		queryFn: () => apiFetch<PublicStore>(`/public/stores/${slug}`),
		enabled: !!slug,
		retry: false,
	});
}

export function useCreateOrderCheckout(slug: string) {
	return useMutation({
		mutationFn: (params: {
			items: CartLine[];
			supporterName?: string;
			supporterEmail?: string;
			successUrl: string;
			cancelUrl: string;
		}) =>
			apiFetch<OrderCheckout>(`/public/stores/${slug}/orders`, {
				method: "POST",
				body: params,
			}),
	});
}

export function useOrderStatus(slug: string, orderId: string | null) {
	return useQuery({
		queryKey: ["public", "stores", slug, "orders", orderId] as const,
		queryFn: () => apiFetch<OrderStatusResult>(`/public/stores/${slug}/orders/${orderId}`),
		enabled: !!slug && !!orderId,
		refetchInterval: (query) => (query.state.data?.status === "PENDING" ? 2000 : false),
	});
}

export type { Order };
