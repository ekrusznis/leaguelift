package com.rally26.order.infra

import com.stripe.StripeClient
import com.stripe.net.RequestOptions
import com.stripe.param.RefundCreateParams
import com.stripe.param.checkout.SessionCreateParams
import org.springframework.stereotype.Component
import java.util.UUID

data class OrderCheckoutLineItem(val name: String, val quantity: Long, val unitPriceMinor: Long, val currency: String)

data class OrderCheckoutSession(val sessionId: String, val checkoutUrl: String)

/**
 * Thin seam over the Stripe SDK's Checkout Session API — sibling to
 * `fundraising/infra/StripeCheckoutClient.kt`, kept separate because an order
 * needs multiple line items and US shipping-address collection, which a single
 * contribution amount never does. Stripe hosts the shipping-address form itself
 * (US-only for this proof); we read the collected address back off the
 * confirmed session in the webhook handler, not from a form we built.
 */
@Component
class StripeOrderCheckoutClient(private val stripeClient: StripeClient) {

	fun createOrderCheckoutSession(
		orderId: UUID,
		lineItems: List<OrderCheckoutLineItem>,
		successUrl: String,
		cancelUrl: String,
	): OrderCheckoutSession {
		val builder = SessionCreateParams.builder()
			.setMode(SessionCreateParams.Mode.PAYMENT)
			.setSuccessUrl(successUrl)
			.setCancelUrl(cancelUrl)
			.putMetadata("orderId", orderId.toString())
			.setShippingAddressCollection(
				SessionCreateParams.ShippingAddressCollection.builder()
					.addAllowedCountry(SessionCreateParams.ShippingAddressCollection.AllowedCountry.US)
					.build(),
			)
		lineItems.forEach { item ->
			builder.addLineItem(
				SessionCreateParams.LineItem.builder()
					.setQuantity(item.quantity)
					.setPriceData(
						SessionCreateParams.LineItem.PriceData.builder()
							.setCurrency(item.currency.lowercase())
							.setUnitAmount(item.unitPriceMinor)
							.setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder().setName(item.name).build())
							.build(),
					)
					.build(),
			)
		}
		val session = stripeClient.checkout().sessions().create(builder.build())
		return OrderCheckoutSession(sessionId = session.id, checkoutUrl = session.url)
	}

	/** `reverseTransfer = false` — see StripeCheckoutClient.createRefund's identical note on ADR-017's negative-balance handling. */
	fun createRefund(paymentIntentId: String, amountMinor: Long, idempotencyKey: String): String {
		val params = RefundCreateParams.builder()
			.setPaymentIntent(paymentIntentId)
			.setAmount(amountMinor)
			.setReverseTransfer(false)
			.build()
		val options = RequestOptions.builder().setIdempotencyKey(idempotencyKey).build()
		return stripeClient.refunds().create(params, options).id
	}

	fun createRefund(paymentIntentId: String): String {
		val refund = stripeClient.refunds().create(
			RefundCreateParams.builder()
				.setPaymentIntent(paymentIntentId)
				.setReverseTransfer(false)
				.build(),
		)
		return refund.id
	}
}
