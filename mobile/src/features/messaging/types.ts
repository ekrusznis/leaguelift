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
