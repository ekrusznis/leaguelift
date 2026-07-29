package com.leaguelift.order.domain

import java.time.Instant
import java.util.UUID

/**
 * DRAFT_CREATED means a Printify order object exists but send_to_production.json
 * was never called — see the accompanying ADR. FAILED means the payment was
 * confirmed but the Printify draft-order call itself failed; the order/payment
 * record still stands, this is purely an admin-visible fulfillment-provider hiccup.
 */
enum class FulfillmentStatus { NOT_SUBMITTED, DRAFT_CREATED, FAILED }

data class Fulfillment(
	val id: UUID,
	val orderId: UUID,
	val status: FulfillmentStatus,
	val printifyOrderId: String?,
	val lastError: String?,
	val createdAt: Instant,
	val updatedAt: Instant,
)
