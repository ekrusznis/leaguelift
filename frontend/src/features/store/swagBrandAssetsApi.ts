import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiFetch } from "../../lib/apiClient";
import type { Product, ProductPage } from "./types";

export type SwagBrandAssetCategory =
	| "PRIMARY"
	| "ALTERNATE"
	| "LIGHT"
	| "DARK"
	| "WORDMARK"
	| "MASCOT"
	| "SMALL_PRINT"
	| "WIDE"
	| "SEASONAL"
	| "COMMEMORATIVE";

export interface SwagBrandAsset {
	id: string;
	organizationId: string;
	teamId: string | null;
	mediaAssetId: string;
	name: string;
	category: SwagBrandAssetCategory;
	status: "ACTIVE" | "ARCHIVED";
	previewUrl: string;
	contentType: string;
	widthPx: number | null;
	heightPx: number | null;
	createdAt: string;
	updatedAt: string;
}

export type ManagedProduct = Product & {
	swagBrandAssetId: string | null;
	hasSwagBrandAsset: boolean;
};

type ManagedProductPage = Omit<ProductPage, "items"> & { items: ManagedProduct[] };
const productsKey = (organizationId: string, storeId: string) => ["organizations", organizationId, "stores", storeId, "products"] as const;
const brandAssetsOrganizationKey = (organizationId: string) => ["organizations", organizationId, "swag-brand-assets"] as const;
const brandAssetsKey = (organizationId: string, teamId: string | null) => [...brandAssetsOrganizationKey(organizationId), teamId] as const;

export function useManagedProducts(organizationId: string, storeId: string, includeArchived: boolean) {
	return useQuery({
		queryKey: [...productsKey(organizationId, storeId), includeArchived] as const,
		queryFn: () => apiFetch<ManagedProductPage>(`/organizations/${organizationId}/stores/${storeId}/products?includeArchived=${includeArchived}`),
		enabled: !!organizationId && !!storeId,
	});
}

export function useSwagBrandAssets(organizationId: string, teamId: string | null, includeArchived = false) {
	const teamQuery = teamId ? `&teamId=${encodeURIComponent(teamId)}` : "";
	return useQuery({
		queryKey: [...brandAssetsKey(organizationId, teamId), includeArchived] as const,
		queryFn: () => apiFetch<SwagBrandAsset[]>(`/organizations/${organizationId}/swag-brand-assets?includeArchived=${includeArchived}${teamQuery}`),
		enabled: !!organizationId,
	});
}

export function useCreateSwagBrandAsset(organizationId: string, teamId: string | null) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (values: { mediaAssetId: string; name: string; category: SwagBrandAssetCategory }) =>
			apiFetch<SwagBrandAsset>(`/organizations/${organizationId}/swag-brand-assets`, {
				method: "POST",
				body: { ...values, teamId },
			}),
		onSuccess: () => queryClient.invalidateQueries({ queryKey: brandAssetsOrganizationKey(organizationId) }),
	});
}

export function useArchiveSwagBrandAsset(organizationId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (assetId: string) => apiFetch<SwagBrandAsset>(`/organizations/${organizationId}/swag-brand-assets/${assetId}/archive`, { method: "PATCH" }),
		onSuccess: () => queryClient.invalidateQueries({ queryKey: brandAssetsOrganizationKey(organizationId) }),
	});
}

export function useRestoreSwagBrandAsset(organizationId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (assetId: string) => apiFetch<SwagBrandAsset>(`/organizations/${organizationId}/swag-brand-assets/${assetId}/restore`, { method: "PATCH" }),
		onSuccess: () => queryClient.invalidateQueries({ queryKey: brandAssetsOrganizationKey(organizationId) }),
	});
}

export function useAssignProductBrandAsset(organizationId: string, storeId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: ({ productId, brandAssetId }: { productId: string; brandAssetId: string }) =>
			apiFetch<ManagedProduct>(`/organizations/${organizationId}/products/${productId}/use-brand-asset`, {
				method: "POST",
				body: { brandAssetId },
			}),
		onSuccess: () => queryClient.invalidateQueries({ queryKey: productsKey(organizationId, storeId) }),
	});
}
