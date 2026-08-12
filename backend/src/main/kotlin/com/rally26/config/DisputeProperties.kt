package com.rally26.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Bound from `rally26.dispute.*`. [feeMinor] is a configured estimate of Stripe's own
 * non-refundable per-dispute fee (typically $15 as of 2026), not parsed from the
 * webhook payload — `Dispute.balanceTransactions`' exact shape isn't reliable enough
 * to parse without live verification against a real dispute, so this stays an
 * explicit, reconcilable estimate (DESIGN-DOC.md §14.6 item #4) rather than a
 * possibly-wrong automatic parse.
 */
@ConfigurationProperties(prefix = "rally26.dispute")
data class DisputeProperties(
    val feeMinor: Long = 1500,
)
