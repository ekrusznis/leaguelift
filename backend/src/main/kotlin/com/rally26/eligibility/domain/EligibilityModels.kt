package com.rally26.eligibility.domain

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

enum class RequirementMode {
    GUARDIAN_ESIGN_ACKNOWLEDGMENT,
    GUARDIAN_LEGAL_NAME_SIGNATURE,
    FILE_UPLOAD,
    STAFF_REVIEWED_EXTERNAL,
    INFORMATIONAL,
}

enum class GuardianActionMode { ANY_ONE_GUARDIAN, ALL_GUARDIANS }

enum class ExpirationRule { NEVER, SEASON_END, FIXED_DAYS_FROM_ACCEPTANCE, ORG_CONFIGURED }

enum class RequirementStatus { ACTIVE, ARCHIVED }

/** DESIGN-DOC.md section 14.1L §30.4 — the evidence-strength ladder every requirement's `acceptedEvidenceLevels` picks from. */
enum class EvidenceClassification {
    VERIFIED_EXTERNAL_DOCUMENT,
    EXTERNAL_ACKNOWLEDGMENT,
    EXTERNAL_STATUS_ONLY,
    MANUALLY_VERIFIED,
    MISSING,
    EXPIRED,
    REJECTED,
    REVOKED,
}

enum class AcceptanceMethod {
    ESIGN_LEGAL_NAME,
    ACKNOWLEDGMENT_CHECKBOX,
    FILE_UPLOAD,
    EXTERNAL_IMPORT,
    STAFF_MANUAL_VERIFICATION,
}

enum class EvidenceStatus { ACTIVE, SUPERSEDED, REVOKED, REJECTED, EXPIRED }

/** DESIGN-DOC.md section 14.1L §30.2 — never hand-set; always the output of [com.rally26.eligibility.application.EligibilityService]'s recomputation. */
enum class ClearanceStatus { ROSTER_PENDING, DOCUMENTS_REQUIRED, UNDER_REVIEW, CLEARED, EXPIRED, INELIGIBLE }

/**
 * One immutable version of a requirement definition. "Editing" a requirement never
 * mutates this row — see the migration's header comment — it inserts a new row with
 * the same [requirementGroupId] and `version + 1`, then archives this one.
 */
data class EligibilityRequirement(
    val id: UUID,
    val organizationId: UUID,
    val requirementGroupId: UUID,
    val version: Int,
    val title: String,
    val mode: RequirementMode,
    val content: String,
    val contentHash: String,
    val sport: String?,
    val program: String?,
    val season: String?,
    val teamId: UUID?,
    val guardianActionMode: GuardianActionMode,
    val athleteSelfSignAllowed: Boolean,
    val externalEvidenceAccepted: Boolean,
    val acceptedEvidenceLevels: Set<EvidenceClassification>,
    val effectiveStart: LocalDate,
    val effectiveEnd: LocalDate?,
    val expirationRule: ExpirationRule,
    val expirationDays: Int?,
    val status: RequirementStatus,
    val createdAt: Instant,
    val createdByUserId: UUID,
)

/** One accepted/rejected/revoked/expired evidence instance, bound to the exact requirement version it satisfies. */
data class EligibilityEvidence(
    val id: UUID,
    val organizationId: UUID,
    val participantId: UUID,
    val requirementId: UUID,
    val classification: EvidenceClassification,
    val acceptanceMethod: AcceptanceMethod,
    val signerUserId: UUID?,
    val signerGuardianRelationship: String?,
    val enteredLegalName: String?,
    val documentAssetId: UUID?,
    val provider: String?,
    val externalIdentifiers: String?,
    val contentHash: String?,
    val status: EvidenceStatus,
    val supersededByEvidenceId: UUID?,
    val acceptedAt: Instant,
    val expiresAt: Instant?,
    val ipAddress: String?,
    val userAgent: String?,
    val reviewedByUserId: UUID?,
    val reviewNote: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/** A recomputed snapshot, one row per (participant, team) — never hand-edited, see the migration's header comment. */
data class EligibilityClearance(
    val id: UUID,
    val organizationId: UUID,
    val participantId: UUID,
    val teamId: UUID,
    val status: ClearanceStatus,
    val unmetRequirementCount: Int,
    val computedAt: Instant,
)
