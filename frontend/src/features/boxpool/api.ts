import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiFetch } from "../../lib/apiClient";
import type { BoxPool, ReserveBoxResult } from "./types";

export interface CreateBoxPoolInput {
	sport: string;
	rows: number;
	cols: number;
	pricePerBoxMinor: number;
	rowAxisLabel?: string | null;
	colAxisLabel?: string | null;
	prizeDescription?: string | null;
}

const boxPoolKey = (organizationId: string, campaignId: string) =>
	["organizations", organizationId, "campaigns", campaignId, "box-pool"] as const;

export function useBoxPool(organizationId: string, campaignId: string, enabled = true) {
	return useQuery({
		queryKey: boxPoolKey(organizationId, campaignId),
		queryFn: () => apiFetch<BoxPool>(`/organizations/${organizationId}/campaigns/${campaignId}/box-pool`),
		enabled: enabled && !!organizationId && !!campaignId,
		retry: false,
	});
}

export function useCreateBoxPool(organizationId: string, campaignId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (input: CreateBoxPoolInput) =>
			apiFetch<BoxPool>(`/organizations/${organizationId}/campaigns/${campaignId}/box-pool`, {
				method: "POST",
				body: input,
			}),
		onSuccess: () => queryClient.invalidateQueries({ queryKey: boxPoolKey(organizationId, campaignId) }),
	});
}

export function usePublicBoxPool(slug: string) {
	return useQuery({
		queryKey: ["public", "campaigns", slug, "box-pool"] as const,
		queryFn: () => apiFetch<BoxPool>(`/public/campaigns/${slug}/box-pool`),
		enabled: !!slug,
		retry: false,
	});
}

export interface ReserveBoxInput {
	rowIndex: number;
	colIndex: number;
	claimantName: string;
	claimantEmail?: string | null;
	successUrl: string;
	cancelUrl: string;
}

/** Creates a Stripe Checkout Session for the box the same way a plain contribution does — the caller redirects the browser to `checkoutUrl`. */
export function useReserveBox(slug: string) {
	return useMutation({
		mutationFn: ({ rowIndex, colIndex, ...body }: ReserveBoxInput) =>
			apiFetch<ReserveBoxResult>(`/public/campaigns/${slug}/box-pool/boxes/${rowIndex}/${colIndex}/reserve`, {
				method: "POST",
				body,
			}),
	});
}
