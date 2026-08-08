package com.rally26.identityintegrity.domain

import java.time.Instant
import java.util.UUID

enum class DuplicateIdentityKind { APP_USER, GUARDIAN_SHELL }

enum class DuplicateMatchType { EMAIL, PHONE }

enum class DuplicateResolutionStrategy { LINK_SHELL_TO_EXISTING_USER, MERGE_USER_ACCOUNTS, REVIEW_SHELLS_SEPARATELY, KEEP_SEPARATE }

enum class MergePlanSeverity { INFO, WARNING, BLOCKER }

data class IdentityRef(
    val kind: DuplicateIdentityKind,
    val id: UUID,
)

data class DuplicateOrganizationMembership(
    val organizationId: UUID,
    val organizationName: String,
    val role: String,
    val status: String,
)

data class DuplicateIdentitySummary(
    val ref: IdentityRef,
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
    val memberships: List<DuplicateOrganizationMembership>,
    val externalIds: List<String>,
)

data class DuplicateCandidateGroup(
    val matchType: DuplicateMatchType,
    val normalizedValue: String,
    val identities: List<DuplicateIdentitySummary>,
)

data class IdentityDependency(
    val tableName: String,
    val columnName: String,
    val count: Long,
    val historical: Boolean,
)

data class MergePlanItem(
    val code: String,
    val severity: MergePlanSeverity,
    val summary: String,
)

data class DuplicateMergePreview(
    val source: DuplicateIdentitySummary,
    val target: DuplicateIdentitySummary,
    val strategy: DuplicateResolutionStrategy,
    val canProceedToMutationSlice: Boolean,
    val dependencies: List<IdentityDependency>,
    val plan: List<MergePlanItem>,
)
