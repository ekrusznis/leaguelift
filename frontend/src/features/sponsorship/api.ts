import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiFetch } from "../../lib/apiClient";
import type { MediaAssignment } from "../media/types";
import type { CreateSponsorshipCheckoutFormValues, CreateSponsorshipPackageFormValues } from "./schema";
import type {
	PublicSponsorshipPackage,
	SponsorDirectoryEntry,
	SponsorshipCheckout,
	SponsorshipPackage,
	SponsorshipPackagePage,
	SponsorshipPage,
	SponsorshipStatusResult,
} from "./types";

const sponsorshipPackagesKey = (organizationId: string) => ["organizations", organizationId, "sponsorship-packages"] as const;
const packageSponsorshipsKey = (organizationId: string, packageId: string) =>
	["organizations", organizationId, "sponsorship-packages", packageId, "sponsorships"] as const;

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
