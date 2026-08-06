package com.rally26.credit.domain

import java.time.Instant
import java.util.UUID

enum class FamilyCreditGrantStatus { PENDING, AVAILABLE, EXPIRED, REVOKED }

enum class CreditSourceType { CAMPAIGN_ATTRIBUTION, ORG_PROMO, MANUAL, P2P_TRANSFER }

/**
 * One row per grant (Phase 23, DESIGN-DOC.md section 13/14.1). `remainingMinor`
 * decrements as the grant is applied to a fee or transferred, so a grant can be
 * partially consumed — real typed state over pure event sourcing, matching this
 * schema's existing convention (e.g. `fulfillment.status`).
 */
data class FamilyCreditGrant(
    val id: UUID,
    val organizationId: UUID,
    val householdId: UUID,
    val amountMinor: Long,
    val remainingMinor: Long,
    val currency: String,
    val status: FamilyCreditGrantStatus,
    val sourceType: CreditSourceType,
    val sourceId: UUID?,
    val grantedAt: Instant,
    val availableAt: Instant,
    val expiresAt: Instant?,
    val createdAt: Instant,
)

/** Available/pending/expiring-soon/applied summary for a household — replaces `ParentDashboardService`'s hardcoded demo block. */
data class FamilyCreditBalance(
    val currency: String,
    val availableMinor: Long,
    val pendingMinor: Long,
    val expiringSoonMinor: Long,
    /** All-time applied total — this codebase has no formal season-dates entity to bound a "this season" window by. */
    val appliedAllTimeMinor: Long,
    /** Whether this organization has P2P transfer enabled — surfaced here (not only via the staff-only credit-settings endpoint) so a pure guardian with no org membership can still know whether to show the transfer action. */
    val p2pTransferEnabled: Boolean,
)
