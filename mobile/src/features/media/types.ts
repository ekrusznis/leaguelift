/** Mirrors frontend/src/features/media/types.ts exactly (matches backend/.../media/web/*Dto.kt). */

export type MediaUsageSlot = 'LOGO' | 'COVER' | 'PROFILE_PHOTO' | 'PRODUCT_DESIGN' | 'SPONSOR_LOGO' | 'DOCUMENT';
export type MediaEntityType = 'ORGANIZATION' | 'TEAM' | 'TOURNAMENT' | 'HOUSEHOLD_ADULT' | 'PARTICIPANT' | 'PRODUCT' | 'SPONSOR' | 'HOUSEHOLD';

export interface RequestUploadResponse {
  assetId: string;
  uploadUrl: string;
  uploadMethod: 'PUT';
  requiredHeaders: Record<string, string>;
  expiresAt: string;
}

export interface ConfirmUploadResponse {
  assetId: string;
  status: 'READY' | 'REJECTED';
  contentType: string | null;
  byteSize: number | null;
  widthPx: number | null;
  heightPx: number | null;
  rejectionReason: string | null;
}
