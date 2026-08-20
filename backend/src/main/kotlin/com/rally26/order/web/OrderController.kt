package com.rally26.order.web

import com.rally26.common.web.CurrentUser
import com.rally26.common.web.PageResponse
import com.rally26.finance.domain.PaymentSource
import com.rally26.order.application.FulfillmentOperationsService
import com.rally26.order.application.OrderService
import com.rally26.order.domain.FulfillmentStatus
import com.rally26.order.domain.OrderSearchCriteria
import com.rally26.order.domain.OrderSearchSort
import com.rally26.order.domain.OrderStatus
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}")
class OrderController(
    private val orderService: OrderService,
    private val fulfillmentOperationsService: FulfillmentOperationsService,
) {
    /** Swag Shop (DESIGN-DOC.md section 13): authenticated coach/guardian order, redirects to Stripe Checkout exactly like the public store flow. */
    @PostMapping("/swag-shop/orders")
    fun createSwagShopOrder(
        @PathVariable organizationId: UUID,
        @Valid @RequestBody request: CreateSwagShopOrderRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): OrderCheckoutResponse =
        orderService
            .createSwagShopCheckoutSession(
                organizationId,
                request.productVariantId,
                request.participantId,
                request.personalizationName,
                request.personalizationNumber,
                request.personalizationPlacement,
                request.personalizationLogoSize,
                currentUser,
            ).toResponse()

    /** Swag Shop "my past orders": every confirmed order the caller could reorder today (own household's participants, plus any team roster they hold TEAM_ORDER_CREATE for). See OrderService.listMySwagShopOrders. */
    @GetMapping("/swag-shop/my-orders")
    fun listMySwagShopOrders(
        @PathVariable organizationId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): List<SwagShopOrderHistoryItemDto> = orderService.listMySwagShopOrders(organizationId, currentUser).map { it.toResponse() }

    @GetMapping("/stores/{storeId}/orders")
    fun listForStore(
        @PathVariable organizationId: UUID,
        @PathVariable storeId: UUID,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): PageResponse<OrderResponse> {
        val offset = page * size
        val items = orderService.listForStore(organizationId, storeId, currentUser, offset, size).map { it.toResponse() }
        val total = orderService.getConfirmedCount(organizationId, storeId, currentUser)
        return PageResponse(items, page, size, total)
    }

    @GetMapping("/stores/{storeId}/orders/search")
    fun searchOrders(
        @PathVariable organizationId: UUID,
        @PathVariable storeId: UUID,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "25") size: Int,
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) status: OrderStatus?,
        @RequestParam(required = false) paymentSource: PaymentSource?,
        @RequestParam(required = false) fulfillmentStatus: FulfillmentStatus?,
        @RequestParam(defaultValue = "NEWEST") sort: OrderSearchSort,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): PageResponse<OrderSearchItemResponse> {
        val offset = page * size
        val criteria = OrderSearchCriteria(q, status, paymentSource, fulfillmentStatus, sort)
        val items = orderService.search(organizationId, storeId, criteria, currentUser, offset, size).map { it.toResponse() }
        val total = orderService.countSearch(organizationId, storeId, criteria, currentUser)
        return PageResponse(items, page, size, total)
    }

    @GetMapping("/orders/{orderId}/fulfillment")
    fun getFulfillment(
        @PathVariable organizationId: UUID,
        @PathVariable orderId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): FulfillmentResponse? = fulfillmentOperationsService.get(organizationId, orderId, currentUser)?.toResponse()

    @PutMapping("/orders/{orderId}/fulfillment")
    fun updateFulfillment(
        @PathVariable organizationId: UUID,
        @PathVariable orderId: UUID,
        @Valid @RequestBody request: UpdateFulfillmentRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): FulfillmentResponse =
        fulfillmentOperationsService
            .update(
                organizationId,
                orderId,
                request.status,
                request.manualVendorId,
                request.vendorOrderReference,
                request.carrier,
                request.trackingNumber,
                request.trackingUrl,
                request.internalNotes,
                request.attentionReason,
                request.note,
                currentUser,
            ).toResponse()

    @GetMapping("/orders/{orderId}/fulfillment/history")
    fun listFulfillmentHistory(
        @PathVariable organizationId: UUID,
        @PathVariable orderId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): List<FulfillmentHistoryResponse> =
        fulfillmentOperationsService.listHistory(organizationId, orderId, currentUser).map { it.toResponse() }

    @GetMapping("/orders/{orderId}/fulfillment/reprints")
    fun listReprints(
        @PathVariable organizationId: UUID,
        @PathVariable orderId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): List<FulfillmentReprintResponse> =
        fulfillmentOperationsService.listReprints(organizationId, orderId, currentUser).map {
            it.toResponse()
        }

    @PostMapping("/orders/{orderId}/fulfillment/reprints")
    fun requestReprint(
        @PathVariable organizationId: UUID,
        @PathVariable orderId: UUID,
        @Valid @RequestBody request: CreateFulfillmentReprintRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<FulfillmentReprintResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(
            fulfillmentOperationsService
                .requestReprint(
                    organizationId,
                    orderId,
                    request.reason,
                    request.vendorOrderReference,
                    request.internalNotes,
                    currentUser,
                ).toResponse(),
        )

    @PutMapping("/orders/{orderId}/fulfillment/reprints/{reprintId}")
    fun updateReprint(
        @PathVariable organizationId: UUID,
        @PathVariable orderId: UUID,
        @PathVariable reprintId: UUID,
        @Valid @RequestBody request: UpdateFulfillmentReprintRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): FulfillmentReprintResponse =
        fulfillmentOperationsService
            .updateReprint(
                organizationId,
                orderId,
                reprintId,
                request.status,
                request.vendorOrderReference,
                request.carrier,
                request.trackingNumber,
                request.trackingUrl,
                request.internalNotes,
                currentUser,
            ).toResponse()

    @PostMapping("/orders/{orderId}/refund")
    fun refund(
        @PathVariable organizationId: UUID,
        @PathVariable orderId: UUID,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): OrderResponse = orderService.refund(organizationId, orderId, currentUser).toResponse()
}
