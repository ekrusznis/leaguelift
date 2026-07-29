package com.leaguelift.order.domain

import java.util.UUID

data class OrderItem(
	val id: UUID,
	val orderId: UUID,
	val productVariantId: UUID,
	val quantity: Int,
	/** Both snapshotted at order time — "must store transaction-time cost" (DESIGN-DOC.md section 14.3 Apparel acceptance criteria). */
	val unitPriceMinor: Long,
	val unitCostMinor: Long,
)
