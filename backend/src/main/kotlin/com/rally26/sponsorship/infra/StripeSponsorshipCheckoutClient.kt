package com.rally26.sponsorship.infra

import com.rally26.common.stripe.StripeTaxSupport
import com.rally26.config.StripeTaxProperties
import com.stripe.StripeClient
import com.stripe.net.RequestOptions
import com.stripe.param.RefundCreateParams
import com.stripe.param.checkout.SessionCreateParams
import org.springframework.stereotype.Component
import java.util.UUID

data class SponsorshipCheckoutSession(
    val sessionId: String,
    val checkoutUrl: String,
)

/**
 * Thin seam over the Stripe SDK's Checkout Session API, exactly mirroring
 * `fundraising/infra/StripeCheckoutClient.kt` — one-time payment mode, no
 * subscriptions/saved payment methods. `sponsorshipId` is set as session metadata so
 * `webhook/web/StripeWebhookController.kt` can disambiguate this checkout from a
 * contribution/order checkout without a second Stripe API call. [createRefund] was
 * added in Phase 6 remainder (ADR-019) — refunds were explicitly out of scope for
 * Phase 6 slice 1 (ADR-018).
 */
@Component
class StripeSponsorshipCheckoutClient(
    private val stripeClient: StripeClient,
    private val stripeTaxProperties: StripeTaxProperties,
) {
    fun createSponsorshipCheckoutSession(
        sponsorshipId: UUID,
        amountMinor: Long,
        currency: String,
        packageName: String,
        successUrl: String,
        cancelUrl: String,
    ): SponsorshipCheckoutSession {
        val builder =
            SessionCreateParams
                .builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(successUrl)
                .setCancelUrl(cancelUrl)
                .putMetadata("sponsorshipId", sponsorshipId.toString())
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
                                    StripeTaxSupport.applyTaxCode(
                                        SessionCreateParams.LineItem.PriceData.ProductData
                                            .builder()
                                            .setName("Sponsorship: $packageName"),
                                        stripeTaxProperties.sponsorshipTaxCode,
                                    ).build(),
                                ).build(),
                        ).build(),
                )
        // No shipping address is collected for a sponsorship, so automatic_tax needs billing-address collection instead.
        if (stripeTaxProperties.sponsorshipEnabled) {
            StripeTaxSupport.applyAutomaticTax(builder, collectBillingAddress = true)
        }
        val session = stripeClient.checkout().sessions().create(builder.build())
        return SponsorshipCheckoutSession(sessionId = session.id, checkoutUrl = session.url)
    }

    /** `reverseTransfer = false` — see `StripeCheckoutClient.createRefund`'s identical note on ADR-017's negative-balance handling, which this Phase 6 remainder refund flow reuses unmodified. */
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
