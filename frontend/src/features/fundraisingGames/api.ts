import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiFetch } from "../../lib/apiClient";
import type { FundraisingGame, FundraisingGameEntry, FundraisingGameFormValues, PublicFundraisingGame, PublicFundraisingGameEntry } from "./types";

const gameKey = (organizationId: string, campaignId: string) => ["organizations", organizationId, "campaigns", campaignId, "free-game"] as const;
const entriesKey = (organizationId: string, campaignId: string) => [...gameKey(organizationId, campaignId), "entries"] as const;

function body(values: FundraisingGameFormValues) {
	return {
		...values,
		maxEntries: values.gameType === "BIG_GAME_SQUARES" ? null : (values.maxEntries.trim() ? Number(values.maxEntries) : null),
		rows: values.gameType === "BIG_GAME_SQUARES" ? Number(values.rows) : null,
		cols: values.gameType === "BIG_GAME_SQUARES" ? Number(values.cols) : null,
		instructions: values.instructions.trim() || null,
		prizeDescription: values.prizeDescription.trim() || null,
	};
}

export function useFundraisingGame(organizationId: string, campaignId: string) {
	return useQuery({
		queryKey: gameKey(organizationId, campaignId),
		queryFn: () => apiFetch<FundraisingGame | null>(`/organizations/${organizationId}/campaigns/${campaignId}/game`),
		enabled: !!organizationId && !!campaignId,
		retry: false,
	});
}

export function useCreateFundraisingGame(organizationId: string, campaignId: string) {
	const qc = useQueryClient();
	return useMutation({
		mutationFn: (values: FundraisingGameFormValues) =>
			apiFetch<FundraisingGame>(`/organizations/${organizationId}/campaigns/${campaignId}/game`, {
				method: "POST",
				body: body(values),
			}),
		onSuccess: () => qc.invalidateQueries({ queryKey: gameKey(organizationId, campaignId) }),
	});
}

export function useUpdateFundraisingGame(organizationId: string, campaignId: string) {
	const qc = useQueryClient();
	return useMutation({
		mutationFn: (values: FundraisingGameFormValues) =>
			apiFetch<FundraisingGame>(`/organizations/${organizationId}/campaigns/${campaignId}/game`, {
				method: "PATCH",
				body: body(values),
			}),
		onSuccess: () => qc.invalidateQueries({ queryKey: gameKey(organizationId, campaignId) }),
	});
}

function useFundraisingGameAction(organizationId: string, campaignId: string, action: "open" | "close") {
	const qc = useQueryClient();
	return useMutation({
		mutationFn: () =>
			apiFetch<FundraisingGame>(`/organizations/${organizationId}/campaigns/${campaignId}/game/${action}`, {
				method: "POST",
			}),
		onSuccess: () => {
			qc.invalidateQueries({ queryKey: gameKey(organizationId, campaignId) });
			qc.invalidateQueries({ queryKey: entriesKey(organizationId, campaignId) });
		},
	});
}

export function useOpenFundraisingGame(organizationId: string, campaignId: string) {
	return useFundraisingGameAction(organizationId, campaignId, "open");
}

export function useCloseFundraisingGame(organizationId: string, campaignId: string) {
	return useFundraisingGameAction(organizationId, campaignId, "close");
}

export function useDrawFundraisingGameWinner(organizationId: string, campaignId: string) {
	const qc = useQueryClient();
	return useMutation({
		mutationFn: () =>
			apiFetch<FundraisingGameEntry>(`/organizations/${organizationId}/campaigns/${campaignId}/game/draw-winner`, {
				method: "POST",
			}),
		onSuccess: () => {
			qc.invalidateQueries({ queryKey: gameKey(organizationId, campaignId) });
			qc.invalidateQueries({ queryKey: entriesKey(organizationId, campaignId) });
		},
	});
}

export function useFundraisingGameEntries(organizationId: string, campaignId: string, enabled = true) {
	return useQuery({
		queryKey: entriesKey(organizationId, campaignId),
		queryFn: () => apiFetch<FundraisingGameEntry[]>(`/organizations/${organizationId}/campaigns/${campaignId}/game/entries`),
		enabled: enabled && !!organizationId && !!campaignId,
	});
}

export function usePublicFundraisingGame(slug: string) {
	return useQuery({
		queryKey: ["public", "campaigns", slug, "free-game"],
		queryFn: () => apiFetch<PublicFundraisingGame | null>(`/public/campaigns/${slug}/game`),
		enabled: !!slug,
		retry: false,
	});
}

export function useEnterPublicFundraisingGame(slug: string) {
	const qc = useQueryClient();
	return useMutation({
		mutationFn: (values: { displayName: string; email: string; selectionKey?: string | null; selectionText?: string | null }) =>
			apiFetch<PublicFundraisingGameEntry>(`/public/campaigns/${slug}/game/entries`, {
				method: "POST",
				body: values,
			}),
		onSuccess: () => qc.invalidateQueries({ queryKey: ["public", "campaigns", slug, "free-game"] }),
	});
}
