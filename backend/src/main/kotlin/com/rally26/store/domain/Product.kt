package com.rally26.store.domain

import java.time.Instant
import java.util.UUID

enum class ProductStatus { DRAFT, ACTIVE, ARCHIVED }
enum class CatalogSource { PRINTIFY, MANUAL }

data class Product(
	val id: UUID,
	val organizationId: UUID,
	val storeId: UUID,
	val name: String,
	val description: String?,
	val catalogSource: CatalogSource,
	val manualVendorId: UUID?,
	val manualVendorName: String?,
	val printifyBlueprintId: Long?,
	/** Set once a design is uploaded and pushed to Printify's image library — null for manual products. */
	val printifyImageId: String?,
	val printifyPrintPosition: String,
	val status: ProductStatus,
	val createdAt: Instant,
	val updatedAt: Instant,
)
