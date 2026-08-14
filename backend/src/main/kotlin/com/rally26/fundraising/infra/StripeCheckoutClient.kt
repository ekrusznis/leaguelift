package com.rally26.fundraising.infra

import com.rally26.common.stripe.StripeTaxSupport
import com.rally26.config.StripeTaxProperties
import com.stripe.StripeClient
import com.stripe.net.RequestOptions
import com.stripe.param.RefundCreateParams
import com.stripe.param.checkout.SessionCreateParams
import org.springframework.stereotype.Component
import java.util.UUID

data class CheckoutSession(
    val sessionId: String,
    val checkoutUrl: String,
)

/**
 * Thin seam over the Stripe SDK's Checkout Session API so no other file in the
 * fundraising module imports Stripe types directly (mirrors
 * `payout/infra/StripeConnectClient.kt`'s seam over the Connect API). One-time
 * payment mode only — no subscriptions, no saved payment methods. The
 * `contributionId` is set as session metadata so the webhook handler
 * (`webhook/web/StripeWebhookController.kt`) can map `checkout.session.completed`
 * back to our `contribution` row without a second Stripe API call.
 */
@Component
class StripeCheckoutClient(
    private val stripeClient: StripeClient,
    private val stripeTaxProperties: StripeTaxProperties,
) {
    fun createContributionCheckoutSession(
        contributionId: UUID,
        amountMinor: Long,
        currency: String,
        campaignName: String,
        successUrl: String,
        cancelUrl: String,
    ): CheckoutSession {
        val builder =
            SessionCreateParams
                .builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(successUrl)
                .setCancelUrl(cancelUrl)
                .putMetadata("contributionId", contributionId.toString())
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
                                    StripeTaxSupport
                                        .applyTaxCode(
                                            SessionCreateParams.LineItem.PriceData.ProductData
                                                .builder()
                                                .setName("Contribution to $campaignName"),
                                            stripeTaxProperties.contributionTaxCode,
                                        ).build(),
                                ).build(),
                        ).build(),
                )
        // No shipping address is collected for a contribution, so automatic_tax needs billing-address collection instead.
        if (stripeTaxProperties.contributionEnabled) {
            StripeTaxSupport.applyAutomaticTax(builder, collectBillingAddress = true)
        }
        val session = stripeClient.checkout().sessions().create(builder.build())
        return CheckoutSession(sessionId = session.id, checkoutUrl = session.url)
    }

    /**
     * `reverseTransfer = false` — this slice's negative-balance handling
     * (ADR-017) nets a refund against the org's own next payout entirely through
     * our own ledger, so Stripe must not also automatically claw the transferred
     * amount back from the connected account itself, which would double-count it.
     */
    fun createRefund(
        paymentIntentId: String,
        amountMinor: Long,
        idempotencyKey: String,
    ): String {
        val params =
            RefundCreateParams
                .builder()
                .setPaymentIntent(paymentIntentId)
                .setAmount(amountMinor)
                .setReverseTransfer(false)
                .build()
        val options = RequestOptions.builder().setIdempotencyKey(idempotencyKey).build()
        return stripeClient.refunds().create(params, options).id
    }

    fun createRefund(paymentIntentId: String): String {
        val refund =
            stripeClient.refunds().create(
                RefundCreateParams
                    .builder()
                    .setPaymentIntent(paymentIntentId)
                    .setReverseTransfer(false)
                    .build(),
            )
        return refund.id
    }
}
