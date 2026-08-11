package com.rally26.eligibility.web

import com.rally26.eligibility.domain.AcceptanceMethod
import com.rally26.eligibility.domain.ClearanceStatus
import com.rally26.eligibility.domain.EligibilityClearance
import com.rally26.eligibility.domain.EligibilityEvidence
import com.rally26.eligibility.domain.EligibilityRequirement
import com.rally26.eligibility.domain.EvidenceClassification
import com.rally26.eligibility.domain.ExpirationRule
import com.rally26.eligibility.domain.GuardianActionMode
import com.rally26.eligibility.domain.RequirementMode
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class CreateEligibilityRequirementRequest(
    @field:NotBlank @field:Size(max = 200) val title: String,
    @field:NotNull val mode: RequirementMode,
    @field:NotBlank @field:Size(max = 20_000) val content: String,
    val sport: String? = null,
    val program: String? = null,
    val season: String? = null,
    val teamId: UUID? = null,
    val guardianActionMode: GuardianActionMode = GuardianActionMode.ANY_ONE_GUARDIAN,
    val athleteSelfSignAllowed: Boolean = false,
    val externalEvidenceAccepted: Boolean = false,
    val acceptedEvidenceLevels: Set<EvidenceClassification> = emptySet(),
    @field:NotNull val effectiveStart: LocalDate,
    val effectiveEnd: LocalDate? = null,
    val expirationRule: ExpirationRule = ExpirationRule.NEVER,
    val expirationDays: Int? = null,
)

data class CreateEligibilityRequirementVersionRequest(
    @field:NotBlank @field:Size(max = 200) val title: String,
    @field:NotBlank @field:Size(max = 20_000) val content: String,
    @field:NotNull val effectiveStart: LocalDate,
    val effectiveEnd: LocalDate? = null,
)

data class EligibilityRequirementResponse(
    val id: UUID,
    val organizationId: UUID,
    val requirementGroupId: UUID,
    val version: Int,
    val title: String,
    val mode: String,
    val content: String,
    val contentHash: String,
    val sport: String?,
    val program: String?,
    val season: String?,
    val teamId: UUID?,
    val guardianActionMode: String,
    val athleteSelfSignAllowed: Boolean,
    val externalEvidenceAccepted: Boolean,
    val acceptedEvidenceLevels: Set<String>,
    val effectiveStart: LocalDate,
    val effectiveEnd: LocalDate?,
    val expirationRule: String,
    val expirationDays: Int?,
    val status: String,
    val createdAt: Instant,
    val createdByUserId: UUID,
)

fun EligibilityRequirement.toResponse() =
    EligibilityRequirementResponse(
        id = id,
        organizationId = organizationId,
        requirementGroupId = requirementGroupId,
        version = version,
        title = title,
        mode = mode.name,
        content = content,
        contentHash = contentHash,
        sport = sport,
        program = program,
        season = season,
        teamId = teamId,
        guardianActionMode = guardianActionMode.name,
        athleteSelfSignAllowed = athleteSelfSignAllowed,
        externalEvidenceAccepted = externalEvidenceAccepted,
        acceptedEvidenceLevels = acceptedEvidenceLevels.map { it.name }.toSet(),
        effectiveStart = effectiveStart,
        effectiveEnd = effectiveEnd,
        expirationRule = expirationRule.name,
        expirationDays = expirationDays,
        status = status.name,
        createdAt = createdAt,
        createdByUserId = createdByUserId,
    )

data class EligibilityClearanceResponse(
    val participantId: UUID,
    val teamId: UUID,
    val status: String,
    val unmetRequirementCount: Int,
    val computedAt: Instant?,
)

fun EligibilityClearance.toResponse() =
    EligibilityClearanceResponse(
        participantId = participantId,
        teamId = teamId,
        status = status.name,
        unmetRequirementCount = unmetRequirementCount,
        computedAt = computedAt,
    )

/** A participant with no [EligibilityClearance] row at all — not-yet-evaluated, per EligibilityService's documented convention. */
fun rosterPendingResponse(
    participantId: UUID,
    teamId: UUID,
) = EligibilityClearanceResponse(
    participantId = participantId,
    teamId = teamId,
    status = ClearanceStatus.ROSTER_PENDING.name,
    unmetRequirementCount = 0,
    computedAt = null,
)

data class EligibilityEvidenceResponse(
    val id: UUID,
    val requirementId: UUID,
    val classification: String,
    val acceptanceMethod: String,
    val enteredLegalName: String?,
    val documentAssetId: UUID?,
    val status: String,
    val acceptedAt: Instant,
    val expiresAt: Instant?,
)

fun EligibilityEvidence.toResponse() =
    EligibilityEvidenceResponse(
        id = id,
        requirementId = requirementId,
        classification = classification.name,
        acceptanceMethod = acceptanceMethod.name,
        enteredLegalName = enteredLegalName,
        documentAssetId = documentAssetId,
        status = status.name,
        acceptedAt = acceptedAt,
        expiresAt = expiresAt,
    )

/** One requirement paired with the caller's current evidence status for it, if any — what a guardian/athlete-self sees. */
data class ParticipantRequirementResponse(
    val requirement: EligibilityRequirementResponse,
    val evidence: EligibilityEvidenceResponse?,
)

data class SubmitGuardianEvidenceRequest(
    @field:NotNull val acceptanceMethod: AcceptanceMethod,
    @field:Size(max = 200) val enteredLegalName: String? = null,
    val documentAssetId: UUID? = null,
)
