package com.rally26.order.web

import com.rally26.order.application.OrderService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Public, unauthenticated — a supporter buying for a specific athlete is never
 * a Rally26 user (mirrors OrderPublicController exactly). Confirmation is
 * authoritative only via the Stripe webhook; the GET here is a read-only poll
 * for the browser's return page.
 */
@RestController
@RequestMapping("/api/v1/public/athlete-storefronts/{slug}/orders")
class AthleteStorefrontOrderPublicController(
    private val orderService: OrderService,
) {
    @PostMapping
    fun createCheckoutSession(
        @PathVariable slug: String,
        @Valid @RequestBody request: CreateAthleteStorefrontOrderRequest,
    ): OrderCheckoutResponse =
        orderService
            .createAthleteStorefrontCheckoutSession(
                slug,
                request.productVariantId,
                request.personalizationName,
                request.personalizationNumber,
                request.personalizationPlacement,
                request.personalizationLogoSize,
                request.supporterName,
                request.supporterEmail,
            ).toResponse()

    @GetMapping("/{orderId}")
    fun getStatus(
        @PathVariable slug: String,
        @PathVariable orderId: UUID,
    ): OrderStatusResponse = orderService.getAthleteStorefrontOrderStatus(slug, orderId).toStatusResponse()
}
