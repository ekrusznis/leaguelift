package com.rally26.identityintegrity.application

import com.rally26.identityintegrity.domain.DuplicateIdentityKind
import com.rally26.identityintegrity.domain.DuplicateIdentitySummary
import com.rally26.identityintegrity.domain.DuplicateMergePreview
import com.rally26.identityintegrity.domain.DuplicateResolutionStrategy
import com.rally26.identityintegrity.domain.IdentityDependency
import com.rally26.identityintegrity.domain.MergePlanItem
import com.rally26.identityintegrity.domain.MergePlanSeverity

object DuplicateMergePlanner {
    fun plan(
        source: DuplicateIdentitySummary,
        target: DuplicateIdentitySummary,
        dependencies: List<IdentityDependency>,
    ): DuplicateMergePreview {
        require(source.ref != target.ref) { "Source and target identities must be different." }

        val items = mutableListOf<MergePlanItem>()
        val strategy =
            when {
                source.ref.kind == DuplicateIdentityKind.GUARDIAN_SHELL && target.ref.kind == DuplicateIdentityKind.APP_USER -> {
                    DuplicateResolutionStrategy.LINK_SHELL_TO_EXISTING_USER
                }

                source.ref.kind == DuplicateIdentityKind.APP_USER && target.ref.kind == DuplicateIdentityKind.APP_USER -> {
                    DuplicateResolutionStrategy.MERGE_USER_ACCOUNTS
                }

                source.ref.kind == DuplicateIdentityKind.GUARDIAN_SHELL && target.ref.kind == DuplicateIdentityKind.GUARDIAN_SHELL -> {
                    DuplicateResolutionStrategy.REVIEW_SHELLS_SEPARATELY
                }

                else -> {
                    DuplicateResolutionStrategy.KEEP_SEPARATE
                }
            }

        when (strategy) {
            DuplicateResolutionStrategy.LINK_SHELL_TO_EXISTING_USER -> {
                planShellLink(source, target, items)
            }

            DuplicateResolutionStrategy.MERGE_USER_ACCOUNTS -> {
                planUserMerge(source, target, dependencies, items)
            }

            DuplicateResolutionStrategy.REVIEW_SHELLS_SEPARATELY -> {
                items +=
                    blocker(
                        "SHELL_TO_SHELL_NO_ACCOUNT_TARGET",
                        "Two guardian shells are not merged directly. Review whether both should link to the same existing or newly activated adult account.",
                    )
            }

            DuplicateResolutionStrategy.KEEP_SEPARATE -> {
                items +=
                    blocker(
                        "TARGET_MUST_BE_APP_USER",
                        "A guardian shell cannot be the surviving account target. Reverse the preview or keep the records separate.",
                    )
            }
        }

        if (!sameNonBlank(source.email, target.email) && !source.email.isNullOrBlank() && !target.email.isNullOrBlank()) {
            items +=
                warning(
                    "EMAIL_MISMATCH",
                    "The identities have different email addresses. A reviewer must verify they belong to the same person before any mutation.",
                )
        }
        if (!samePhone(source.phone, target.phone) && !source.phone.isNullOrBlank() && !target.phone.isNullOrBlank()) {
            items +=
                warning(
                    "PHONE_MISMATCH",
                    "The identities have different phone numbers. A reviewer must verify the identity before any mutation.",
                )
        }

        val canProceed = items.none { it.severity == MergePlanSeverity.BLOCKER }
        return DuplicateMergePreview(source, target, strategy, canProceed, dependencies, items)
    }

    private fun planShellLink(
        source: DuplicateIdentitySummary,
        target: DuplicateIdentitySummary,
        items: MutableList<MergePlanItem>,
    ) {
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
                    "27.4 may link this existing app user to the guardian shell; the household and participant records remain in place.",
                )
            items +=
                info(
                    "NO_PROFILE_ROW_DELETE",
                    "The guardian shell remains organization-owned profile data; linking does not delete or relocate the household-adult row.",
                )
        }
        if (target.platformAdministrator) {
            items +=
                blocker(
                    "PLATFORM_ADMIN_TARGET",
                    "A platform administrator account cannot be used as the surviving customer/guardian identity.",
                )
        }
    }

    private fun planUserMerge(
        source: DuplicateIdentitySummary,
        target: DuplicateIdentitySummary,
        dependencies: List<IdentityDependency>,
        items: MutableList<MergePlanItem>,
    ) {
        if (source.platformAdministrator || target.platformAdministrator) {
            items +=
                blocker("PLATFORM_ADMIN_ACCOUNT", "Platform administrator identities are excluded from customer-account merge operations.")
        }

        val sourceMemberships = source.memberships.associateBy { it.organizationId }
        val targetMemberships = target.memberships.associateBy { it.organizationId }
        sourceMemberships.forEach { (organizationId, sourceMembership) ->
            val targetMembership = targetMemberships[organizationId]
            when {
                targetMembership == null -> {
                    items +=
                        info(
                            "MOVE_MEMBERSHIP_$organizationId",
                            "Move ${sourceMembership.organizationName} membership (${sourceMembership.role}, ${sourceMembership.status}) to the surviving user.",
                        )
                }

                targetMembership.role == sourceMembership.role && targetMembership.status == sourceMembership.status -> {
                    items +=
                        info(
                            "DEDUPE_MEMBERSHIP_$organizationId",
                            "Both users already have the same ${sourceMembership.organizationName} membership; 27.4 may preserve the target row and retire the duplicate source row.",
                        )
                }

                else -> {
                    items +=
                        blocker(
                            "MEMBERSHIP_CONFLICT_$organizationId",
                            "${sourceMembership.organizationName} has conflicting membership role/status values (${sourceMembership.role}/${sourceMembership.status} vs ${targetMembership.role}/${targetMembership.status}); no privilege union is automatic.",
                        )
                }
            }
        }

        dependencies.filter { it.count > 0 }.forEach { dependency ->
            if (dependency.historical) {
                items +=
                    info(
                        "PRESERVE_${dependency.tableName}_${dependency.columnName}",
                        "Preserve ${dependency.count} historical reference(s) in ${dependency.tableName}.${dependency.columnName}; audit/history attribution must not be rewritten.",
                    )
            } else {
                items +=
                    info(
                        "REPOINT_${dependency.tableName}_${dependency.columnName}",
                        "27.4 must explicitly resolve ${dependency.count} reference(s) in ${dependency.tableName}.${dependency.columnName} before the source account can be retired.",
                    )
            }
        }
        items +=
            info(
                "RETAIN_SOURCE_IDENTITY_HISTORY",
                "The source app-user record must remain traceable after merge; 27.4 must not hard-delete identity or audit history.",
            )
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
}
