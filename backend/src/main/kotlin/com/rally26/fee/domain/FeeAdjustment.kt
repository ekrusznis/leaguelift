package com.rally26.fee.domain

import java.time.Instant
import java.util.UUID

/**
 * Manual, admin-issued only this phase — not DESIGN-DOC.md section 13's full
 * attribution-sourced Family Credits system (merchandise/campaign/sponsorship
 * attribution), which depends on fundraising-contribution and apparel-purchase data
 * that doesn't exist yet.
 */
enum class AdjustmentType { DISCOUNT, CREDIT, CORRECTION }

data class FeeAdjustment(
    val id: UUID,
    val organizationId: UUID,
    val feeAssignmentId: UUID,
    val householdId: UUID,
    val adjustmentType: AdjustmentType,
    val amountMinor: Long,
    val currency: String,
    val reason: String?,
    val createdByUserId: UUID,
    val voidedAt: Instant?,
    val voidedByUserId: UUID?,
    val voidReason: String?,
    val createdAt: Instant,
) {
    val isVoided: Boolean get() = voidedAt != null
}
