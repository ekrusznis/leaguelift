package com.rally26.identityintegrity.web

import com.rally26.identityintegrity.domain.DuplicateCandidateGroup
import com.rally26.identityintegrity.domain.DuplicateIdentityKind
import com.rally26.identityintegrity.domain.DuplicateIdentitySummary
import com.rally26.identityintegrity.domain.DuplicateMatchType
import com.rally26.identityintegrity.domain.DuplicateMergePreview
import com.rally26.identityintegrity.domain.DuplicateResolutionStrategy
import com.rally26.identityintegrity.domain.IdentityRef
import com.rally26.identityintegrity.domain.IdentityResolutionOperationStatus
import com.rally26.identityintegrity.domain.IdentityResolutionOperationType
import com.rally26.identityintegrity.domain.IdentityResolutionOutcome
import com.rally26.identityintegrity.domain.IdentityResolutionReceipt
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

data class DuplicateRoleAssignmentResponse(
    val organizationId: UUID,
    val contextType: String,
    val resourceId: UUID,
    val role: String,
)

data class DuplicateGuardianLinkResponse(
    val organizationId: UUID,
    val householdId: UUID,
    val householdAdultId: UUID,
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
    val roleAssignments: List<DuplicateRoleAssignmentResponse>,
    val guardianLinks: List<DuplicateGuardianLinkResponse>,
    val mergedIntoUserId: UUID?,
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

data class DuplicateMatchEvidenceResponse(
    val matchType: DuplicateMatchType,
    val normalizedValue: String,
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
    val sharedEvidence: List<DuplicateMatchEvidenceResponse>,
    val plan: List<MergePlanItemResponse>,
    val requiredSupportOrganizationId: UUID?,
    val previewHash: String,
)

data class ResolveDuplicateIdentityRequest(
    val sourceKind: DuplicateIdentityKind,
    val sourceId: UUID,
    val targetKind: DuplicateIdentityKind,
    val targetId: UUID,
    val previewHash: String,
    val supportAccessId: UUID,
    val reason: String,
    val confirmedTargetEmail: String,
)

data class IdentityResolutionOutcomeResponse(
    val membershipsMoved: Int,
    val membershipsDeduplicated: Int,
    val roleAssignmentsMoved: Int,
    val roleAssignmentsDeduplicated: Int,
    val guardianRelationshipsMoved: Int,
    val guardianRelationshipsDeduplicated: Int,
    val messageThreadMembershipsMoved: Int,
    val messageThreadMembershipsDeduplicated: Int,
    val messageRecipientAccessMoved: Int,
    val messageRecipientAccessAlreadyPresent: Int,
    val announcementRecipientAccessMoved: Int,
    val announcementRecipientAccessAlreadyPresent: Int,
    val guardianRelationshipCreated: Boolean,
    val authTokensInvalidated: Int,
)

data class IdentityResolutionReceiptResponse(
    val operationId: UUID,
    val operationType: IdentityResolutionOperationType,
    val status: IdentityResolutionOperationStatus,
    val source: DuplicateIdentityRefResponse,
    val target: DuplicateIdentityRefResponse,
    val organizationId: UUID,
    val supportAccessId: UUID,
    val previewHash: String,
    val completedAt: Instant,
    val outcome: IdentityResolutionOutcomeResponse,
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
        roleAssignments =
            roleAssignments.map {
                DuplicateRoleAssignmentResponse(
                    it.organizationId,
                    it.contextType,
                    it.resourceId,
                    it.role,
                )
            },
        guardianLinks = guardianLinks.map { DuplicateGuardianLinkResponse(it.organizationId, it.householdId, it.householdAdultId) },
        mergedIntoUserId = mergedIntoUserId,
    )

fun DuplicateCandidateGroup.toResponse() = DuplicateCandidateGroupResponse(matchType, normalizedValue, identities.map { it.toResponse() })

fun DuplicateMergePreview.toResponse() =
    DuplicateMergePreviewResponse(
        source = source.toResponse(),
        target = target.toResponse(),
        strategy = strategy,
        canProceedToMutationSlice = canProceedToMutationSlice,
        dependencies = dependencies.map { IdentityDependencyResponse(it.tableName, it.columnName, it.count, it.historical) },
        sharedEvidence = sharedEvidence.map { DuplicateMatchEvidenceResponse(it.matchType, it.normalizedValue) },
        plan = plan.map { MergePlanItemResponse(it.code, it.severity, it.summary) },
        requiredSupportOrganizationId = requiredSupportOrganizationId,
        previewHash = previewHash,
    )

fun IdentityResolutionReceipt.toResponse() =
    IdentityResolutionReceiptResponse(
        operationId = operationId,
        operationType = operationType,
        status = status,
        source = source.toResponse(),
        target = target.toResponse(),
        organizationId = organizationId,
        supportAccessId = supportAccessId,
        previewHash = previewHash,
        completedAt = completedAt,
        outcome = outcome.toResponse(),
    )

private fun IdentityRef.toResponse() = DuplicateIdentityRefResponse(kind, id)

private fun IdentityResolutionOutcome.toResponse() =
    IdentityResolutionOutcomeResponse(
        membershipsMoved,
        membershipsDeduplicated,
        roleAssignmentsMoved,
        roleAssignmentsDeduplicated,
        guardianRelationshipsMoved,
        guardianRelationshipsDeduplicated,
        messageThreadMembershipsMoved,
        messageThreadMembershipsDeduplicated,
        messageRecipientAccessMoved,
        messageRecipientAccessAlreadyPresent,
        announcementRecipientAccessMoved,
        announcementRecipientAccessAlreadyPresent,
        guardianRelationshipCreated,
        authTokensInvalidated,
    )
