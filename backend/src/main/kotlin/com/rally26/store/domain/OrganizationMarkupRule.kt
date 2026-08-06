package com.rally26.store.domain

import java.time.Instant
import java.util.UUID

enum class MarkupType { PERCENTAGE, FLAT }

/**
 * Swag Shop markup rule (Phase 23, DESIGN-DOC.md section 13/14.1). `printifyBlueprintId
 * == null` means the org-wide default; non-null is a per-apparel-type override.
 * `markupValue` is basis points for PERCENTAGE (3000 = 30.00%) or minor-unit cents
 * for FLAT.
 */
data class OrganizationMarkupRule(
    val id: UUID,
    val organizationId: UUID,
    val printifyBlueprintId: Long?,
    val markupType: MarkupType,
    val markupValue: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        /** Used when an org has configured no markup rules at all — a reasonable starter value, per founder direction to ship defaults and iterate. */
        const val SYSTEM_DEFAULT_MARKUP_PERCENT_BASIS_POINTS = 4000
    }
}
