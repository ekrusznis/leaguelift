export type AuditResult = "SUCCESS" | "FAILURE" | "DENIED" | "PARTIAL";
export type AuditActorType = "USER" | "SYSTEM" | "PROVIDER";
export type AuditSortField = "DATE" | "ACTION" | "RESULT";
export type AuditSortDirection = "ASC" | "DESC";

export interface AuditHistoryFilterAccess {
	canFilterUser: boolean;
	canFilterTeam: boolean;
	canFilterOrganization: boolean;
}

export interface AuditHistoryItem {
	id: string;
	occurredAt: string;
	action: string;
	result: AuditResult;
	summary: string;
	actorType: AuditActorType;
	actorUserId: string | null;
	actorDisplayName: string | null;
	targetUserId: string | null;
	targetDisplayName: string | null;
	organizationId: string | null;
	organizationName: string | null;
	teamId: string | null;
	teamName: string | null;
	householdId: string | null;
	householdName: string | null;
	participantId: string | null;
	participantDisplayName: string | null;
	entityType: string;
	entityId: string;
}

export interface AuditHistoryPageResponse {
	items: AuditHistoryItem[];
	nextCursor: string | null;
	filterAccess: AuditHistoryFilterAccess;
}

export interface AuditHistoryFilters {
	from?: string;
	to?: string;
	action?: string;
	result?: AuditResult | "";
	keyword?: string;
	user?: string;
	organizationId?: string;
	teamId?: string;
	sortBy: AuditSortField;
	direction: AuditSortDirection;
	size?: number;
	cursor?: string;
}
