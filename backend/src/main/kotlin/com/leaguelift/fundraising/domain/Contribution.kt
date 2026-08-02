package com.leaguelift.fundraising.domain

import com.leaguelift.finance.domain.PaymentSource
import java.time.Instant
import java.util.UUID

enum class ContributionStatus { PENDING, CONFIRMED, CANCELED, REFUNDED }

data class Contribution(
	val id: UUID,
	val organizationId: UUID,
	val campaignId: UUID,
	val amountMinor: Long,
	val currency: String,
	val supporterName: String?,
	val isAnonymous: Boolean,
	val supporterEmail: String?,
	val status: ContributionStatus,
	val stripeCheckoutSessionId: String?,
	val stripePaymentIntentId: String?,
	val confirmedAt: Instant?,
	val refundedAt: Instant?,
	val createdAt: Instant,
	val paymentSource: PaymentSource = PaymentSource.STRIPE,
)

/**
 * Bounds on a public, unauthenticated checkout-session request — this is the only
 * money-moving endpoint in the app nobody has to be logged in to hit, so it needs
 * its own sanity limits independent of any campaign goal (DESIGN-DOC.md section 16
 * money rules; mirrors `media/domain/UploadLimits.kt`'s pattern of pure, directly
 * unit-testable validation constants).
 */
object ContributionLimits {
	const val MIN_AMOUNT_MINOR = 100L
	const val MAX_AMOUNT_MINOR = 5_000_000L

	fun isAmountAllowed(amountMinor: Long): Boolean = amountMinor in MIN_AMOUNT_MINOR..MAX_AMOUNT_MINOR
}
