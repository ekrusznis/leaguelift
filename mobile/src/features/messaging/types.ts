/** Matches backend/src/main/kotlin/com/rally26/messaging/web/MessageDto.kt (ADR-102). */

export type MessageThreadType = 'BROADCAST' | 'CONVERSATION' | 'ATHLETE_CONVERSATION';
export type MessageThreadScope = 'ORGANIZATION' | 'TEAM';
export type MessageThreadAudience = 'ALL' | 'STAFF' | 'GUARDIANS' | 'ATHLETES' | 'SELECTED';
export type MessageThreadStatus = 'OPEN' | 'ARCHIVED';

export interface MessageThreadResponse {
  id: string;
  organizationId: string;
  scopeType: MessageThreadScope;
  scopeId: string;
  scopeName: string | null;
  threadType: MessageThreadType;
  title: string;
  audience: MessageThreadAudience;
  emailEnabled: boolean;
  smsEnabled: boolean;
  status: MessageThreadStatus;
  messageCount: number;
  recipientCount: number;
  archivedAt: string | null;
  createdAt: string;
  updatedAt: string;
  safetyLockedAt: string | null;
  safetyLockReason: string | null;
}

export interface MyMessageThreadResponse {
  thread: MessageThreadResponse;
  unreadCount: number;
  lastMessageAt: string | null;
  lastMessagePreview: string | null;
  canReply: boolean;
  accessReason: 'TARGETED' | 'GUARDIAN_VISIBILITY';
}

export interface BroadcastMessageResponse {
  id: string;
  organizationId: string;
  threadId: string;
  senderUserId: string;
  senderDisplayName: string;
  body: string;
  sentAt: string;
  recipientCount: number;
  readRecipientCount: number;
  emailSentCount: number;
  emailFailedCount: number;
  smsSentCount: number;
  smsFailedCount: number;
}

export interface MyBroadcastMessageResponse {
  message: BroadcastMessageResponse;
  readAt: string | null;
  accessReason: 'TARGETED' | 'GUARDIAN_VISIBILITY';
}

/** Athlete-only peer-messaging endpoints, all under /me/messaging/* (ConversationMessageService). */
export interface AthleteMessagingTeamResponse {
  organizationId: string;
  teamId: string;
  teamName: string;
  /** False when the org's SafeSport review gate isn't APPROVED+enabled yet — not an error, just not-yet-available. */
  athleteMessagingEnabled: boolean;
}

export interface ConversationContactResponse {
  userId: string;
  recipientType: string;
  householdId: string | null;
  participantId: string | null;
  displayName: string;
}

export interface CreateAthleteConversationRequest {
  organizationId: string;
  teamId: string;
  idempotencyKey: string;
  title: string;
  targetUserIds: string[];
  initialMessageIdempotencyKey: string;
  initialMessage: string;
}

/** Owner/manager-tier broadcast compose (ADR-105) — POST /organizations/{orgId}/message-threads creates an OPEN thread; sending the first message is a separate call. */
export interface CreateMessageThreadRequest {
  scopeType: MessageThreadScope;
  scopeId?: string;
  idempotencyKey: string;
  title: string;
  audience: MessageThreadAudience;
  emailEnabled?: boolean;
  smsEnabled?: boolean;
}

/** Team-staff-tier "message specific people" compose (ADR-107) — POST /organizations/{orgId}/message-threads/conversations, distinct from CreateAthleteConversationRequest (that one's athlete-self, this one's staff-initiated). */
export interface CreateConversationRequest {
  teamId: string;
  idempotencyKey: string;
  title: string;
  targetUserIds: string[];
  emailEnabled?: boolean;
  smsEnabled?: boolean;
  initialMessageIdempotencyKey: string;
  initialMessage: string;
}
