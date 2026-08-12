package com.rally26.fee.infra

import com.stripe.StripeClient
import com.stripe.param.checkout.SessionCreateParams
import org.springframework.stereotype.Component
import java.util.UUID

data class FeePaymentCheckoutSession(
    val sessionId: String,
    val checkoutUrl: String,
)

/**
 * Thin seam over the Stripe SDK's Checkout Session API so no other file in the
 * fee module imports Stripe types directly (mirrors
 * `fundraising/infra/StripeCheckoutClient.kt`). One-time payment mode only. The
 * `feePaymentId` is set as session metadata so the webhook handler
 * (`webhook/web/StripeWebhookController.kt`) can map `checkout.session.completed`
 * back to our `fee_payment` row without a second Stripe API call.
 */
@Component
class StripeFeePaymentCheckoutClient(
    private val stripeClient: StripeClient,
) {
    fun createFeePaymentCheckoutSession(
        feePaymentId: UUID,
        amountMinor: Long,
        currency: String,
        feeDescription: String,
        successUrl: String,
        cancelUrl: String,
    ): FeePaymentCheckoutSession {
        val params =
            SessionCreateParams
                .builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(successUrl)
                .setCancelUrl(cancelUrl)
                .putMetadata("feePaymentId", feePaymentId.toString())
                .addLineItem(
                    SessionCreateParams.LineItem
                        .builder()
                        .setQuantity(1L)
                        .setPriceData(
                            SessionCreateParams.LineItem.PriceData
                                .builder()
                                .setCurrency(currency.lowercase())
                                .setUnitAmount(amountMinor)
                                .setProductData(
                                    SessionCreateParams.LineItem.PriceData.ProductData
                                        .builder()
                                        .setName(feeDescription)
                                        .build(),
                                ).build(),
                        ).build(),
                ).build()
        val session = stripeClient.checkout().sessions().create(params)
        return FeePaymentCheckoutSession(sessionId = session.id, checkoutUrl = session.url)
    }
}
