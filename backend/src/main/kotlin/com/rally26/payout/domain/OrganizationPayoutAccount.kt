package com.rally26.payout.domain

import java.time.Instant
import java.util.UUID

/**
 * Mirrors Stripe's own Connect account object rather than inventing a separate status
 * enum — Stripe is the source of truth, this is a synchronized record (DESIGN-DOC.md
 * section 16, ADR-005).
 */
data class OrganizationPayoutAccount(
    val id: UUID,
    val organizationId: UUID,
    val stripeAccountId: String,
    val detailsSubmitted: Boolean,
    val chargesEnabled: Boolean,
    val payoutsEnabled: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
    // Appended with a default so older positional test fixtures remain source-compatible.
    // Populated by Stripe's own account.requirements.disabled_reason (Phase 37.8, ADR-117)
    // — set only when Stripe has actually restricted the account (e.g. "requirements.past_due",
    // "listed", "rejected.fraud"), synced via the account.updated webhook, not the manual refresh alone.
    val disabledReason: String? = null,
) {
    /** Both flags true is the practical "fully connected, can receive payouts" signal used for the onboarding checklist. */
    val isFullyConnected: Boolean get() = chargesEnabled && payoutsEnabled
}
