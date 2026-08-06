package com.rally26.credit.domain

import java.time.Instant
import java.util.UUID

enum class CreditExpirationPolicy { ROLLOVER, EXPIRES }

/**
 * Satellite 1:1 org settings (Phase 23), mirroring `organization_payout_account`'s
 * shape. "Post-season expiration" is simplified to a configurable N-month window
 * since this codebase has no formal season-dates entity. `p2pTransferEnabled`
 * defaults false — amending section 1's Credit boundary hard rule only for an
 * organization that explicitly opts in.
 */
data class OrganizationCreditSettings(
    val id: UUID,
    val organizationId: UUID,
    val defaultCreditPercent: Int,
    val expirationPolicy: CreditExpirationPolicy,
    val expirationMonths: Int?,
    val p2pTransferEnabled: Boolean,
    val updatedAt: Instant,
) {
    companion object {
        /** Used when an org hasn't configured anything yet — a reasonable starter value, per founder direction to ship defaults and iterate. */
        const val DEFAULT_CREDIT_PERCENT = 1000
    }
}
