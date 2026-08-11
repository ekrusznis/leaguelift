/** Matches backend/src/main/kotlin/com/rally26/messaging/web/MessageSafeSportDto.kt's guardian-facing shapes (ADR-108). */

export type MessageContactRestrictionKind = 'ADULT_TO_MINOR' | 'ALL_MESSAGING';
export type MessageContactRestrictionStatus = 'ACTIVE' | 'LIFTED';

export interface GuardianMessagingParticipantResponse {
  organizationId: string;
  participantId: string;
  displayName: string;
}

export interface MessageContactRestrictionResponse {
  id: string;
  organizationId: string;
  participantId: string;
  participantDisplayName: string;
  requestedByUserId: string;
  kind: MessageContactRestrictionKind;
  note: string | null;
  status: MessageContactRestrictionStatus;
  createdAt: string;
  liftedAt: string | null;
  liftedByUserId: string | null;
  liftNote: string | null;
}

export interface CreateMessageContactRestrictionRequest {
  organizationId: string;
  participantId: string;
  kind: MessageContactRestrictionKind;
  note?: string;
}

export interface LiftMessageContactRestrictionRequest {
  note: string;
}
