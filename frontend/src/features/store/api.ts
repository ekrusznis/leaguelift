import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiFetch } from "../../lib/apiClient";
import type {
	CreateProductFormValues,
	CreateProductVariantFormValues,
	CreateStoreFormValues,
	ManualProductVariantFormValues,
	ManualVendorFormValues,
} from "./schema";
import type {
	CartLine,
	EligiblePrintProvider,
	Fulfillment,
	FulfillmentHistory,
	FulfillmentReprint,
	FulfillmentReprintStatus,
	FulfillmentStatus,
	ManualVendor,
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
const vendorsKey = (organizationId: string) => ["organizations", organizationId, "manual-vendors"] as const;
const productsKey = (organizationId: string, storeId: string) => ["organizations", organizationId, "stores", storeId, "products"] as const;
const variantsKey = (organizationId: string, productId: string) => ["organizations", organizationId, "products", productId, "variants"] as const;
const ordersKey = (organizationId: string, storeId: string) => ["organizations", organizationId, "stores", storeId, "orders"] as const;
const fulfillmentKey = (organizationId: string, orderId: string | null) => ["organizations", organizationId, "orders", orderId, "fulfillment"] as const;

export function useStores(organizationId: string) {
	return useQuery({ queryKey: storesKey(organizationId), queryFn: () => apiFetch<StorePage>(`/organizations/${organizationId}/stores`), enabled: !!organizationId });
}

export function useCreateStore(organizationId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (values: CreateStoreFormValues) => apiFetch<Store>(`/organizations/${organizationId}/stores`, { method: "POST", body: { ...values, teamId: values.teamId || null } }),
		onSuccess: () => queryClient.invalidateQueries({ queryKey: storesKey(organizationId) }),
	});
}

export function useUpdateStoreStatus(organizationId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: ({ storeId, status }: { storeId: string; status: string }) => apiFetch<Store>(`/organizations/${organizationId}/stores/${storeId}/status`, { method: "PATCH", body: { status } }),
		onSuccess: () => queryClient.invalidateQueries({ queryKey: storesKey(organizationId) }),
	});
}

export function useManualVendors(organizationId: string, includeArchived = false) {
	return useQuery({
		queryKey: [...vendorsKey(organizationId), includeArchived] as const,
		queryFn: () => apiFetch<ManualVendor[]>(`/organizations/${organizationId}/manual-vendors?includeArchived=${includeArchived}`),
		enabled: !!organizationId,
	});
}

function vendorBody(values: ManualVendorFormValues) {
	return {
		name: values.name,
		contactName: values.contactName || null,
		contactEmail: values.contactEmail || null,
		phone: values.phone || null,
		websiteUrl: values.websiteUrl || null,
		notes: values.notes || null,
	};
}

export function useCreateManualVendor(organizationId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (values: ManualVendorFormValues) => apiFetch<ManualVendor>(`/organizations/${organizationId}/manual-vendors`, { method: "POST", body: vendorBody(values) }),
		onSuccess: () => queryClient.invalidateQueries({ queryKey: vendorsKey(organizationId) }),
	});
}

export function useUpdateManualVendor(organizationId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: ({ vendorId, values }: { vendorId: string; values: ManualVendorFormValues }) => apiFetch<ManualVendor>(`/organizations/${organizationId}/manual-vendors/${vendorId}`, { method: "PUT", body: vendorBody(values) }),
		onSuccess: () => queryClient.invalidateQueries({ queryKey: vendorsKey(organizationId) }),
	});
}

export function useArchiveManualVendor(organizationId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (vendorId: string) => apiFetch<ManualVendor>(`/organizations/${organizationId}/manual-vendors/${vendorId}/archive`, { method: "PATCH" }),
		onSuccess: () => queryClient.invalidateQueries({ queryKey: vendorsKey(organizationId) }),
	});
}

export function usePrintifyBlueprints(organizationId: string, enabled = true) {
	return useQuery({ queryKey: ["organizations", organizationId, "printify", "blueprints"] as const, queryFn: () => apiFetch<PrintifyBlueprint[]>(`/organizations/${organizationId}/printify/blueprints`), enabled: !!organizationId && enabled });
}

export function usePrintifyPrintProviders(organizationId: string, blueprintId: number | null) {
	return useQuery({
		queryKey: ["organizations", organizationId, "printify", "blueprints", blueprintId, "print-providers"] as const,
		queryFn: () => apiFetch<EligiblePrintProvider[]>(`/organizations/${organizationId}/printify/blueprints/${blueprintId}/print-providers`),
		enabled: !!organizationId && blueprintId != null,
	});
}

export function usePrintifyCatalogVariants(organizationId: string, blueprintId: number | null, printProviderId: number | null) {
	return useQuery({
		queryKey: ["organizations", organizationId, "printify", "blueprints", blueprintId, "print-providers", printProviderId, "variants"] as const,
		queryFn: () => apiFetch<PrintifyCatalogVariant[]>(`/organizations/${organizationId}/printify/blueprints/${blueprintId}/print-providers/${printProviderId}/variants`),
		enabled: !!organizationId && blueprintId != null && printProviderId != null,
	});
}

export function useProducts(organizationId: string, storeId: string) {
	return useQuery({ queryKey: productsKey(organizationId, storeId), queryFn: () => apiFetch<ProductPage>(`/organizations/${organizationId}/stores/${storeId}/products`), enabled: !!organizationId && !!storeId });
}

export function useCreateProduct(organizationId: string, storeId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (values: CreateProductFormValues) => apiFetch<Product>(`/organizations/${organizationId}/stores/${storeId}/products`, {
			method: "POST",
			body: {
				name: values.name,
				description: values.description || null,
				catalogSource: values.catalogSource,
				manualVendorId: values.catalogSource === "MANUAL" ? values.manualVendorId || null : null,
				printifyBlueprintId: values.catalogSource === "PRINTIFY" ? values.printifyBlueprintId : null,
				printifyPrintPosition: values.printifyPrintPosition,
			},
		}),
		onSuccess: () => queryClient.invalidateQueries({ queryKey: productsKey(organizationId, storeId) }),
	});
}

export function useUpdateProductStatus(organizationId: string, storeId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: ({ productId, status }: { productId: string; status: string }) => apiFetch<Product>(`/organizations/${organizationId}/products/${productId}/status`, { method: "PATCH", body: { status } }),
		onSuccess: () => queryClient.invalidateQueries({ queryKey: productsKey(organizationId, storeId) }),
	});
}

export function useAssignProductDesign(organizationId: string, storeId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: ({ productId, assetId, altText }: { productId: string; assetId: string; altText?: string }) => apiFetch<MediaAssignmentDescriptor>(`/organizations/${organizationId}/products/${productId}/design`, { method: "PUT", body: { assetId, altText: altText ?? null } }),
		onSuccess: () => queryClient.invalidateQueries({ queryKey: productsKey(organizationId, storeId) }),
	});
}

export function useVariants(organizationId: string, productId: string) {
	return useQuery({ queryKey: variantsKey(organizationId, productId), queryFn: () => apiFetch<ProductVariant[]>(`/organizations/${organizationId}/products/${productId}/variants`), enabled: !!organizationId && !!productId });
}

export function useCreateProductVariant(organizationId: string, productId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (values: CreateProductVariantFormValues) => apiFetch<ProductVariant>(`/organizations/${organizationId}/products/${productId}/variants`, { method: "POST", body: values }),
		onSuccess: () => queryClient.invalidateQueries({ queryKey: variantsKey(organizationId, productId) }),
	});
}

export function useCreateManualProductVariant(organizationId: string, productId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (values: ManualProductVariantFormValues) => apiFetch<ProductVariant>(`/organizations/${organizationId}/products/${productId}/manual-variants`, {
			method: "POST",
			body: { ...values, sku: values.sku || null, size: values.size || null, color: values.color || null },
		}),
		onSuccess: () => queryClient.invalidateQueries({ queryKey: variantsKey(organizationId, productId) }),
	});
}

export function useUpdateManualProductVariant(organizationId: string, productId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: ({ variantId, values }: { variantId: string; values: ManualProductVariantFormValues }) => apiFetch<ProductVariant>(`/organizations/${organizationId}/products/${productId}/manual-variants/${variantId}`, {
			method: "PUT",
			body: { ...values, sku: values.sku || null, size: values.size || null, color: values.color || null },
		}),
		onSuccess: () => queryClient.invalidateQueries({ queryKey: variantsKey(organizationId, productId) }),
	});
}

export function useUpdateProductVariantStatus(organizationId: string, productId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: ({ variantId, isActive }: { variantId: string; isActive: boolean }) => apiFetch<ProductVariant>(`/organizations/${organizationId}/products/${productId}/variants/${variantId}/status`, { method: "PATCH", body: { isActive } }),
		onSuccess: () => queryClient.invalidateQueries({ queryKey: variantsKey(organizationId, productId) }),
	});
}

export function useOrders(organizationId: string, storeId: string) {
	return useQuery({ queryKey: ordersKey(organizationId, storeId), queryFn: () => apiFetch<OrderPage>(`/organizations/${organizationId}/stores/${storeId}/orders`), enabled: !!organizationId && !!storeId });
}

export function useOrderFulfillment(organizationId: string, orderId: string | null) {
	return useQuery({ queryKey: fulfillmentKey(organizationId, orderId), queryFn: () => apiFetch<Fulfillment | null>(`/organizations/${organizationId}/orders/${orderId}/fulfillment`), enabled: !!organizationId && !!orderId });
}

export function useFulfillmentHistory(organizationId: string, orderId: string | null) {
	return useQuery({ queryKey: [...fulfillmentKey(organizationId, orderId), "history"] as const, queryFn: () => apiFetch<FulfillmentHistory[]>(`/organizations/${organizationId}/orders/${orderId}/fulfillment/history`), enabled: !!organizationId && !!orderId });
}

export function useUpdateFulfillment(organizationId: string, storeId: string, orderId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (values: {
			status: FulfillmentStatus;
			manualVendorId: string | null;
			vendorOrderReference: string | null;
			carrier: string | null;
			trackingNumber: string | null;
			trackingUrl: string | null;
			internalNotes: string | null;
			attentionReason: string | null;
			note: string;
		}) => apiFetch<Fulfillment>(`/organizations/${organizationId}/orders/${orderId}/fulfillment`, { method: "PUT", body: values }),
		onSuccess: async () => {
			await queryClient.invalidateQueries({ queryKey: fulfillmentKey(organizationId, orderId) });
			await queryClient.invalidateQueries({ queryKey: ordersKey(organizationId, storeId) });
			await queryClient.invalidateQueries({ queryKey: ["me", "action-center"] });
		},
	});
}

export function useFulfillmentReprints(organizationId: string, orderId: string | null) {
	return useQuery({ queryKey: [...fulfillmentKey(organizationId, orderId), "reprints"] as const, queryFn: () => apiFetch<FulfillmentReprint[]>(`/organizations/${organizationId}/orders/${orderId}/fulfillment/reprints`), enabled: !!organizationId && !!orderId });
}

export function useCreateFulfillmentReprint(organizationId: string, orderId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (values: { reason: string; vendorOrderReference?: string; internalNotes?: string }) => apiFetch<FulfillmentReprint>(`/organizations/${organizationId}/orders/${orderId}/fulfillment/reprints`, { method: "POST", body: { reason: values.reason, vendorOrderReference: values.vendorOrderReference || null, internalNotes: values.internalNotes || null } }),
		onSuccess: () => queryClient.invalidateQueries({ queryKey: [...fulfillmentKey(organizationId, orderId), "reprints"] }),
	});
}

export function useUpdateFulfillmentReprint(organizationId: string, orderId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: ({ reprintId, values }: { reprintId: string; values: { status: FulfillmentReprintStatus; vendorOrderReference?: string; carrier?: string; trackingNumber?: string; trackingUrl?: string; internalNotes?: string } }) => apiFetch<FulfillmentReprint>(`/organizations/${organizationId}/orders/${orderId}/fulfillment/reprints/${reprintId}`, { method: "PUT", body: { ...values, vendorOrderReference: values.vendorOrderReference || null, carrier: values.carrier || null, trackingNumber: values.trackingNumber || null, trackingUrl: values.trackingUrl || null, internalNotes: values.internalNotes || null } }),
		onSuccess: () => queryClient.invalidateQueries({ queryKey: [...fulfillmentKey(organizationId, orderId), "reprints"] }),
	});
}

export function useRefundOrder(organizationId: string, storeId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (orderId: string) => apiFetch<Order>(`/organizations/${organizationId}/orders/${orderId}/refund`, { method: "POST" }),
		onSuccess: () => queryClient.invalidateQueries({ queryKey: ordersKey(organizationId, storeId) }),
	});
}

export function usePublicStore(slug: string) {
	return useQuery({ queryKey: ["public", "stores", slug] as const, queryFn: () => apiFetch<PublicStore>(`/public/stores/${slug}`), enabled: !!slug, retry: false });
}

export function useCreateOrderCheckout(slug: string) {
	return useMutation({
		mutationFn: (params: { items: CartLine[]; supporterName?: string; supporterEmail?: string; successUrl: string; cancelUrl: string }) => apiFetch<OrderCheckout>(`/public/stores/${slug}/orders`, { method: "POST", body: params }),
	});
}

export function useOrderStatus(slug: string, orderId: string | null) {
	return useQuery({ queryKey: ["public", "stores", slug, "orders", orderId] as const, queryFn: () => apiFetch<OrderStatusResult>(`/public/stores/${slug}/orders/${orderId}`), enabled: !!slug && !!orderId, refetchInterval: (query) => (query.state.data?.status === "PENDING" ? 2000 : false) });
}

export type { Order };
