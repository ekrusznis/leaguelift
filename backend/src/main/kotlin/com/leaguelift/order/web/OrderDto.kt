package com.leaguelift.order.web

import com.leaguelift.order.application.OrderCheckout
import com.leaguelift.order.application.OrderLineItemRequest
import com.leaguelift.order.domain.Fulfillment
import com.leaguelift.order.domain.Order
import com.leaguelift.order.domain.ShippingAddress
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class OrderLineItemDto(
	@field:NotNull val productVariantId: UUID,
	@field:NotNull @field:Min(1) val quantity: Int,
) {
	fun toRequest() = OrderLineItemRequest(productVariantId, quantity)
}

data class CreateOrderCheckoutRequest(
	@field:NotEmpty @field:Valid val items: List<OrderLineItemDto>,
	@field:Size(max = 120) val supporterName: String? = null,
	@field:Email @field:Size(max = 254) val supporterEmail: String? = null,
	@field:NotBlank val successUrl: String,
	@field:NotBlank val cancelUrl: String,
)

data class OrderCheckoutResponse(val orderId: UUID, val checkoutUrl: String)

fun OrderCheckout.toResponse() = OrderCheckoutResponse(orderId, checkoutUrl)

data class ShippingAddressResponse(val name: String?, val line1: String?, val line2: String?, val city: String?, val state: String?, val postalCode: String?, val country: String?)

fun ShippingAddress.toResponse() = ShippingAddressResponse(name, line1, line2, city, state, postalCode, country)

/** Public status-poll shape — no supporter contact info exposed back to the browser (mirrors ContributionStatusResponse). */
data class OrderStatusResponse(val id: UUID, val status: String, val currency: String, val confirmedAt: Instant?)

fun Order.toStatusResponse() = OrderStatusResponse(id, status.name, currency, confirmedAt)

/** Org-admin shape. */
data class OrderResponse(
	val id: UUID,
	val storeId: UUID,
	val status: String,
	val currency: String,
	val supporterName: String?,
	val supporterEmail: String?,
	val shippingAddress: ShippingAddressResponse?,
	val confirmedAt: Instant?,
	val refundedAt: Instant?,
	val createdAt: Instant,
)

fun Order.toResponse() = OrderResponse(id, storeId, status.name, currency, supporterName, supporterEmail, shippingAddress?.toResponse(), confirmedAt, refundedAt, createdAt)

data class FulfillmentResponse(val status: String, val printifyOrderId: String?, val lastError: String?)

fun Fulfillment.toResponse() = FulfillmentResponse(status.name, printifyOrderId, lastError)
