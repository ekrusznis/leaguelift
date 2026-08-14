import { useQuery } from "@tanstack/react-query";
import { apiFetch } from "../../lib/apiClient";
import type { FundraisingGameEntry } from "./types";

export type GameEntrySearchSort = "NEWEST" | "OLDEST" | "NAME_ASC";

export interface GameEntryPage {
	items: FundraisingGameEntry[];
	page: number;
	size: number;
	totalElements: number;
}

export function useFundraisingGameEntrySearch(
	organizationId: string,
	campaignId: string,
	params: {
		page?: number;
		size?: number;
		q?: string;
		winnerOnly?: boolean;
		sort?: GameEntrySearchSort;
	},
	enabled = true,
) {
	const search = new URLSearchParams({
		page: String(params.page ?? 0),
		size: String(params.size ?? 25),
		sort: params.sort ?? "NEWEST",
		winnerOnly: String(params.winnerOnly ?? false),
	});
	if (params.q?.trim()) search.set("q", params.q.trim());

	return useQuery({
		queryKey: [
			"organizations",
			organizationId,
			"campaigns",
			campaignId,
			"free-game",
			"entries",
			"search",
			Object.fromEntries(search.entries()),
		],
		queryFn: () =>
			apiFetch<GameEntryPage>(
				`/organizations/${organizationId}/campaigns/${campaignId}/game/entries/search?${search.toString()}`,
			),
		enabled: enabled && !!organizationId && !!campaignId,
	});
}
