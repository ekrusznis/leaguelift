package com.rally26.ledger.infra

import com.stripe.StripeClient
import com.stripe.param.PaymentIntentRetrieveParams
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

private val log = LoggerFactory.getLogger(StripeBalanceTransactionClient::class.java)

/**
 * Thin seam over the Stripe SDK so no other file imports Stripe types directly
 * (mirrors every other `*Client` in this codebase). Fetches Stripe's own real
 * processing fee for a confirmed charge, for internal margin visibility only —
 * see `LedgerService.recordStripeProcessingFee`. Never blocks a payment
 * confirmation: any failure (network, missing charge, not-yet-settled balance
 * transaction) returns null rather than throwing, since this is observability,
 * not a payment-critical path.
 */
@Component
class StripeBalanceTransactionClient(
    private val stripeClient: StripeClient,
) {
    fun fetchFeeMinor(stripePaymentIntentId: String): Long? =
        runCatching {
            val paymentIntent =
                stripeClient.paymentIntents().retrieve(
                    stripePaymentIntentId,
                    PaymentIntentRetrieveParams
                        .builder()
                        .addExpand("latest_charge.balance_transaction")
                        .build(),
                )
            paymentIntent.latestChargeObject?.balanceTransactionObject?.fee
        }.onFailure {
            log.warn("Could not fetch Stripe's real processing fee for payment intent {}: {}", stripePaymentIntentId, it.message)
        }.getOrNull()
}
