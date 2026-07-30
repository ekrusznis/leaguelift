import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiFetch } from "../../lib/apiClient";
import type { CsvImportResult, EventSourceConnection } from "./types";

const connectionsKey = (organizationId: string) => ["organizations", organizationId, "event-source-connections"] as const;

export function useEventSourceConnections(organizationId: string) {
	return useQuery({
		queryKey: connectionsKey(organizationId),
		queryFn: () => apiFetch<EventSourceConnection[]>(`/organizations/${organizationId}/event-source-connections`),
		enabled: !!organizationId,
	});
}

export function useConnectIcsFeed(organizationId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (params: { label: string; feedUrl: string; timezone: string; teamId: string | null }) =>
			apiFetch<EventSourceConnection>(`/organizations/${organizationId}/event-source-connections/ics-feed`, {
				method: "POST",
				body: params,
			}),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: connectionsKey(organizationId) });
		},
	});
}

export function useDisconnectEventSource(organizationId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (connectionId: string) =>
			apiFetch<void>(`/organizations/${organizationId}/event-source-connections/${connectionId}`, { method: "DELETE" }),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: connectionsKey(organizationId) });
		},
	});
}

export function useImportEventsCsv(organizationId: string) {
	return useMutation({
		mutationFn: (params: { teamId: string | null; timezone: string; csvContent: string }) =>
			apiFetch<CsvImportResult>(`/organizations/${organizationId}/events/csv-import`, {
				method: "POST",
				body: params,
			}),
	});
}
