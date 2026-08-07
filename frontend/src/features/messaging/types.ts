export type MessageScopeType = "ORGANIZATION" | "TEAM";
export type MessageThreadType = "BROADCAST" | "CONVERSATION";
export type MessageAudience = "ALL" | "STAFF" | "GUARDIANS" | "ATHLETES" | "SELECTED";
export type MessageThreadStatus = "OPEN" | "ARCHIVED";
export type MessageAccessReason = "TARGETED" | "GUARDIAN_VISIBILITY";
export type MessageRecipientType = "STAFF" | "GUARDIAN" | "ATHLETE";

export interface MessageThread {
	id: string;
	organizationId: string;
	scopeType: MessageScopeType;
	scopeId: string;
	scopeName: string | null;
	threadType: MessageThreadType;
	title: string;
	audience: MessageAudience;
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

export interface BroadcastMessage {
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

export interface ConversationContact {
	userId: string;
	recipientType: "GUARDIAN" | "ATHLETE";
	householdId: string | null;
	participantId: string | null;
	displayName: string;
}

export interface MessageThreadMember {
	userId: string;
	memberType: MessageRecipientType;
	displayName: string;
	accessReason: MessageAccessReason;
	canReply: boolean;
}

export interface MyMessageThread {
	thread: MessageThread;
	unreadCount: number;
	lastMessageAt: string;
	lastMessagePreview: string;
	canReply: boolean;
	accessReason: MessageAccessReason;
}

export interface MyBroadcastMessage {
	message: BroadcastMessage;
	readAt: string | null;
	accessReason: MessageAccessReason;
}

export interface PageResponse<T> {
	items: T[];
	page: number;
	size: number;
	totalElements: number;
}

export interface MessageManagementScope {
	organizationId: string;
	scopeType: MessageScopeType;
	scopeId: string;
	label: string;
}


export type MessageSafetyReportReason = "HARASSMENT" | "BULLYING" | "INAPPROPRIATE_CONTENT" | "SAFETY_CONCERN" | "SPAM" | "OTHER";
export type MessageSafetyReportStatus = "OPEN" | "IN_REVIEW" | "RESOLVED" | "DISMISSED";
export type MessageModerationEventType = "REPORT_CREATED" | "REVIEW_STARTED" | "RESOLVED" | "DISMISSED" | "THREAD_LOCKED" | "THREAD_UNLOCKED";

export interface MessageSafetyReport {
	id: string;
	organizationId: string;
	threadId: string;
	threadTitle: string;
	messageId: string;
	messageBody: string;
	messageSenderDisplayName: string;
	reporterUserId: string;
	reporterDisplayName: string;
	reason: MessageSafetyReportReason;
	details: string | null;
	status: MessageSafetyReportStatus;
	assignedToUserId: string | null;
	resolutionNote: string | null;
	createdAt: string;
	updatedAt: string;
	resolvedAt: string | null;
	threadSafetyLockedAt: string | null;
	threadSafetyLockReason: string | null;
}

export interface MessageModerationEvent {
	id: string;
	organizationId: string;
	reportId: string | null;
	threadId: string;
	messageId: string | null;
	actorUserId: string;
	actorDisplayName: string;
	eventType: MessageModerationEventType;
	note: string | null;
	createdAt: string;
}
