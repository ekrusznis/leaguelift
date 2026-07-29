package com.leaguelift.order.web

import com.leaguelift.common.web.CurrentUser
import com.leaguelift.common.web.PageResponse
import com.leaguelift.order.application.OrderService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}")
class OrderController(private val orderService: OrderService) {

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

	@GetMapping("/orders/{orderId}/fulfillment")
	fun getFulfillment(
		@PathVariable organizationId: UUID,
		@PathVariable orderId: UUID,
		@AuthenticationPrincipal currentUser: CurrentUser,
	): FulfillmentResponse? = orderService.getFulfillment(organizationId, orderId, currentUser)?.toResponse()

	@PostMapping("/orders/{orderId}/refund")
	fun refund(
		@PathVariable organizationId: UUID,
		@PathVariable orderId: UUID,
		@AuthenticationPrincipal currentUser: CurrentUser,
	): OrderResponse = orderService.refund(organizationId, orderId, currentUser).toResponse()
}
