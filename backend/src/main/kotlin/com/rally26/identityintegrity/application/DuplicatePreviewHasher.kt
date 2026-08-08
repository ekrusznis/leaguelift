package com.rally26.identityintegrity.application

import com.rally26.identityintegrity.domain.DuplicateIdentitySummary
import com.rally26.identityintegrity.domain.DuplicateMergePreview
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

object DuplicatePreviewHasher {
    fun hash(preview: DuplicateMergePreview): String {
        val canonical =
            buildString {
                appendIdentity(preview.source)
                append('|')
                appendIdentity(preview.target)
                append('|').append(preview.strategy.name)
                append('|').append(preview.requiredSupportOrganizationId ?: "-")
                preview.sharedEvidence.sortedWith(compareBy({ it.matchType.name }, { it.normalizedValue })).forEach {
                    append("|evidence:").append(it.matchType.name).append(':').append(it.normalizedValue)
                }
                preview.dependencies.sortedWith(compareBy({ it.tableName }, { it.columnName })).forEach {
                    append(
                        "|dep:",
                    ).append(it.tableName).append(':').append(it.columnName).append(':').append(it.count).append(':').append(it.historical)
                }
                preview.plan.forEach {
                    append("|plan:").append(it.code).append(':').append(it.severity.name)
                }
            }
        return MessageDigest
            .getInstance("SHA-256")
            .digest(canonical.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun StringBuilder.appendIdentity(identity: DuplicateIdentitySummary) {
        append(identity.ref.kind.name).append(':').append(identity.ref.id)
        append(':').append(identity.status)
        append(':').append(identity.email?.trim()?.lowercase() ?: "-")
        append(':').append(identity.phone?.filter(Char::isDigit) ?: "-")
        append(':').append(identity.linkedUserId ?: "-")
        append(':').append(identity.platformAdministrator)
        append(':').append(identity.mergedIntoUserId ?: "-")
        identity.memberships.sortedBy { it.organizationId.toString() }.forEach {
            append("|m:")
                .append(it.organizationId)
                .append(':')
                .append(it.role)
                .append(':')
                .append(it.status)
        }
        identity.roleAssignments
            .sortedWith(
                compareBy({
                    it.organizationId.toString()
                }, { it.contextType }, { it.resourceId.toString() }, { it.role }),
            ).forEach {
                append(
                    "|r:",
                ).append(it.organizationId).append(':').append(it.contextType).append(':').append(it.resourceId).append(':').append(it.role)
            }
        identity.guardianLinks.sortedWith(compareBy({ it.organizationId.toString() }, { it.householdAdultId.toString() })).forEach {
            append("|g:")
                .append(it.organizationId)
                .append(':')
                .append(it.householdId)
                .append(':')
                .append(it.householdAdultId)
        }
    }
}
