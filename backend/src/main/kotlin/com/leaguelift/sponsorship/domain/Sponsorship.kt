package com.leaguelift.sponsorship.domain

import java.time.Instant
import java.util.UUID

/** No REFUNDED-by-way-of-active-refund-flow this slice (refunds are design target — see ADR-018) — the status exists so a later slice's refund flow has somewhere to land without a schema change, mirroring how `ContributionStatus`/`OrderStatus` already carry `REFUNDED`. */
enum class SponsorshipStatus { PENDING, CONFIRMED, REFUNDED }

/**
 * One purchased sponsorship of a [SponsorshipPackage] by a [Sponsor] — mirrors
 * `fundraising/domain/Contribution.kt`'s shape closely (PENDING row inserted before
 * Stripe is ever called, CONFIRMED only via the webhook). [amountMinor]/[currency] are
 * snapshotted from the package's price at purchase time rather than re-read from the
 * package later, so a subsequent price change on the package never retroactively
 * changes what an already-purchased sponsorship is worth (same snapshot rationale as
 * `order_item.unit_price_minor`).
 */
data class Sponsorship(
	val id: UUID,
	val organizationId: UUID,
	val packageId: UUID,
	val sponsorId: UUID,
	val amountMinor: Long,
	val currency: String,
	val status: SponsorshipStatus,
	val stripeCheckoutSessionId: String?,
	val stripePaymentIntentId: String?,
	val confirmedAt: Instant?,
	val createdAt: Instant,
)
