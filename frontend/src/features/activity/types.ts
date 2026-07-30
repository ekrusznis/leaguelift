export interface ActivityFeedItem {
	id: string;
	organizationId: string | null;
	organizationName: string | null;
	action: string;
	entityType: string;
	entityId: string;
	occurredAt: string;
}

export interface ActivityFeedResponse {
	items: ActivityFeedItem[];
}
