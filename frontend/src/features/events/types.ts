export type EventType = "COMPETITION" | "TOURNAMENT" | "PRACTICE" | "MEETING" | "OTHER";
export type EventStatus = "DRAFT" | "TENTATIVE" | "SCHEDULED" | "DELAYED" | "POSTPONED" | "CANCELLED" | "COMPLETED";
export type EventVisibility = "TEAM" | "ORGANIZATION" | "PUBLIC";
export type RsvpResponse = "ATTENDING" | "NOT_ATTENDING" | "MAYBE";

export interface Rally26Event {
	id: string;
	organizationId: string;
	teamId: string | null;
	tournamentId: string | null;
	opponentTeamId: string | null;
	opponentName: string | null;
	eventType: EventType;
	displayTitle: string;
	description: string | null;
	status: EventStatus;
	startAt: string | null;
	endAt: string | null;
	arrivalAt: string | null;
	meetingAt: string | null;
	timezone: string;
	venueName: string | null;
	address: string | null;
	latitude: number | null;
	longitude: number | null;
	area: string | null;
	meetingPoint: string | null;
	directionsNotes: string | null;
	visibility: EventVisibility;
	sourceType: string;
	createdAt: string;
	updatedAt: string;
	/** Phase 24 slice 24.5 (ADR-071): a plain `YYYY-MM-DD` date, never zone-converted. When set, every instant field above is null. */
	allDayDate: string | null;
	/** A detected-but-unapplied change from this event's source (ICS feed poll, CSV re-import) — null when nothing is staged. Apply via useApplySourceUpdate. */
	pendingSourceChanges: EventFieldChange[] | null;
}

export interface EventFieldChange {
	field: string;
	oldValue: string | null;
	newValue: string | null;
}

export interface CreateEventInput {
	teamId?: string | null;
	tournamentId?: string | null;
	opponentTeamId?: string | null;
	opponentName?: string | null;
	eventType: EventType;
	title?: string | null;
	description?: string | null;
	startAt?: string | null;
	endAt?: string | null;
	arrivalAt?: string | null;
	meetingAt?: string | null;
	timezone: string;
	venueName?: string | null;
	address?: string | null;
	area?: string | null;
	meetingPoint?: string | null;
	directionsNotes?: string | null;
	visibility: EventVisibility;
	allDayDate?: string | null;
}

export interface TimezoneDefault {
	timezone: string;
}

export interface RsvpSummary {
	attending: number;
	notAttending: number;
	maybe: number;
	noResponse: number;
}

export interface EventRsvp {
	id: string;
	eventId: string;
	participantId: string;
	/** Staff-visible individual responses only; null on the guardian/athlete summary-only view or when submitting your own RSVP. */
	participantName: string | null;
	response: RsvpResponse;
	note: string | null;
	source: string;
	createdAt: string;
	updatedAt: string;
}

export interface EventRsvps {
	summary: RsvpSummary;
	responses: EventRsvp[];
}

export interface DirectionsResponse {
	url: string | null;
}

export type EventTemplateStatus = "ACTIVE" | "ARCHIVED";

export interface EventTemplate {
	id: string;
	organizationId: string;
	name: string;
	eventType: EventType;
	title: string | null;
	description: string | null;
	durationMinutes: number | null;
	arrivalOffsetMinutes: number | null;
	meetingOffsetMinutes: number | null;
	timezone: string;
	venueName: string | null;
	address: string | null;
	area: string | null;
	meetingPoint: string | null;
	directionsNotes: string | null;
	visibility: EventVisibility;
	status: EventTemplateStatus;
	createdAt: string;
	updatedAt: string;
}

export interface SaveEventTemplateInput {
	name: string;
	eventType: EventType;
	title?: string | null;
	description?: string | null;
	durationMinutes?: number | null;
	arrivalOffsetMinutes?: number | null;
	meetingOffsetMinutes?: number | null;
	timezone: string;
	venueName?: string | null;
	address?: string | null;
	area?: string | null;
	meetingPoint?: string | null;
	directionsNotes?: string | null;
	visibility: EventVisibility;
}
