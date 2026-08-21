package com.rally26.identityintegrity.application

import com.rally26.identityintegrity.domain.DuplicateIdentityKind
import com.rally26.identityintegrity.domain.DuplicateIdentitySummary
import com.rally26.identityintegrity.domain.DuplicateMatchEvidence
import com.rally26.identityintegrity.domain.DuplicateMatchType
import com.rally26.identityintegrity.domain.DuplicateMergePreview
import com.rally26.identityintegrity.domain.DuplicateResolutionStrategy
import com.rally26.identityintegrity.domain.IdentityDependency
import com.rally26.identityintegrity.domain.MergePlanItem
import com.rally26.identityintegrity.domain.MergePlanSeverity
import java.util.UUID

object DuplicateMergePlanner {
    fun plan(
        source: DuplicateIdentitySummary,
        target: DuplicateIdentitySummary,
        dependencies: List<IdentityDependency>,
        sharedEvidence: List<DuplicateMatchEvidence> = inferDirectEvidence(source, target),
    ): DuplicateMergePreview {
        require(source.ref != target.ref) { "Source and target identities must be different." }
        val items = mutableListOf<MergePlanItem>()
        val strategy =
            when {
                source.ref.kind == DuplicateIdentityKind.GUARDIAN_SHELL && target.ref.kind == DuplicateIdentityKind.APP_USER ->
                    DuplicateResolutionStrategy.LINK_SHELL_TO_EXISTING_USER
                source.ref.kind == DuplicateIdentityKind.APP_USER && target.ref.kind == DuplicateIdentityKind.APP_USER ->
                    DuplicateResolutionStrategy.MERGE_USER_ACCOUNTS
                source.ref.kind == DuplicateIdentityKind.GUARDIAN_SHELL && target.ref.kind == DuplicateIdentityKind.GUARDIAN_SHELL ->
                    DuplicateResolutionStrategy.REVIEW_SHELLS_SEPARATELY
                else -> DuplicateResolutionStrategy.KEEP_SEPARATE
            }

        val requiredSupportOrganizationId =
            when (strategy) {
                DuplicateResolutionStrategy.LINK_SHELL_TO_EXISTING_USER -> planShellLink(source, target, items)
                DuplicateResolutionStrategy.MERGE_USER_ACCOUNTS -> planUserMerge(source, target, dependencies, items)
                DuplicateResolutionStrategy.REVIEW_SHELLS_SEPARATELY -> {
                    items +=
                        blocker(
                            "SHELL_TO_SHELL_NO_ACCOUNT_TARGET",
                            "Two guardian shells are not merged directly. Review whether both should link to the same existing or newly activated adult account.",
                        )
                    null
                }
                DuplicateResolutionStrategy.KEEP_SEPARATE -> {
                    items +=
                        blocker(
                            "TARGET_MUST_BE_APP_USER",
                            "A guardian shell cannot be the surviving account target. Reverse the preview or keep the records separate.",
                        )
                    null
                }
            }

        if (sharedEvidence.isEmpty()) {
            items +=
                blocker(
                    "NO_SHARED_DUPLICATE_EVIDENCE",
                    "The source and target no longer share normalized email or phone evidence. Refresh the candidate scan rather than resolving unrelated identities.",
                )
        } else {
            items +=
                info(
                    "SHARED_DUPLICATE_EVIDENCE",
                    "The identities still share ${sharedEvidence.joinToString {
                        "${it.matchType.name.lowercase()}: ${it.normalizedValue}"
                    }}.",
                )
        }

        if (!sameNonBlank(source.email, target.email) && !source.email.isNullOrBlank() && !target.email.isNullOrBlank()) {
            items +=
                warning(
                    "EMAIL_MISMATCH",
                    "The identities have different email addresses. Verify they belong to the same person before resolving the duplicate.",
                )
        }
        if (!samePhone(source.phone, target.phone) && !source.phone.isNullOrBlank() && !target.phone.isNullOrBlank()) {
            items +=
                warning(
                    "PHONE_MISMATCH",
                    "The identities have different phone numbers. Verify the identity before resolving the duplicate.",
                )
        }

        val canProceed = items.none { it.severity == MergePlanSeverity.BLOCKER }
        val preview =
            DuplicateMergePreview(
                source = source,
                target = target,
                strategy = strategy,
                canProceedToMutationSlice = canProceed,
                dependencies = dependencies,
                sharedEvidence = sharedEvidence,
                plan = items,
                requiredSupportOrganizationId = requiredSupportOrganizationId,
            )
        return preview.copy(previewHash = DuplicatePreviewHasher.hash(preview))
    }

    private fun planShellLink(
        source: DuplicateIdentitySummary,
        target: DuplicateIdentitySummary,
        items: MutableList<MergePlanItem>,
    ): UUID? {
        if (source.status != "ACTIVE") {
            items += blocker("SHELL_NOT_ACTIVE", "Only an active guardian shell can be linked.")
        }
        if (source.linkedUserId != null) {
            items +=
                blocker(
                    "SHELL_ALREADY_LINKED",
                    "The guardian shell is already linked to an app user and cannot be linked again without resolving that relationship first.",
                )
        } else {
            items +=
                info(
                    "CREATE_GUARDIAN_RELATIONSHIP",
                    "Link the surviving app user to this existing guardian profile; the household-adult row remains in place.",
                )
            items +=
                info(
                    "NO_PROFILE_ROW_DELETE",
                    "The organization-owned guardian profile is preserved; linking does not delete or relocate household data.",
                )
        }
        if (target.platformAdministrator) {
            items += blocker("PLATFORM_ADMIN_TARGET", "A Platform Administrator identity cannot be used as the surviving customer account.")
        }
        if (target.status != "ACTIVE") {
            items += blocker("TARGET_USER_NOT_ACTIVE", "The surviving app user must be active before a guardian shell can be linked.")
        }
        if (target.mergedIntoUserId != null) {
            items += blocker("TARGET_USER_ALREADY_MERGED", "The selected target has already been merged into another account.")
        }
        if (source.organizationId == null) {
            items += blocker("SHELL_ORGANIZATION_MISSING", "The guardian shell has no organization scope and cannot be safely mutated.")
        }
        return source.organizationId
    }

    private fun planUserMerge(
        source: DuplicateIdentitySummary,
        target: DuplicateIdentitySummary,
        dependencies: List<IdentityDependency>,
        items: MutableList<MergePlanItem>,
    ): UUID? {
        if (source.platformAdministrator || target.platformAdministrator) {
            items +=
                blocker("PLATFORM_ADMIN_ACCOUNT", "Platform Administrator identities are excluded from customer-account merge operations.")
        }
        if (source.mergedIntoUserId != null) {
            items += blocker("SOURCE_USER_ALREADY_MERGED", "The source account has already been merged into another user.")
        }
        if (target.mergedIntoUserId != null) {
            items += blocker("TARGET_USER_ALREADY_MERGED", "The selected target has already been merged into another user.")
        }
        if (source.status == "SUSPENDED") {
            items += blocker("SOURCE_USER_SUSPENDED", "A suspended source account requires manual review before identity resolution.")
        }
        if (target.status != "ACTIVE") {
            items += blocker("TARGET_USER_NOT_ACTIVE", "The surviving user must be active.")
        }

        val sourceMemberships = source.memberships.filter { it.status != "REVOKED" }.associateBy { it.organizationId }
        val targetMemberships = target.memberships.associateBy { it.organizationId }
        sourceMemberships.forEach { (organizationId, sourceMembership) ->
            val targetMembership = targetMemberships[organizationId]
            when {
                targetMembership == null ->
                    items +=
                        info(
                            "MOVE_MEMBERSHIP_$organizationId",
                            "Move ${sourceMembership.organizationName} membership (${sourceMembership.role}, ${sourceMembership.status}) to the surviving user.",
                        )
                targetMembership.role == sourceMembership.role && targetMembership.status == sourceMembership.status ->
                    items +=
                        info(
                            "DEDUPE_MEMBERSHIP_$organizationId",
                            "Both users have the same ${sourceMembership.organizationName} membership; preserve the target row and revoke the duplicate source row.",
                        )
                else ->
                    items +=
                        blocker(
                            "MEMBERSHIP_CONFLICT_$organizationId",
                            "${sourceMembership.organizationName} has conflicting membership role/status values (${sourceMembership.role}/${sourceMembership.status} vs ${targetMembership.role}/${targetMembership.status}); no privilege union is automatic.",
                        )
            }
        }

        val targetAssignments = target.roleAssignments.groupBy { Triple(it.contextType, it.resourceId, it.organizationId) }
        source.roleAssignments.forEach { assignment ->
            val key = Triple(assignment.contextType, assignment.resourceId, assignment.organizationId)
            val atTarget = targetAssignments[key].orEmpty()
            when {
                atTarget.any { it.role == assignment.role } ->
                    items +=
                        info(
                            "DEDUPE_ROLE_${assignment.contextType}_${assignment.resourceId}_${assignment.role}",
                            "The surviving user already has the same ${assignment.contextType.lowercase()} role; revoke the duplicate source assignment.",
                        )
                atTarget.isNotEmpty() ->
                    items +=
                        blocker(
                            "ROLE_ASSIGNMENT_CONFLICT_${assignment.contextType}_${assignment.resourceId}",
                            "The source and target have different active roles for the same ${assignment.contextType.lowercase()} resource; review privileges manually.",
                        )
                else ->
                    items +=
                        info(
                            "MOVE_ROLE_${assignment.contextType}_${assignment.resourceId}_${assignment.role}",
                            "Move the source ${assignment.role} assignment to the surviving user.",
                        )
            }
        }

        val targetGuardianAdults = target.guardianLinks.map { it.householdAdultId }.toSet()
        source.guardianLinks.forEach { link ->
            items +=
                if (link.householdAdultId in targetGuardianAdults) {
                    info(
                        "DEDUPE_GUARDIAN_${link.householdAdultId}",
                        "The target is already linked to this guardian profile; revoke the duplicate source relationship.",
                    )
                } else {
                    info("MOVE_GUARDIAN_${link.householdAdultId}", "Move this active guardian relationship to the surviving user.")
                }
        }

        val sourceOrganizations =
            buildSet {
                source.memberships.filter { it.status != "REVOKED" }.forEach { add(it.organizationId) }
                source.roleAssignments.forEach { add(it.organizationId) }
                source.guardianLinks.forEach { add(it.organizationId) }
            }
        val supportOrganizationId =
            when (sourceOrganizations.size) {
                0 -> {
                    items +=
                        blocker(
                            "NO_SUPPORT_ORGANIZATION_SCOPE",
                            "This source account has no active tenant scope. Account merges require an organization-scoped support session.",
                        )
                    null
                }
                1 -> sourceOrganizations.single()
                else -> {
                    items +=
                        blocker(
                            "MULTI_ORGANIZATION_MERGE_REQUIRES_MANUAL_REVIEW",
                            "The source account currently affects ${sourceOrganizations.size} organizations. One Rally26 support session is scoped to one organization, so this merge is not automatic.",
                        )
                    null
                }
            }

        dependencies.filter { it.count > 0 }.forEach { dependency ->
            when {
                dependency.historical ->
                    items +=
                        info(
                            "PRESERVE_${dependency.tableName}_${dependency.columnName}",
                            "Preserve ${dependency.count} historical reference(s) in ${dependency.tableName}.${dependency.columnName}; attribution is not rewritten.",
                        )
                dependency.tableName to dependency.columnName in REASSIGNED_DEPENDENCIES ->
                    items +=
                        info(
                            "RESOLVE_${dependency.tableName}_${dependency.columnName}",
                            "Resolve ${dependency.count} current reference(s) in ${dependency.tableName}.${dependency.columnName} through the domain-specific merge path.",
                        )
                dependency.tableName to dependency.columnName in INVALIDATED_DEPENDENCIES ->
                    items +=
                        info(
                            "INVALIDATE_${dependency.tableName}_${dependency.columnName}",
                            "Invalidate ${dependency.count} active authentication reference(s) for the retired source account.",
                        )
                else ->
                    items +=
                        blocker(
                            "UNSUPPORTED_DEPENDENCY_${dependency.tableName}_${dependency.columnName}",
                            "${dependency.tableName}.${dependency.columnName} has ${dependency.count} source reference(s) that this merge tool does not explicitly migrate. The merge is blocked rather than guessing.",
                        )
            }
        }
        items +=
            info(
                "RETAIN_SOURCE_IDENTITY_HISTORY",
                "Retain the source app-user row as SUSPENDED with a merged-into pointer; do not hard-delete identity or audit history.",
            )
        return supportOrganizationId
    }

    private fun inferDirectEvidence(
        source: DuplicateIdentitySummary,
        target: DuplicateIdentitySummary,
    ): List<DuplicateMatchEvidence> {
        val evidence = mutableListOf<DuplicateMatchEvidence>()
        if (sameNonBlank(source.email, target.email)) {
            evidence += DuplicateMatchEvidence(DuplicateMatchType.EMAIL, source.email!!.trim().lowercase())
        }
        val sourcePhone = normalizePhone(source.phone)
        val targetPhone = normalizePhone(target.phone)
        if (sourcePhone != null && sourcePhone == targetPhone) {
            evidence += DuplicateMatchEvidence(DuplicateMatchType.PHONE, sourcePhone)
        }
        return evidence
    }

    private fun sameNonBlank(
        a: String?,
        b: String?,
    ) = !a.isNullOrBlank() && !b.isNullOrBlank() && a.trim().equals(b.trim(), ignoreCase = true)

    private fun samePhone(
        a: String?,
        b: String?,
    ) = normalizePhone(a) != null && normalizePhone(a) == normalizePhone(b)

    private fun normalizePhone(value: String?) = value?.filter(Char::isDigit)?.takeIf { it.length >= 7 }

    private fun info(
        code: String,
        summary: String,
    ) = MergePlanItem(code, MergePlanSeverity.INFO, summary)

    private fun warning(
        code: String,
        summary: String,
    ) = MergePlanItem(code, MergePlanSeverity.WARNING, summary)

    private fun blocker(
        code: String,
        summary: String,
    ) = MergePlanItem(code, MergePlanSeverity.BLOCKER, summary)

    private val REASSIGNED_DEPENDENCIES =
        setOf(
            "organization_membership" to "user_id",
            "role_assignment" to "user_id",
            "guardian_relationship" to "user_id",
            "message_thread_member" to "user_id",
            "message_recipient" to "user_id",
            "announcement_recipient" to "user_id",
        )
    private val INVALIDATED_DEPENDENCIES =
        setOf(
            "email_verification_token" to "user_id",
            "password_reset_token" to "user_id",
        )
}
