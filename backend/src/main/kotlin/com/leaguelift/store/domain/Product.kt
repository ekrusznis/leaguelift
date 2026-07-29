package com.leaguelift.store.domain

import java.time.Instant
import java.util.UUID

enum class ProductStatus { DRAFT, ACTIVE, ARCHIVED }

data class Product(
	val id: UUID,
	val organizationId: UUID,
	val storeId: UUID,
	val name: String,
	val description: String?,
	val printifyBlueprintId: Long,
	/** Set once a design is uploaded and pushed to Printify's image library — null until then (see ProductService.createVariant). */
	val printifyImageId: String?,
	val printifyPrintPosition: String,
	val status: ProductStatus,
	val createdAt: Instant,
	val updatedAt: Instant,
)
