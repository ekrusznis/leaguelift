package com.leaguelift.order.domain

import java.time.Instant
import java.util.UUID

enum class OrderStatus { PENDING, CONFIRMED, CANCELED, REFUNDED }

/** Collected by Stripe Checkout's own shipping_address_collection, not a form we built (see order/application/OrderService.kt). */
data class ShippingAddress(
	val name: String?,
	val line1: String?,
	val line2: String?,
	val city: String?,
	val state: String?,
	val postalCode: String?,
	val country: String?,
)

data class Order(
	val id: UUID,
	val organizationId: UUID,
	val storeId: UUID,
	val status: OrderStatus,
	val currency: String,
	val supporterName: String?,
	val supporterEmail: String?,
	val shippingAddress: ShippingAddress?,
	val stripeCheckoutSessionId: String?,
	val stripePaymentIntentId: String?,
	val confirmedAt: Instant?,
	val refundedAt: Instant?,
	val createdAt: Instant,
)
