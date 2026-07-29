export type MediaUsageSlot = "LOGO" | "COVER" | "PRODUCT_DESIGN" | "SPONSOR_LOGO";

export interface RequestUploadResponse {
	assetId: string;
	uploadUrl: string;
	uploadMethod: "PUT";
	requiredHeaders: Record<string, string>;
	expiresAt: string;
}

export interface ConfirmUploadResponse {
	assetId: string;
	status: "READY" | "REJECTED";
	contentType: string | null;
	byteSize: number | null;
	widthPx: number | null;
	heightPx: number | null;
	rejectionReason: string | null;
}

export interface MediaAssignment {
	usageSlot: MediaUsageSlot;
	assetId: string;
	url: string;
	altText: string | null;
	contentType: string | null;
	widthPx: number | null;
	heightPx: number | null;
	byteSizeBytes: number | null;
	visibility: string;
	publicationStatus: string;
	updatedAt: string;
}

export interface MediaAssignmentListResponse {
	items: MediaAssignment[];
}
