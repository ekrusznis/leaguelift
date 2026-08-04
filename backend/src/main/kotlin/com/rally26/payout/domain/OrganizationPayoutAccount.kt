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
) {
	/** Both flags true is the practical "fully connected, can receive payouts" signal used for the onboarding checklist. */
	val isFullyConnected: Boolean get() = chargesEnabled && payoutsEnabled
}
