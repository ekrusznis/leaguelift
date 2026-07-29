import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiFetch } from "../../lib/apiClient";
import type { MediaAssignment } from "../media/types";
import type { CreateSponsorshipCheckoutFormValues, CreateSponsorshipPackageFormValues, UpdateSponsorFormValues } from "./schema";
import type {
	PublicSponsorshipPackage,
	ShareLink,
	Sponsor,
	SponsorDirectoryEntry,
	SponsorshipCheckout,
	SponsorshipInvoice,
	SponsorshipPackage,
	SponsorshipPackagePage,
	SponsorshipPage,
	SponsorshipStatusResult,
} from "./types";

const sponsorshipPackagesKey = (organizationId: string) => ["organizations", organizationId, "sponsorship-packages"] as const;
const packageSponsorshipsKey = (organizationId: string, packageId: string) =>
	["organizations", organizationId, "sponsorship-packages", packageId, "sponsorships"] as const;
const pendingReviewKey = (organizationId: string) => ["organizations", organizationId, "sponsorships", "pending-review"] as const;

export function useSponsorshipPackages(organizationId: string) {
	return useQuery({
		queryKey: sponsorshipPackagesKey(organizationId),
		queryFn: () => apiFetch<SponsorshipPackagePage>(`/organizations/${organizationId}/sponsorship-packages`),
		enabled: !!organizationId,
	});
}

export function useCreateSponsorshipPackage(organizationId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (values: CreateSponsorshipPackageFormValues) =>
			apiFetch<SponsorshipPackage>(`/organizations/${organizationId}/sponsorship-packages`, {
				method: "POST",
				body: {
					name: values.name,
					description: values.description || null,
					priceMinor: values.priceMinor,
					maxQuantity: values.maxQuantity === "" || values.maxQuantity == null ? null : values.maxQuantity,
					exclusive: values.exclusive,
					placementStartDate: values.placementStartDate || null,
					placementEndDate: values.placementEndDate || null,
				},
			}),
		onSuccess: () => queryClient.invalidateQueries({ queryKey: sponsorshipPackagesKey(organizationId) }),
	});
}

export function usePublishSponsorshipPackage(organizationId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (packageId: string) =>
			apiFetch<SponsorshipPackage>(`/organizations/${organizationId}/sponsorship-packages/${packageId}/publish`, { method: "POST" }),
		onSuccess: () => queryClient.invalidateQueries({ queryKey: sponsorshipPackagesKey(organizationId) }),
	});
}

export function useUpdateSponsorshipPackageStatus(organizationId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: ({ packageId, status }: { packageId: string; status: string }) =>
			apiFetch<SponsorshipPackage>(`/organizations/${organizationId}/sponsorship-packages/${packageId}/status`, {
				method: "PATCH",
				body: { status },
			}),
		onSuccess: () => queryClient.invalidateQueries({ queryKey: sponsorshipPackagesKey(organizationId) }),
	});
}

export function usePackageSponsorships(organizationId: string, packageId: string) {
	return useQuery({
		queryKey: packageSponsorshipsKey(organizationId, packageId),
		queryFn: () => apiFetch<SponsorshipPage>(`/organizations/${organizationId}/sponsorship-packages/${packageId}/sponsorships`),
		enabled: !!organizationId && !!packageId,
	});
}

/**
 * Assigns/replaces a confirmed sponsor's logo. Admin-side action — there is no
 * public/self-service logo upload during checkout (ADR-018). Pair with
 * `useRequestMediaUpload`/`useConfirmMediaUpload` from `features/media/api.ts` for the
 * upload-request/confirm steps first, exactly like `useAssignProductDesign` does.
 */
export function useAssignSponsorLogo(organizationId: string, packageId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: ({ sponsorId, assetId, altText }: { sponsorId: string; assetId: string; altText?: string }) =>
			apiFetch<MediaAssignment>(`/organizations/${organizationId}/sponsors/${sponsorId}/logo`, {
				method: "PUT",
				body: { assetId, altText: altText ?? null },
			}),
		onSuccess: () => queryClient.invalidateQueries({ queryKey: packageSponsorshipsKey(organizationId, packageId) }),
	});
}

export function usePublicSponsorshipPackages(organizationSlug: string) {
	return useQuery({
		queryKey: ["public", "organizations", organizationSlug, "sponsorship-packages"] as const,
		queryFn: () => apiFetch<PublicSponsorshipPackage[]>(`/public/organizations/${organizationSlug}/sponsorship-packages`),
		enabled: !!organizationSlug,
		retry: false,
	});
}

export function usePublicSponsorDirectory(organizationSlug: string) {
	return useQuery({
		queryKey: ["public", "organizations", organizationSlug, "sponsors"] as const,
		queryFn: () => apiFetch<SponsorDirectoryEntry[]>(`/public/organizations/${organizationSlug}/sponsors`),
		enabled: !!organizationSlug,
		retry: false,
	});
}

/**
 * Creates a Stripe Checkout Session for a sponsorship purchase. The caller is
 * responsible for redirecting the browser to the returned `checkoutUrl` — this hook
 * only talks to our own API, never Stripe directly.
 */
export function useCreateSponsorshipCheckout(packageId: string) {
	return useMutation({
		mutationFn: (values: CreateSponsorshipCheckoutFormValues & { successUrl: string; cancelUrl: string }) =>
			apiFetch<SponsorshipCheckout>(`/public/sponsorship-packages/${packageId}/sponsorships`, {
				method: "POST",
				body: {
					sponsorName: values.sponsorName,
					sponsorContactEmail: values.sponsorContactEmail || null,
					successUrl: values.successUrl,
					cancelUrl: values.cancelUrl,
				},
			}),
	});
}

/** Confirmation is authoritative via the Stripe webhook, not this poll — see SponsorshipService.confirmFromWebhook. Polls while PENDING, same pattern as useContributionStatus/useOrderStatus. */
export function useSponsorshipStatus(packageId: string, sponsorshipId: string | null) {
	return useQuery({
		queryKey: ["public", "sponsorship-packages", packageId, "sponsorships", sponsorshipId] as const,
		queryFn: () => apiFetch<SponsorshipStatusResult>(`/public/sponsorship-packages/${packageId}/sponsorships/${sponsorshipId}`),
		enabled: !!packageId && !!sponsorshipId,
		refetchInterval: (query) => (query.state.data?.status === "PENDING" ? 2000 : false),
	});
}

// ---- Phase 6 remainder (ADR-019): approval workflow, refunds, invoices, sponsor CRM, QR/link ----

/** The org-admin approval queue — every confirmed sponsorship still awaiting a review decision, across all packages. Lazily enabled by the caller (matches the existing "expand to fetch" pattern `usePackageSponsorships` already uses). */
export function usePendingReviewSponsorships(organizationId: string, enabled: boolean) {
	return useQuery({
		queryKey: pendingReviewKey(organizationId),
		queryFn: () => apiFetch<SponsorshipPage>(`/organizations/${organizationId}/sponsorships/pending-review`),
		enabled: !!organizationId && enabled,
	});
}

function invalidateSponsorshipLists(queryClient: ReturnType<typeof useQueryClient>, organizationId: string) {
	queryClient.invalidateQueries({ queryKey: pendingReviewKey(organizationId) });
	queryClient.invalidateQueries({ queryKey: sponsorshipPackagesKey(organizationId) });
	queryClient.invalidateQueries({ queryKey: ["organizations", organizationId, "sponsorship-packages"] });
}

export function useApproveSponsorship(organizationId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (sponsorshipId: string) =>
			apiFetch<SponsorshipStatusResult>(`/organizations/${organizationId}/sponsorships/${sponsorshipId}/approve`, { method: "POST" }),
		onSuccess: () => invalidateSponsorshipLists(queryClient, organizationId),
	});
}

export function useRejectSponsorship(organizationId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (sponsorshipId: string) =>
			apiFetch<SponsorshipStatusResult>(`/organizations/${organizationId}/sponsorships/${sponsorshipId}/reject`, { method: "POST" }),
		onSuccess: () => invalidateSponsorshipLists(queryClient, organizationId),
	});
}

/** A general org-admin-initiated refund — independent of the review workflow; within the same 14-day window ADR-017 already established for contributions/orders. */
export function useRefundSponsorship(organizationId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (sponsorshipId: string) =>
			apiFetch<SponsorshipStatusResult>(`/organizations/${organizationId}/sponsorships/${sponsorshipId}/refund`, { method: "POST" }),
		onSuccess: () => invalidateSponsorshipLists(queryClient, organizationId),
	});
}

/** A receipt-style invoice summary — lazily fetched only when an org admin opens it. */
export function useSponsorshipInvoice(organizationId: string, sponsorshipId: string | null, enabled: boolean) {
	return useQuery({
		queryKey: ["organizations", organizationId, "sponsorships", sponsorshipId, "invoice"] as const,
		queryFn: () => apiFetch<SponsorshipInvoice>(`/organizations/${organizationId}/sponsorships/${sponsorshipId}/invoice`),
		enabled: !!organizationId && !!sponsorshipId && enabled,
	});
}

export function useUpdateSponsor(organizationId: string, packageId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: ({ sponsorId, values }: { sponsorId: string; values: UpdateSponsorFormValues }) =>
			apiFetch<Sponsor>(`/organizations/${organizationId}/sponsors/${sponsorId}`, {
				method: "PATCH",
				body: {
					name: values.name || null,
					contactEmail: values.contactEmail || null,
					phone: values.phone || null,
					companyName: values.companyName || null,
					notes: values.notes || null,
				},
			}),
		onSuccess: () => queryClient.invalidateQueries({ queryKey: packageSponsorshipsKey(organizationId, packageId) }),
	});
}

/** A shareable QR code + plain URL for an org admin to hand to a prospective sponsor or print in event materials. Nothing persisted, no click-through tracking (ADR-019). */
export function useShareLinkQrCode(organizationId: string, url: string, enabled: boolean) {
	return useQuery({
		queryKey: ["organizations", organizationId, "sponsorship-packages", "qr-code", url] as const,
		queryFn: () => apiFetch<ShareLink>(`/organizations/${organizationId}/sponsorship-packages/qr-code?url=${encodeURIComponent(url)}`),
		enabled: !!organizationId && !!url && enabled,
	});
}
