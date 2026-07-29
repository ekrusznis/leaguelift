package com.leaguelift.sponsorship.domain

import java.time.Instant
import java.util.UUID

/** Refunds are now real as of Phase 6 remainder (ADR-019) — `refund()`/`reject()` on `SponsorshipService` reach `REFUNDED`, mirroring `ContributionStatus`/`OrderStatus`. */
enum class SponsorshipStatus { PENDING, CONFIRMED, REFUNDED }

/**
 * The org-admin approval gate added in Phase 6 remainder (ADR-019) — deliberately a
 * separate column from [SponsorshipStatus] rather than folded into it, because payment
 * status and review status evolve independently: a sponsorship can be CONFIRMED and
 * still PENDING_REVIEW (paid, awaiting approval, not yet on the public directory), or
 * CONFIRMED and REJECTED (which also triggers a refund — see
 * `SponsorshipService.reject` — landing the sponsorship at status=REFUNDED,
 * reviewStatus=REJECTED simultaneously). A PENDING (unpaid) sponsorship's review status
 * is meaningless until it confirms; it simply carries the PENDING_REVIEW default until
 * then.
 */
enum class SponsorshipReviewStatus { PENDING_REVIEW, APPROVED, REJECTED }

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
	// Defaults below keep pre-existing test call sites (LedgerServiceTest,
	// StripeWebhookControllerTest — neither exercises the review/refund workflow)
	// compiling without churn; every real write path (SponsorshipRepository) always
	// sets these explicitly via named parameters.
	val refundedAt: Instant? = null,
	val reviewStatus: SponsorshipReviewStatus = SponsorshipReviewStatus.PENDING_REVIEW,
	val reviewedAt: Instant? = null,
	val reviewedById: UUID? = null,
	val renewalReminderSentAt: Instant? = null,
	val createdAt: Instant,
)
