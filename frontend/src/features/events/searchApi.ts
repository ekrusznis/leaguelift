import { useQuery } from "@tanstack/react-query";
import { apiFetch } from "../../lib/apiClient";
import { eventScopeKey, type EventScope } from "./api";
import type { EventStatus, EventType, Rally26Event } from "./types";

export type EventSearchSort = "DATE_ASC" | "DATE_DESC" | "TITLE_ASC" | "CREATED_DESC";

export interface EventSearchParams {
	page?: number;
	size?: number;
	q?: string;
	eventType?: EventType | "";
	status?: EventStatus | "";
	fromDate?: string;
	toDate?: string;
	sort?: EventSearchSort;
}

export interface EventPage {
	items: Rally26Event[];
	page: number;
	size: number;
	totalElements: number;
}

function searchPath(scope: EventScope) {
	switch (scope.type) {
		case "organization":
			return `/organizations/${scope.organizationId}/events/search`;
		case "team":
			return `/teams/${scope.teamId}/events/search`;
		case "tournament":
			return `/tournaments/${scope.tournamentId}/events/search`;
		case "household":
			return `/households/${scope.householdId}/events/search`;
		case "participant":
			return `/participants/${scope.participantId}/events/search`;
	}
}

function dateBoundary(value: string | undefined, endOfDay: boolean) {
	if (!value) return null;
	const date = new Date(`${value}T${endOfDay ? "23:59:59.999" : "00:00:00.000"}`);
	return Number.isNaN(date.getTime()) ? null : date.toISOString();
}

export function useEventSearch(scope: EventScope, params: EventSearchParams) {
	const search = new URLSearchParams({
		page: String(params.page ?? 0),
		size: String(params.size ?? 25),
		sort: params.sort ?? "DATE_ASC",
		organizationId: scope.organizationId,
	});
	if (params.q?.trim()) search.set("q", params.q.trim());
	if (params.eventType) search.set("eventType", params.eventType);
	if (params.status) search.set("status", params.status);
	const from = dateBoundary(params.fromDate, false);
	const to = dateBoundary(params.toDate, true);
	if (from) search.set("from", from);
	if (to) search.set("to", to);

	return useQuery({
		queryKey: [...eventScopeKey(scope), "search", Object.fromEntries(search.entries())],
		queryFn: () => apiFetch<EventPage>(`${searchPath(scope)}?${search.toString()}`),
	});
}
