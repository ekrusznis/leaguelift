package com.rally26.sponsorship.domain

import com.rally26.finance.domain.PaymentSource
import java.time.Instant
import java.util.UUID

data class SponsorshipSearchCriteria(
    val keyword: String? = null,
    val packageId: UUID? = null,
    val status: SponsorshipStatus? = null,
    val reviewStatus: SponsorshipReviewStatus? = null,
    val paymentSource: PaymentSource? = null,
    val sort: SponsorshipSearchSort = SponsorshipSearchSort.NEWEST,
)

enum class SponsorshipSearchSort { NEWEST, OLDEST, SPONSOR_ASC, AMOUNT_ASC, AMOUNT_DESC, PACKAGE_ASC, REVIEW_STATUS_ASC }

/**
 * `/sponsorships/search`'s flat result row — the frontend needs sponsor and package
 * names alongside the sponsorship itself (`SponsorshipSearchItem`), a different, flatter
 * shape than [SponsorshipWithSponsor]'s nested one.
 */
data class SponsorshipSearchRow(
    val id: UUID,
    val packageId: UUID,
    val packageName: String,
    val status: SponsorshipStatus,
    val paymentSource: PaymentSource,
    val amountMinor: Long,
    val currency: String,
    val sponsorId: UUID,
    val sponsorName: String,
    val sponsorContactEmail: String?,
    val sponsorCompanyName: String?,
    val confirmedAt: Instant?,
    val refundedAt: Instant?,
    val reviewStatus: SponsorshipReviewStatus,
    val reviewedAt: Instant?,
    val createdAt: Instant,
)
