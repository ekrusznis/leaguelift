/**
 * Matches backend/src/main/kotlin/com/rally26/document/web/DocumentDto.kt exactly
 * (verified directly against source, ADR-103). Simple upload+acknowledge — NOT Phase 31
 * eligibility/waivers, which doesn't exist on the backend yet.
 */

export interface DocumentResponse {
  /** This is the *assignment* id, not a separate document id — DocumentController maps assignment.id here. */
  id: string;
  assetId: string;
  url: string;
  title: string | null;
  contentType: string | null;
  byteSizeBytes: number | null;
  updatedAt: string;
}

export interface DocumentListResponse {
  items: DocumentResponse[];
}

export interface DocumentAcknowledgmentResponse {
  id: string;
  householdAdultId: string;
  acknowledgedByUserId: string;
  acknowledgedAt: string;
}

export interface DocumentAcknowledgmentListResponse {
  items: DocumentAcknowledgmentResponse[];
}
