/** Matches backend/src/main/kotlin/com/rally26/event/web/EventDto.kt and EventRsvpDto.kt (ADR-102). */

export type EventType = 'COMPETITION' | 'TOURNAMENT' | 'PRACTICE' | 'MEETING' | 'OTHER';
export type EventStatus = 'DRAFT' | 'TENTATIVE' | 'SCHEDULED' | 'DELAYED' | 'POSTPONED' | 'CANCELLED' | 'COMPLETED';

export interface EventResponse {
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
  visibility: 'PUBLIC' | 'ORGANIZATION' | 'TEAM';
  sourceType: 'MANUAL' | 'CSV_IMPORT' | 'ICS_FEED' | 'MAXPREPS' | 'GAMECHANGER';
  createdAt: string;
  updatedAt: string;
  allDayDate: string | null;
}

export type RsvpStatus = 'ATTENDING' | 'NOT_ATTENDING' | 'MAYBE' | 'NO_RESPONSE';
export type SubmittableRsvpStatus = 'ATTENDING' | 'NOT_ATTENDING' | 'MAYBE';

export interface EventRsvpResponse {
  id: string;
  eventId: string;
  participantId: string;
  participantName: string | null;
  response: RsvpStatus;
  note: string | null;
  source: 'SELF' | 'GUARDIAN' | 'ADMIN';
  createdAt: string;
  updatedAt: string;
}

export interface RsvpSummary {
  attending: number;
  notAttending: number;
  maybe: number;
  noResponse: number;
}

export interface EventRsvpsResponse {
  summary: RsvpSummary;
  responses: EventRsvpResponse[];
}
