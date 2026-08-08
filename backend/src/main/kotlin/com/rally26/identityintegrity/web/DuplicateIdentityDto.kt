package com.rally26.identityintegrity.web

import com.rally26.identityintegrity.domain.DuplicateCandidateGroup
import com.rally26.identityintegrity.domain.DuplicateIdentityKind
import com.rally26.identityintegrity.domain.DuplicateIdentitySummary
import com.rally26.identityintegrity.domain.DuplicateMatchType
import com.rally26.identityintegrity.domain.DuplicateMergePreview
import com.rally26.identityintegrity.domain.DuplicateResolutionStrategy
import com.rally26.identityintegrity.domain.MergePlanSeverity
import java.time.Instant
import java.util.UUID

data class DuplicateIdentityRefResponse(
    val kind: DuplicateIdentityKind,
    val id: UUID,
)

data class DuplicateMembershipResponse(
    val organizationId: UUID,
    val organizationName: String,
    val role: String,
    val status: String,
)

data class DuplicateIdentityResponse(
    val ref: DuplicateIdentityRefResponse,
    val displayName: String,
    val email: String?,
    val phone: String?,
    val status: String,
    val createdAt: Instant,
    val organizationId: UUID?,
    val organizationName: String?,
    val householdId: UUID?,
    val householdName: String?,
    val linkedUserId: UUID?,
    val platformAdministrator: Boolean,
    val memberships: List<DuplicateMembershipResponse>,
    val externalIds: List<String>,
)

data class DuplicateCandidateGroupResponse(
    val matchType: DuplicateMatchType,
    val normalizedValue: String,
    val identities: List<DuplicateIdentityResponse>,
)

data class DuplicateCandidateListResponse(
    val items: List<DuplicateCandidateGroupResponse>,
)

data class IdentityDependencyResponse(
    val tableName: String,
    val columnName: String,
    val count: Long,
    val historical: Boolean,
)

data class MergePlanItemResponse(
    val code: String,
    val severity: MergePlanSeverity,
    val summary: String,
)

data class DuplicateMergePreviewResponse(
    val source: DuplicateIdentityResponse,
    val target: DuplicateIdentityResponse,
    val strategy: DuplicateResolutionStrategy,
    val canProceedToMutationSlice: Boolean,
    val dependencies: List<IdentityDependencyResponse>,
    val plan: List<MergePlanItemResponse>,
)

fun DuplicateIdentitySummary.toResponse() =
    DuplicateIdentityResponse(
        ref = DuplicateIdentityRefResponse(ref.kind, ref.id),
        displayName = displayName,
        email = email,
        phone = phone,
        status = status,
        createdAt = createdAt,
        organizationId = organizationId,
        organizationName = organizationName,
        householdId = householdId,
        householdName = householdName,
        linkedUserId = linkedUserId,
        platformAdministrator = platformAdministrator,
        memberships = memberships.map { DuplicateMembershipResponse(it.organizationId, it.organizationName, it.role, it.status) },
        externalIds = externalIds,
    )

fun DuplicateCandidateGroup.toResponse() = DuplicateCandidateGroupResponse(matchType, normalizedValue, identities.map { it.toResponse() })

fun DuplicateMergePreview.toResponse() =
    DuplicateMergePreviewResponse(
        source.toResponse(),
        target.toResponse(),
        strategy,
        canProceedToMutationSlice,
        dependencies.map { IdentityDependencyResponse(it.tableName, it.columnName, it.count, it.historical) },
        plan.map { MergePlanItemResponse(it.code, it.severity, it.summary) },
    )
