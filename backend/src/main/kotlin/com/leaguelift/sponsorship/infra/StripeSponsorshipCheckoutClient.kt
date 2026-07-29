package com.leaguelift.sponsorship.infra

import com.stripe.StripeClient
import com.stripe.param.checkout.SessionCreateParams
import org.springframework.stereotype.Component
import java.util.UUID

data class SponsorshipCheckoutSession(val sessionId: String, val checkoutUrl: String)

/**
 * Thin seam over the Stripe SDK's Checkout Session API, exactly mirroring
 * `fundraising/infra/StripeCheckoutClient.kt` — one-time payment mode, no
 * subscriptions/saved payment methods. `sponsorshipId` is set as session metadata so
 * `webhook/web/StripeWebhookController.kt` can disambiguate this checkout from a
 * contribution/order checkout without a second Stripe API call. No refund method here
 * (unlike `StripeCheckoutClient`/`StripeOrderCheckoutClient`) — refunds are explicitly
 * out of scope for this slice (ADR-018).
 */
@Component
class StripeSponsorshipCheckoutClient(private val stripeClient: StripeClient) {

	fun createSponsorshipCheckoutSession(
		sponsorshipId: UUID,
		amountMinor: Long,
		currency: String,
		packageName: String,
		successUrl: String,
		cancelUrl: String,
	): SponsorshipCheckoutSession {
		val params = SessionCreateParams.builder()
			.setMode(SessionCreateParams.Mode.PAYMENT)
			.setSuccessUrl(successUrl)
			.setCancelUrl(cancelUrl)
			.putMetadata("sponsorshipId", sponsorshipId.toString())
			.addLineItem(
				SessionCreateParams.LineItem.builder()
					.setQuantity(1L)
					.setPriceData(
						SessionCreateParams.LineItem.PriceData.builder()
							.setCurrency(currency.lowercase())
							.setUnitAmount(amountMinor)
							.setProductData(
								SessionCreateParams.LineItem.PriceData.ProductData.builder()
									.setName("Sponsorship: $packageName")
									.build(),
							)
							.build(),
					)
					.build(),
			)
			.build()
		val session = stripeClient.checkout().sessions().create(params)
		return SponsorshipCheckoutSession(sessionId = session.id, checkoutUrl = session.url)
	}
}
