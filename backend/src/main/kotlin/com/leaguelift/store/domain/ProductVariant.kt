package com.leaguelift.store.domain

import java.time.Instant
import java.util.UUID

data class ProductVariant(
	val id: UUID,
	val organizationId: UUID,
	val productId: UUID,
	val label: String,
	val printifyPrintProviderId: Long,
	val printifyVariantId: Long,
	val currency: String,
	/** Printify's real cost for this variant/provider, in minor units — snapshotted from PrintifyProductClient's response at creation time, never guessed. */
	val costMinor: Long,
	val priceMinor: Long,
	val isActive: Boolean,
	val createdAt: Instant,
	val updatedAt: Instant,
)
