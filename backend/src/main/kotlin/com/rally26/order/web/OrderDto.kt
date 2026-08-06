package com.rally26.order.web

import com.rally26.order.application.OrderCheckout
import com.rally26.order.application.OrderLineItemRequest
import com.rally26.order.domain.Fulfillment
import com.rally26.order.domain.FulfillmentHistory
import com.rally26.order.domain.FulfillmentReprint
import com.rally26.order.domain.FulfillmentReprintStatus
import com.rally26.order.domain.FulfillmentStatus
import com.rally26.order.domain.Order
import com.rally26.order.domain.PersonalizationPlacement
import com.rally26.order.domain.ShippingAddress
import com.rally26.order.domain.SwagLogoSize
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
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

/** Swag Shop (DESIGN-DOC.md section 13): authenticated single-item order, Path 1/Quick — no cart, no successUrl/cancelUrl from the caller (the in-app return destination is fixed, see OrderService.createSwagShopCheckoutSession). */
data class CreateSwagShopOrderRequest(
    @field:NotNull val productVariantId: UUID,
    @field:NotNull val participantId: UUID,
    @field:Size(max = 60) val personalizationName: String? = null,
    @field:Size(max = 20) val personalizationNumber: String? = null,
    val personalizationPlacement: PersonalizationPlacement? = null,
    val personalizationLogoSize: SwagLogoSize? = null,
)

data class OrderCheckoutResponse(
    val orderId: UUID,
    val checkoutUrl: String,
)

fun OrderCheckout.toResponse() = OrderCheckoutResponse(orderId, checkoutUrl)

data class ShippingAddressResponse(
    val name: String?,
    val line1: String?,
    val line2: String?,
    val city: String?,
    val state: String?,
    val postalCode: String?,
    val country: String?,
)

fun ShippingAddress.toResponse() = ShippingAddressResponse(name, line1, line2, city, state, postalCode, country)

data class OrderStatusResponse(
    val id: UUID,
    val status: String,
    val currency: String,
    val confirmedAt: Instant?,
)

fun Order.toStatusResponse() = OrderStatusResponse(id, status.name, currency, confirmedAt)

data class OrderResponse(
    val id: UUID,
    val storeId: UUID,
    val status: String,
    val paymentSource: String,
    val currency: String,
    val supporterName: String?,
    val supporterEmail: String?,
    val shippingAddress: ShippingAddressResponse?,
    val confirmedAt: Instant?,
    val refundedAt: Instant?,
    val createdAt: Instant,
)

fun Order.toResponse() =
    OrderResponse(
        id,
        storeId,
        status.name,
        paymentSource.name,
        currency,
        supporterName,
        supporterEmail,
        shippingAddress?.toResponse(),
        confirmedAt,
        refundedAt,
        createdAt,
    )

data class UpdateFulfillmentRequest(
    @field:NotNull val status: FulfillmentStatus,
    val manualVendorId: UUID? = null,
    @field:Size(max = 200) val vendorOrderReference: String? = null,
    @field:Size(max = 120) val carrier: String? = null,
    @field:Size(max = 200) val trackingNumber: String? = null,
    @field:Pattern(
        regexp = "^https://.*",
        message = "Tracking URL must use HTTPS.",
    ) @field:Size(max = 1000) val trackingUrl: String? = null,
    @field:Size(max = 4000) val internalNotes: String? = null,
    @field:Size(max = 1000) val attentionReason: String? = null,
    @field:NotBlank @field:Size(min = 3, max = 500) val note: String,
)

data class CreateFulfillmentReprintRequest(
    @field:NotBlank @field:Size(min = 3, max = 1000) val reason: String,
    @field:Size(max = 200) val vendorOrderReference: String? = null,
    @field:Size(max = 4000) val internalNotes: String? = null,
)

data class UpdateFulfillmentReprintRequest(
    @field:NotNull val status: FulfillmentReprintStatus,
    @field:Size(max = 200) val vendorOrderReference: String? = null,
    @field:Size(max = 120) val carrier: String? = null,
    @field:Size(max = 200) val trackingNumber: String? = null,
    @field:Pattern(
        regexp = "^https://.*",
        message = "Tracking URL must use HTTPS.",
    ) @field:Size(max = 1000) val trackingUrl: String? = null,
    @field:Size(max = 4000) val internalNotes: String? = null,
)

data class FulfillmentResponse(
    val id: UUID,
    val orderId: UUID,
    val source: String,
    val status: String,
    val printifyOrderId: String?,
    val manualVendorId: UUID?,
    val manualVendorName: String?,
    val vendorOrderReference: String?,
    val carrier: String?,
    val trackingNumber: String?,
    val trackingUrl: String?,
    val internalNotes: String?,
    val attentionReason: String?,
    val lastError: String?,
    val statusChangedAt: Instant,
    val shippedAt: Instant?,
    val deliveredAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

fun Fulfillment.toResponse() =
    FulfillmentResponse(
        id,
        orderId,
        source.name,
        status.name,
        printifyOrderId,
        manualVendorId,
        manualVendorName,
        vendorOrderReference,
        carrier,
        trackingNumber,
        trackingUrl,
        internalNotes,
        attentionReason,
        lastError,
        statusChangedAt,
        shippedAt,
        deliveredAt,
        createdAt,
        updatedAt,
    )

data class FulfillmentHistoryResponse(
    val id: UUID,
    val previousStatus: String?,
    val newStatus: String,
    val note: String,
    val actorUserId: UUID?,
    val createdAt: Instant,
)

fun FulfillmentHistory.toResponse() = FulfillmentHistoryResponse(id, previousStatus?.name, newStatus.name, note, actorUserId, createdAt)

data class FulfillmentReprintResponse(
    val id: UUID,
    val fulfillmentId: UUID,
    val orderId: UUID,
    val status: String,
    val reason: String,
    val vendorOrderReference: String?,
    val carrier: String?,
    val trackingNumber: String?,
    val trackingUrl: String?,
    val internalNotes: String?,
    val requestedByUserId: UUID,
    val createdAt: Instant,
    val updatedAt: Instant,
    val shippedAt: Instant?,
    val deliveredAt: Instant?,
)

fun FulfillmentReprint.toResponse() =
    FulfillmentReprintResponse(
        id,
        fulfillmentId,
        orderId,
        status.name,
        reason,
        vendorOrderReference,
        carrier,
        trackingNumber,
        trackingUrl,
        internalNotes,
        requestedByUserId,
        createdAt,
        updatedAt,
        shippedAt,
        deliveredAt,
    )
