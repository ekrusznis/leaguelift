export interface DocumentItem {
	id: string;
	assetId: string;
	url: string;
	title: string | null;
	contentType: string | null;
	byteSizeBytes: number | null;
	updatedAt: string;
}

export interface DocumentListResponse {
	items: DocumentItem[];
}

export interface DocumentAcknowledgment {
	id: string;
	householdAdultId: string;
	acknowledgedByUserId: string;
	acknowledgedAt: string;
}

export interface DocumentAcknowledgmentListResponse {
	items: DocumentAcknowledgment[];
}
