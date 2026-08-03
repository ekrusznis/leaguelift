import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiFetch } from "../../lib/apiClient";
import type { CsvImportResult, EventSourceConnection, IntegrationCatalogItem, IntegrationConnectionSummary } from "./types";

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


const integrationCatalogKey = (organizationId: string) => ["organizations", organizationId, "integration-catalog"] as const;

export function useOrganizationIntegrationCatalog(organizationId: string) {
	return useQuery({
		queryKey: integrationCatalogKey(organizationId),
		queryFn: () => apiFetch<IntegrationCatalogItem[]>(`/organizations/${organizationId}/integrations/catalog`),
		enabled: !!organizationId,
	});
}

export function useOrganizationIntegrationConnections(organizationId: string) {
	return useQuery({
		queryKey: ["organizations", organizationId, "integration-connections"],
		queryFn: () => apiFetch<IntegrationConnectionSummary[]>(`/organizations/${organizationId}/integration-connections`),
		enabled: !!organizationId,
	});
}

const personalCatalogKey = ["me", "integration-catalog"] as const;
const googleCalendarKey = ["me", "integrations", "google-calendar"] as const;

export function usePersonalIntegrationCatalog() {
	return useQuery({
		queryKey: personalCatalogKey,
		queryFn: () => apiFetch<import("./types").IntegrationCatalogItem[]>("/me/integrations/catalog"),
	});
}

export function useGoogleCalendarOverview() {
	return useQuery({
		queryKey: googleCalendarKey,
		queryFn: () => apiFetch<import("./types").GoogleCalendarOverview>("/me/integrations/google-calendar"),
	});
}

export function useGoogleCalendars(enabled: boolean) {
	return useQuery({
		queryKey: [...googleCalendarKey, "calendars"],
		queryFn: () => apiFetch<import("./types").GoogleCalendarDescriptor[]>("/me/integrations/google-calendar/calendars"),
		enabled,
	});
}

export function useStartPersonalIntegrationAuthorization() {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (provider: import("./types").IntegrationProvider) =>
			apiFetch<import("./types").AuthorizationStartResponse>(`/me/integrations/${provider.toLowerCase().replaceAll("_", "-")}/oauth/start`, { method: "POST" }),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: personalCatalogKey });
			queryClient.invalidateQueries({ queryKey: googleCalendarKey });
		},
	});
}

export function useSelectGoogleCalendar() {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (calendarId: string) =>
			apiFetch<import("./types").GoogleCalendarSetting>("/me/integrations/google-calendar/selection", {
				method: "PUT",
				body: { calendarId },
			}),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: googleCalendarKey });
		},
	});
}

export function useClearGoogleCalendarSelection() {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: () => apiFetch<import("./types").GoogleCalendarSetting | null>("/me/integrations/google-calendar/selection", { method: "DELETE" }),
		onSuccess: () => queryClient.invalidateQueries({ queryKey: googleCalendarKey }),
	});
}

function personalConnectionMutation(method: "POST" | "DELETE", action?: "refresh" | "revoke" | "health-check") {
	return (connectionId: string) =>
		apiFetch<import("./types").IntegrationConnectionSummary | import("./types").IntegrationHealthResponse>(
			`/me/integration-connections/${connectionId}${action ? `/${action}` : ""}`,
			{ method },
		);
}

export function useRefreshPersonalIntegration() {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: personalConnectionMutation("POST", "refresh"),
		onSuccess: () => queryClient.invalidateQueries({ queryKey: googleCalendarKey }),
	});
}

export function useCheckPersonalIntegrationHealth() {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: personalConnectionMutation("POST", "health-check"),
		onSuccess: () => queryClient.invalidateQueries({ queryKey: googleCalendarKey }),
	});
}

export function useDisconnectPersonalIntegration() {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: personalConnectionMutation("DELETE"),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: personalCatalogKey });
			queryClient.invalidateQueries({ queryKey: googleCalendarKey });
		},
	});
}

export function usePlatformIntegrationReadiness() {
	return useQuery({
		queryKey: ["platform", "integration-providers"],
		queryFn: () => apiFetch<import("./types").PlatformIntegrationReadiness[]>("/platform/integrations/providers"),
	});
}

export function useStartOrganizationIntegrationAuthorization(organizationId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (provider: import("./types").IntegrationProvider) =>
			apiFetch<import("./types").AuthorizationStartResponse>(`/organizations/${organizationId}/integrations/${provider.toLowerCase().replaceAll("_", "-")}/oauth/start`, { method: "POST" }),
		onSuccess: () => queryClient.invalidateQueries({ queryKey: integrationCatalogKey(organizationId) }),
	});
}

function organizationConnectionMutation(
	organizationId: string,
	method: "POST" | "DELETE",
	action?: "refresh" | "revoke" | "health-check",
) {
	return (connectionId: string) =>
		apiFetch<import("./types").IntegrationConnectionSummary | import("./types").IntegrationHealthResponse>(
			`/organizations/${organizationId}/integration-connections/${connectionId}${action ? `/${action}` : ""}`,
			{ method },
		);
}

export function useRefreshOrganizationIntegration(organizationId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: organizationConnectionMutation(organizationId, "POST", "refresh"),
		onSuccess: () => queryClient.invalidateQueries({ queryKey: integrationCatalogKey(organizationId) }),
	});
}

export function useCheckOrganizationIntegrationHealth(organizationId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: organizationConnectionMutation(organizationId, "POST", "health-check"),
		onSuccess: () => queryClient.invalidateQueries({ queryKey: integrationCatalogKey(organizationId) }),
	});
}

export function useDisconnectOrganizationIntegration(organizationId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: organizationConnectionMutation(organizationId, "DELETE"),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: integrationCatalogKey(organizationId) });
			queryClient.invalidateQueries({ queryKey: ["organizations", organizationId, "integration-connections"] });
		},
	});
}
