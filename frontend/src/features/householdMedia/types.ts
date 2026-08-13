export interface HouseholdMediaItem {
	id: string;
	assetId: string;
	url: string;
	contentType: string | null;
	byteSizeBytes: number | null;
	widthPx: number | null;
	heightPx: number | null;
	isVideo: boolean;
	visibility: string;
	publicationStatus: string;
	createdAt: string;
	updatedAt: string;
}

export interface HouseholdMediaListResponse {
	items: HouseholdMediaItem[];
}
