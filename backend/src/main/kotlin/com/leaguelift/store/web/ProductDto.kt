package com.leaguelift.store.web

import com.leaguelift.integration.printify.application.EligiblePrintProvider
import com.leaguelift.integration.printify.infra.PrintifyBlueprint
import com.leaguelift.integration.printify.infra.PrintifyCatalogVariant
import com.leaguelift.store.domain.Product
import com.leaguelift.store.domain.ProductStatus
import com.leaguelift.store.domain.ProductVariant
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class CreateProductRequest(
	@field:NotBlank @field:Size(min = 1, max = 120) val name: String,
	@field:Size(max = 2000) val description: String? = null,
	@field:NotNull val printifyBlueprintId: Long,
	@field:NotBlank val printifyPrintPosition: String = "front",
)

data class UpdateProductStatusRequest(@field:NotNull val status: ProductStatus)

data class AssignProductDesignRequest(@field:NotNull val assetId: UUID, val altText: String? = null)

data class CreateProductVariantRequest(
	@field:NotNull val printifyPrintProviderId: Long,
	@field:NotNull val printifyVariantId: Long,
	@field:NotBlank @field:Size(max = 120) val label: String,
	@field:NotNull @field:Min(0) val priceMinor: Long,
)

data class ProductResponse(
	val id: UUID,
	val organizationId: UUID,
	val storeId: UUID,
	val name: String,
	val description: String?,
	val printifyBlueprintId: Long,
	val printifyPrintPosition: String,
	val hasDesign: Boolean,
	val status: String,
	val createdAt: Instant,
	val updatedAt: Instant,
)

fun Product.toResponse() = ProductResponse(
	id, organizationId, storeId, name, description, printifyBlueprintId, printifyPrintPosition,
	hasDesign = printifyImageId != null, status = status.name, createdAt = createdAt, updatedAt = updatedAt,
)

data class ProductVariantResponse(
	val id: UUID,
	val productId: UUID,
	val label: String,
	val printifyPrintProviderId: Long,
	val printifyVariantId: Long,
	val currency: String,
	val costMinor: Long,
	val priceMinor: Long,
	val isActive: Boolean,
)

fun ProductVariant.toResponse() = ProductVariantResponse(id, productId, label, printifyPrintProviderId, printifyVariantId, currency, costMinor, priceMinor, isActive)

data class PrintifyBlueprintResponse(val id: Long, val title: String, val brand: String?, val model: String?)

fun PrintifyBlueprint.toResponse() = PrintifyBlueprintResponse(id, title, brand, model)

data class PrintifyLocationResponse(val country: String?, val region: String?, val city: String?)

data class EligiblePrintProviderResponse(val id: Long, val title: String, val decorationMethods: List<String>?, val location: PrintifyLocationResponse)

fun EligiblePrintProvider.toResponse() = EligiblePrintProviderResponse(id, title, decorationMethods, PrintifyLocationResponse(location.country, location.region, location.city))

data class PrintifyCatalogVariantResponse(val id: Long, val title: String, val options: Map<String, String>?)

fun PrintifyCatalogVariant.toResponse() = PrintifyCatalogVariantResponse(id, title, options)
