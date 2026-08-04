package com.rally26.integration.printify.infra

import com.rally26.config.PrintifyProperties
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

data class PrintifyOrderLineItem(
	val printifyBlueprintId: Long,
	val printifyPrintProviderId: Long,
	val printifyVariantId: Long,
	val quantity: Int,
	/** Position (e.g. "front") -> a fetchable image URL. Printify's "simple" order-time print_areas format takes a raw URL directly — no pre-uploaded Printify image id needed here, unlike product creation. */
	val printAreaImagesByPosition: Map<String, String>,
)

data class PrintifyDraftOrder(val printifyOrderId: String)

private data class SubmitOrderLineItemDto(
	val print_provider_id: Long,
	val blueprint_id: Long,
	val variant_id: Long,
	val quantity: Int,
	val print_areas: Map<String, String>,
)
private data class SubmitOrderRequestDto(val external_id: String, val line_items: List<SubmitOrderLineItemDto>, val send_shipping_notification: Boolean = false)
private data class SubmitOrderResponseDto(val id: String)

/**
 * Creates an order in the founder's Printify shop — deliberately never calls
 * `send_to_production.json` (see the accompanying ADR and DESIGN-DOC.md section
 * 16/17). `external_id` is set to our own `order.id` so the two systems'
 * records are traceable to each other without a webhook back-channel (Stripe
 * Connect account-status webhooks and Printify shipment webhooks both remain
 * Phase 5).
 */
@Component
class PrintifyOrderClient(private val printifyRestClient: RestClient, private val printifyProperties: PrintifyProperties) {

	fun createDraftOrder(ourOrderId: String, lineItems: List<PrintifyOrderLineItem>): PrintifyDraftOrder {
		val request = SubmitOrderRequestDto(
			external_id = ourOrderId,
			line_items = lineItems.map {
				SubmitOrderLineItemDto(
					print_provider_id = it.printifyPrintProviderId,
					blueprint_id = it.printifyBlueprintId,
					variant_id = it.printifyVariantId,
					quantity = it.quantity,
					print_areas = it.printAreaImagesByPosition,
				)
			},
		)
		val dto = printifyRestClient.post()
			.uri("/shops/{shopId}/orders.json", printifyProperties.shopId)
			.body(request)
			.retrieve()
			.body(SubmitOrderResponseDto::class.java)
			?: error("Printify returned an empty response creating an order for $ourOrderId")
		return PrintifyDraftOrder(dto.id)
	}
}
