package com.rally26.order.domain

import com.rally26.finance.domain.PaymentSource
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
    val paymentSource: PaymentSource = PaymentSource.STRIPE,
    /** Phase 24 slice 24.3: set only for an order placed through a published athlete storefront, resolved and snapshotted before Stripe is ever called (mirrors Contribution.attributedHouseholdId). Null for every other order. */
    val attributedHouseholdId: UUID? = null,
)
