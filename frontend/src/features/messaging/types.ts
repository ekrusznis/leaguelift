export type MessageScopeType = "ORGANIZATION" | "TEAM";
export type MessageAudience = "ALL" | "STAFF" | "GUARDIANS" | "ATHLETES";
export type MessageThreadStatus = "OPEN" | "ARCHIVED";
export type MessageAccessReason = "TARGETED" | "GUARDIAN_VISIBILITY";

export interface MessageThread {
	id: string;
	organizationId: string;
	scopeType: MessageScopeType;
	scopeId: string;
	scopeName: string | null;
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

export interface MyMessageThread {
	thread: MessageThread;
	unreadCount: number;
	lastMessageAt: string;
	lastMessagePreview: string;
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
