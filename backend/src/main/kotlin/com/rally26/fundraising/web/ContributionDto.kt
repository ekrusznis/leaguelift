package com.rally26.fundraising.web

import com.rally26.fundraising.application.ContributionCheckout
import com.rally26.fundraising.domain.Contribution
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class CreateContributionCheckoutRequest(
    @field:NotNull @field:Min(1) val amountMinor: Long,
    @field:Size(max = 120) val supporterName: String? = null,
    val isAnonymous: Boolean = false,
    @field:Email @field:Size(max = 254) val supporterEmail: String? = null,
    @field:NotBlank val successUrl: String,
    @field:NotBlank val cancelUrl: String,
)

data class ContributionCheckoutResponse(
    val contributionId: UUID,
    val checkoutUrl: String,
)

fun ContributionCheckout.toResponse() = ContributionCheckoutResponse(contributionId, checkoutUrl)

/** Public status-poll shape (`GET /public/campaigns/{slug}/contributions/{contributionId}`) — no supporter contact info exposed back to the browser. */
data class ContributionStatusResponse(
    val id: UUID,
    val status: String,
    val amountMinor: Long,
    val currency: String,
    val confirmedAt: Instant?,
)

fun Contribution.toStatusResponse() = ContributionStatusResponse(id, status.name, amountMinor, currency, confirmedAt)

/** Org-admin list shape (`GET /organizations/{id}/campaigns/{campaignId}/contributions`). */
data class ContributionResponse(
    val id: UUID,
    val status: String,
    val paymentSource: String,
    val amountMinor: Long,
    val currency: String,
    val supporterName: String?,
    val isAnonymous: Boolean,
    val supporterEmail: String?,
    val confirmedAt: Instant?,
    val refundedAt: Instant?,
    val createdAt: Instant,
)

fun Contribution.toResponse() =
    ContributionResponse(
        id,
        status.name,
        paymentSource.name,
        amountMinor,
        currency,
        supporterName,
        isAnonymous,
        supporterEmail,
        confirmedAt,
        refundedAt,
        createdAt,
    )
