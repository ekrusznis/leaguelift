package com.rally26.store.domain

import java.time.Instant
import java.util.UUID

data class ProductVariant(
	val id: UUID,
	val organizationId: UUID,
	val productId: UUID,
	val catalogSource: CatalogSource,
	val label: String,
	val sku: String?,
	val size: String?,
	val color: String?,
	val printifyPrintProviderId: Long?,
	val printifyVariantId: Long?,
	val currency: String,
	/** Provider or vendor cost in minor units, snapshotted onto each order item at checkout. */
	val costMinor: Long,
	val priceMinor: Long,
	val isActive: Boolean,
	val createdAt: Instant,
	val updatedAt: Instant,
)
