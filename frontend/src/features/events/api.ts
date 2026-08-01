import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiFetch, apiFetchBlob } from "../../lib/apiClient";
import type { CreateEventInput, DirectionsResponse, EventRsvps, EventTemplate, LeagueLiftEvent, RsvpResponse, SaveEventTemplateInput } from "./types";

export type EventScope =
	| { type: "organization"; organizationId: string }
	| { type: "team"; organizationId: string; teamId: string }
	| { type: "tournament"; organizationId: string; tournamentId: string }
	| { type: "household"; organizationId: string; householdId: string }
	| { type: "participant"; organizationId: string; participantId: string };

function listPath(scope: EventScope) {
	switch (scope.type) {
		case "organization":
			return `/organizations/${scope.organizationId}/events`;
		case "team":
			return `/teams/${scope.teamId}/events?organizationId=${scope.organizationId}`;
		case "tournament":
			return `/tournaments/${scope.tournamentId}/events?organizationId=${scope.organizationId}`;
		case "household":
			return `/households/${scope.householdId}/events?organizationId=${scope.organizationId}`;
		case "participant":
			return `/participants/${scope.participantId}/events?organizationId=${scope.organizationId}`;
	}
}

function createPath(scope: EventScope) {
	switch (scope.type) {
		case "organization":
			return `/organizations/${scope.organizationId}/events`;
		case "team":
			return `/teams/${scope.teamId}/events?organizationId=${scope.organizationId}`;
		case "tournament":
			return `/tournaments/${scope.tournamentId}/events?organizationId=${scope.organizationId}`;
		case "household":
		case "participant":
			throw new Error("This schedule is read-only.");
	}
}

export function eventScopeKey(scope: EventScope) {
	return ["events", scope.type, scope.organizationId, "teamId" in scope ? scope.teamId : null, "tournamentId" in scope ? scope.tournamentId : null, "householdId" in scope ? scope.householdId : null, "participantId" in scope ? scope.participantId : null] as const;
}

export function useEvents(scope: EventScope) {
	return useQuery({
		queryKey: eventScopeKey(scope),
		queryFn: () => apiFetch<LeagueLiftEvent[]>(listPath(scope)),
	});
}

export function useEvent(organizationId: string, eventId: string) {
	return useQuery({
		queryKey: ["organizations", organizationId, "events", eventId],
		queryFn: () => apiFetch<LeagueLiftEvent>(`/organizations/${organizationId}/events/${eventId}`),
		enabled: !!organizationId && !!eventId,
	});
}

export function useCreateEvent(scope: EventScope) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (input: CreateEventInput) => apiFetch<LeagueLiftEvent>(createPath(scope), { method: "POST", body: input }),
		onSuccess: () => queryClient.invalidateQueries({ queryKey: eventScopeKey(scope) }),
	});
}

function useEventAction(organizationId: string, eventId: string, action: "publish" | "cancel" | "postpone" | "detach-from-source") {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: () => apiFetch<LeagueLiftEvent>(`/organizations/${organizationId}/events/${eventId}/${action}`, { method: "POST" }),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: ["events"] });
			queryClient.invalidateQueries({ queryKey: ["organizations", organizationId, "events", eventId] });
		},
	});
}

export function usePublishEvent(organizationId: string, eventId: string) {
	return useEventAction(organizationId, eventId, "publish");
}
export function useCancelEvent(organizationId: string, eventId: string) {
	return useEventAction(organizationId, eventId, "cancel");
}
export function usePostponeEvent(organizationId: string, eventId: string) {
	return useEventAction(organizationId, eventId, "postpone");
}
export function useDetachEvent(organizationId: string, eventId: string) {
	return useEventAction(organizationId, eventId, "detach-from-source");
}

export function useEventRsvps(organizationId: string, eventId: string) {
	return useQuery({
		queryKey: ["organizations", organizationId, "events", eventId, "rsvps"],
		queryFn: () => apiFetch<EventRsvps>(`/events/${eventId}/rsvps?organizationId=${organizationId}`),
		enabled: !!organizationId && !!eventId,
	});
}

export function useSubmitRsvp(organizationId: string, eventId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: ({ participantId, response, note }: { participantId: string; response: RsvpResponse; note?: string | null }) =>
			apiFetch(`/events/${eventId}/participants/${participantId}/rsvp?organizationId=${organizationId}`, {
				method: "PUT",
				body: { response, note: note || null },
			}),
		onSuccess: () => queryClient.invalidateQueries({ queryKey: ["organizations", organizationId, "events", eventId, "rsvps"] }),
	});
}

export function useDirections(organizationId: string, eventId: string) {
	return useQuery({
		queryKey: ["organizations", organizationId, "events", eventId, "directions"],
		queryFn: () => apiFetch<DirectionsResponse>(`/organizations/${organizationId}/events/${eventId}/directions`),
		enabled: !!organizationId && !!eventId,
	});
}

export function downloadEventCalendar(organizationId: string, eventId: string) {
	return apiFetchBlob(`/organizations/${organizationId}/events/${eventId}/calendar.ics`);
}

export function downloadHouseholdCalendar(organizationId: string, householdId: string) {
	return apiFetchBlob(`/households/${householdId}/calendar.ics?organizationId=${organizationId}`);
}

export function useEventTemplates(organizationId: string, includeArchived = false) {
	return useQuery({
		queryKey: ["organizations", organizationId, "event-templates", includeArchived],
		queryFn: () => apiFetch<EventTemplate[]>(`/organizations/${organizationId}/event-templates?includeArchived=${includeArchived}`),
		enabled: !!organizationId,
	});
}

export function useCreateEventTemplate(organizationId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (input: SaveEventTemplateInput) =>
			apiFetch<EventTemplate>(`/organizations/${organizationId}/event-templates`, { method: "POST", body: input }),
		onSuccess: () => queryClient.invalidateQueries({ queryKey: ["organizations", organizationId, "event-templates"] }),
	});
}

export function useUpdateEventTemplate(organizationId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: ({ templateId, input }: { templateId: string; input: SaveEventTemplateInput }) =>
			apiFetch<EventTemplate>(`/organizations/${organizationId}/event-templates/${templateId}`, { method: "PUT", body: input }),
		onSuccess: () => queryClient.invalidateQueries({ queryKey: ["organizations", organizationId, "event-templates"] }),
	});
}

export function useArchiveEventTemplate(organizationId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (templateId: string) =>
			apiFetch<EventTemplate>(`/organizations/${organizationId}/event-templates/${templateId}/archive`, { method: "POST" }),
		onSuccess: () => queryClient.invalidateQueries({ queryKey: ["organizations", organizationId, "event-templates"] }),
	});
}
