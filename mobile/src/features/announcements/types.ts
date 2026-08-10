/** Matches backend/src/main/kotlin/com/rally26/communication/web/CommunicationDto.kt (ADR-102). Real domain — not the fake coach-dashboard demo card. */

export type AnnouncementScope = 'ORGANIZATION' | 'TEAM' | 'TOURNAMENT';
export type AnnouncementKind = 'GENERAL' | 'CAMPAIGN_LAUNCH' | 'EVENT_REMINDER' | 'FEE_REMINDER' | 'DOCUMENT_REMINDER';
export type AnnouncementAudience = 'ALL' | 'STAFF' | 'GUARDIANS' | 'ATHLETES';
export type AnnouncementStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED';

export interface AnnouncementResponse {
  id: string;
  organizationId: string;
  scopeType: AnnouncementScope;
  scopeId: string;
  scopeName: string | null;
  kind: AnnouncementKind;
  relatedEntityType: string | null;
  relatedEntityId: string | null;
  title: string;
  body: string;
  audience: AnnouncementAudience;
  status: AnnouncementStatus;
  emailEnabled: boolean;
  smsEnabled: boolean;
  publishedAt: string | null;
  recipientCount: number;
  emailSentCount: number;
  emailFailedCount: number;
  smsSentCount: number;
  smsFailedCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface MyAnnouncementResponse {
  announcement: AnnouncementResponse;
  readAt: string | null;
}
