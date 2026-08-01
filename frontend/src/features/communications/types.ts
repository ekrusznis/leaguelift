export type AnnouncementScopeType = "ORGANIZATION" | "TEAM" | "TOURNAMENT";
export type AnnouncementKind = "GENERAL" | "CAMPAIGN_LAUNCH" | "EVENT_REMINDER" | "FEE_REMINDER" | "DOCUMENT_REMINDER";
export type AnnouncementAudience = "ALL" | "STAFF" | "GUARDIANS" | "ATHLETES";
export type AnnouncementStatus = "DRAFT" | "PUBLISHED" | "ARCHIVED";
export type ReminderResourceType = "CAMPAIGN" | "EVENT" | "FEE_ASSIGNMENT" | "DOCUMENT";

export interface Announcement {
	id: string;
	organizationId: string;
	scopeType: AnnouncementScopeType;
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

export interface MyAnnouncement {
	announcement: Announcement;
	readAt: string | null;
}

export interface PageResponse<T> {
	items: T[];
	page: number;
	size: number;
	totalElements: number;
}

export interface CommunicationScope {
	organizationId: string;
	scopeType: AnnouncementScopeType;
	scopeId: string;
	label: string;
}
