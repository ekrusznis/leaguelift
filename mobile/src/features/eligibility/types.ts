/** Matches backend/src/main/kotlin/com/rally26/eligibility/web/EligibilityDto.kt (Phase 31, ADR-102 conventions). */

export type RequirementMode =
  | 'GUARDIAN_ESIGN_ACKNOWLEDGMENT'
  | 'GUARDIAN_LEGAL_NAME_SIGNATURE'
  | 'FILE_UPLOAD'
  | 'STAFF_REVIEWED_EXTERNAL'
  | 'INFORMATIONAL';

export type AcceptanceMethod = 'ESIGN_LEGAL_NAME' | 'ACKNOWLEDGMENT_CHECKBOX' | 'FILE_UPLOAD' | 'EXTERNAL_IMPORT' | 'STAFF_MANUAL_VERIFICATION';
export type EvidenceStatus = 'ACTIVE' | 'SUPERSEDED' | 'REVOKED' | 'REJECTED' | 'EXPIRED';
export type ClearanceStatus = 'ROSTER_PENDING' | 'DOCUMENTS_REQUIRED' | 'UNDER_REVIEW' | 'CLEARED' | 'EXPIRED' | 'INELIGIBLE';

export interface EligibilityRequirement {
  id: string;
  title: string;
  mode: RequirementMode;
  content: string;
}

export interface EligibilityEvidence {
  id: string;
  requirementId: string;
  status: EvidenceStatus;
  acceptedAt: string;
  expiresAt: string | null;
}

/** One requirement paired with the caller's current evidence status for it, if any. */
export interface ParticipantRequirement {
  requirement: EligibilityRequirement;
  evidence: EligibilityEvidence | null;
}

export interface SubmitGuardianEvidenceRequest {
  acceptanceMethod: AcceptanceMethod;
  enteredLegalName?: string;
  /** Set only for acceptanceMethod: 'FILE_UPLOAD' — the assetId returned by the media upload/confirm flow (Phase 37.9). */
  documentAssetId?: string;
}

/** Coach roster clearance view — operational status only, never evidence contents (DESIGN-DOC.md 14.1L §30.2). */
export interface EligibilityClearance {
  participantId: string;
  teamId: string;
  status: ClearanceStatus;
  unmetRequirementCount: number;
  computedAt: string | null;
}
