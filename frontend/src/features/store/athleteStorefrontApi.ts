import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiFetch } from "../../lib/apiClient";
import type { CreateAthleteStorefrontFormValues } from "./schema";
import type { OrderCheckout, OrderStatusResult, PersonalizationPlacement, SwagLogoSize } from "./types";

export type AthleteStorefrontStatus = "DRAFT" | "PUBLISHED" | "ARCHIVED";

export interface AthleteStorefront {
	id: string;
	organizationId: string;
	participantId: string;
	teamId: string | null;
	storeId: string;
	slug: string;
	status: AthleteStorefrontStatus;
	publishedAt: string | null;
	createdAt: string;
	updatedAt: string;
}

export interface AthleteStorefrontPage {
	items: AthleteStorefront[];
	page: number;
	size: number;
	totalElements: number;
}

/** Matches the backend's `ShareLinkQrResponse` — a ready-to-render data URI, nothing persisted (no click-through tracking). */
export interface AthleteStorefrontShareLink {
	qrDataUri: string;
}

const athleteStorefrontsKey = (organizationId: string) => ["organizations", organizationId, "athlete-storefronts"] as const;

export function useAthleteStorefronts(organizationId: string) {
	return useQuery({
		queryKey: athleteStorefrontsKey(organizationId),
		queryFn: () => apiFetch<AthleteStorefrontPage>(`/organizations/${organizationId}/athlete-storefronts?size=100`),
		enabled: !!organizationId,
	});
}

export function useAthleteStorefrontProductIds(organizationId: string, storefrontId: string | null) {
	return useQuery({
		queryKey: [...athleteStorefrontsKey(organizationId), storefrontId, "products"] as const,
		queryFn: () => apiFetch<string[]>(`/organizations/${organizationId}/athlete-storefronts/${storefrontId}/products`),
		enabled: !!organizationId && !!storefrontId,
	});
}

export function useCreateAthleteStorefront(organizationId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (values: CreateAthleteStorefrontFormValues) =>
			apiFetch<AthleteStorefront>(`/organizations/${organizationId}/athlete-storefronts`, {
				method: "POST",
				body: {
					participantId: values.participantId,
					teamId: values.teamId || null,
					storeId: values.storeId,
					productIds: values.productIds,
					slug: values.slug,
				},
			}),
		onSuccess: () => queryClient.invalidateQueries({ queryKey: athleteStorefrontsKey(organizationId) }),
	});
}

export function useUpdateAthleteStorefrontProducts(organizationId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: ({ storefrontId, productIds }: { storefrontId: string; productIds: string[] }) =>
			apiFetch<AthleteStorefront>(`/organizations/${organizationId}/athlete-storefronts/${storefrontId}/products`, {
				method: "PUT",
				body: { productIds },
			}),
		onSuccess: () => queryClient.invalidateQueries({ queryKey: athleteStorefrontsKey(organizationId) }),
	});
}

function useAthleteStorefrontTransition(organizationId: string, action: "publish" | "unpublish" | "archive") {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (storefrontId: string) =>
			apiFetch<AthleteStorefront>(`/organizations/${organizationId}/athlete-storefronts/${storefrontId}/${action}`, { method: "PATCH" }),
		onSuccess: () => queryClient.invalidateQueries({ queryKey: athleteStorefrontsKey(organizationId) }),
	});
}

export function usePublishAthleteStorefront(organizationId: string) {
	return useAthleteStorefrontTransition(organizationId, "publish");
}

export function useUnpublishAthleteStorefront(organizationId: string) {
	return useAthleteStorefrontTransition(organizationId, "unpublish");
}

export function useArchiveAthleteStorefront(organizationId: string) {
	return useAthleteStorefrontTransition(organizationId, "archive");
}

/** A shareable QR code + plain URL for an org admin to hand to a family or print for a team. Nothing persisted, no click-through tracking — same pattern as `useShareLinkQrCode` in features/sponsorship/api.ts, but the backend endpoint here is POST (it also validates the URL), not GET. */
export function useBuildAthleteStorefrontShareLink(organizationId: string) {
	return useMutation({
		mutationFn: (url: string) =>
			apiFetch<AthleteStorefrontShareLink>(`/organizations/${organizationId}/athlete-storefronts/share-link-qr`, {
				method: "POST",
				body: { url },
			}),
	});
}

// ---- Public, unauthenticated buyer flow (Phase 24 slice 24.3) ----

/** Deliberately fuller than the generic `PublicProductVariant` (mockup + print-area + logo preview) so the 24.2 `SwagPersonalizationPreview` component works unmodified on a public storefront page. */
export interface AthleteStorefrontProductVariantPublic {
	id: string;
	label: string;
	priceMinor: number;
	currency: string;
	printAreaWidthPx: number | null;
	printAreaHeightPx: number | null;
	backPrintAreaWidthPx: number | null;
	backPrintAreaHeightPx: number | null;
	mockupFrontUrl: string | null;
	mockupBackUrl: string | null;
}

export interface AthleteStorefrontProductPublic {
	id: string;
	name: string;
	description: string | null;
	hasSwagLogo: boolean;
	/** Short-lived signed preview of the snapshotted logo, for the buyer's live-preview overlay. Null when no logo is assigned. */
	logoPreviewUrl: string | null;
	variants: AthleteStorefrontProductVariantPublic[];
}

/** The real `Participant.lastName` is never sent to the browser — `athletePublicLabel` is server-computed as first name + last initial only. */
export interface AthleteStorefrontPublic {
	slug: string;
	organizationName: string;
	teamName: string | null;
	athletePublicLabel: string;
	products: AthleteStorefrontProductPublic[];
}

export interface CreateAthleteStorefrontOrderRequest {
	productVariantId: string;
	personalizationName?: string | null;
	personalizationNumber?: string | null;
	personalizationPlacement?: PersonalizationPlacement | null;
	personalizationLogoSize?: SwagLogoSize | null;
	supporterName?: string | null;
	supporterEmail?: string | null;
}

export function usePublicAthleteStorefront(slug: string) {
	return useQuery({
		queryKey: ["public", "athlete-storefronts", slug] as const,
		queryFn: () => apiFetch<AthleteStorefrontPublic>(`/public/athlete-storefronts/${slug}`),
		enabled: !!slug,
		retry: false,
	});
}

export function useCreateAthleteStorefrontOrderCheckout(slug: string) {
	return useMutation({
		mutationFn: (request: CreateAthleteStorefrontOrderRequest) =>
			apiFetch<OrderCheckout>(`/public/athlete-storefronts/${slug}/orders`, { method: "POST", body: request }),
	});
}

/** Confirmation is authoritative via the Stripe webhook, not this poll — see OrderService.confirmFromWebhook. Same pattern as useOrderStatus/useSponsorshipStatus. */
export function useAthleteStorefrontOrderStatus(slug: string, orderId: string | null) {
	return useQuery({
		queryKey: ["public", "athlete-storefronts", slug, "orders", orderId] as const,
		queryFn: () => apiFetch<OrderStatusResult>(`/public/athlete-storefronts/${slug}/orders/${orderId}`),
		enabled: !!slug && !!orderId,
		refetchInterval: (query) => (query.state.data?.status === "PENDING" ? 2000 : false),
	});
}
