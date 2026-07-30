import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiFetch } from "../../lib/apiClient";
import type { EventSourceConnection } from "./types";

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
		mutationFn: (params: { label: string; feedUrl: string }) =>
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
