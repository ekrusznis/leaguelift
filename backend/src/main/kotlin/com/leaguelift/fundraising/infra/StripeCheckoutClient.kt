package com.leaguelift.fundraising.infra

import com.stripe.StripeClient
import com.stripe.param.checkout.SessionCreateParams
import org.springframework.stereotype.Component
import java.util.UUID

data class CheckoutSession(val sessionId: String, val checkoutUrl: String)

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
class StripeCheckoutClient(private val stripeClient: StripeClient) {

	fun createContributionCheckoutSession(
		contributionId: UUID,
		amountMinor: Long,
		currency: String,
		campaignName: String,
		successUrl: String,
		cancelUrl: String,
	): CheckoutSession {
		val params = SessionCreateParams.builder()
			.setMode(SessionCreateParams.Mode.PAYMENT)
			.setSuccessUrl(successUrl)
			.setCancelUrl(cancelUrl)
			.putMetadata("contributionId", contributionId.toString())
			.addLineItem(
				SessionCreateParams.LineItem.builder()
					.setQuantity(1L)
					.setPriceData(
						SessionCreateParams.LineItem.PriceData.builder()
							.setCurrency(currency.lowercase())
							.setUnitAmount(amountMinor)
							.setProductData(
								SessionCreateParams.LineItem.PriceData.ProductData.builder()
									.setName("Contribution to $campaignName")
									.build(),
							)
							.build(),
					)
					.build(),
			)
			.build()
		val session = stripeClient.checkout().sessions().create(params)
		return CheckoutSession(sessionId = session.id, checkoutUrl = session.url)
	}
}
