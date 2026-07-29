package com.leaguelift.order.web

import com.leaguelift.order.application.OrderService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Public, unauthenticated — a supporter checking out is never a LeagueLift user
 * (mirrors ContributionPublicController). Confirmation is authoritative only via
 * the Stripe webhook; this controller's GET is a read-only poll for the
 * browser's return page, nothing here flips status.
 */
@RestController
@RequestMapping("/api/v1/public/stores/{slug}/orders")
class OrderPublicController(private val orderService: OrderService) {

	@PostMapping
	fun createCheckoutSession(
		@PathVariable slug: String,
		@Valid @RequestBody request: CreateOrderCheckoutRequest,
	): OrderCheckoutResponse =
		orderService.createCheckoutSession(
			slug, request.items.map { it.toRequest() }, request.supporterName,
			request.supporterEmail, request.successUrl, request.cancelUrl,
		).toResponse()

	@GetMapping("/{orderId}")
	fun getStatus(@PathVariable slug: String, @PathVariable orderId: UUID): OrderStatusResponse =
		orderService.getStatus(slug, orderId).toStatusResponse()
}
