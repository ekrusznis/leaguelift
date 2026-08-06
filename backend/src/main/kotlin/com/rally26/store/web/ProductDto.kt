package com.rally26.store.web

import com.rally26.integration.printify.application.EligiblePrintProvider
import com.rally26.integration.printify.infra.PrintifyBlueprint
import com.rally26.integration.printify.infra.PrintifyCatalogVariant
import com.rally26.store.domain.CatalogSource
import com.rally26.store.domain.Product
import com.rally26.store.domain.ProductStatus
import com.rally26.store.domain.ProductVariant
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class CreateProductRequest(
    @field:NotBlank @field:Size(min = 1, max = 120) val name: String,
    @field:Size(max = 2000) val description: String? = null,
    val catalogSource: CatalogSource = CatalogSource.PRINTIFY,
    val manualVendorId: UUID? = null,
    val printifyBlueprintId: Long? = null,
    @field:NotBlank val printifyPrintPosition: String = "front",
)

data class UpdateManualProductRequest(
    @field:NotBlank @field:Size(min = 1, max = 120) val name: String,
    @field:Size(max = 2000) val description: String? = null,
    val manualVendorId: UUID? = null,
)

data class UpdateProductStatusRequest(
    @field:NotNull val status: ProductStatus,
)

data class AssignProductDesignRequest(
    @field:NotNull val assetId: UUID,
    val altText: String? = null,
)

data class CreateProductVariantRequest(
    @field:NotNull val printifyPrintProviderId: Long,
    @field:NotNull val printifyVariantId: Long,
    @field:NotBlank @field:Size(max = 120) val label: String,
    /** Optional override — omitted means auto-computed from the org's markup rule engine (Phase 23) once Printify's real cost is known. */
    @field:Min(0) val priceMinor: Long? = null,
)

data class ManualProductVariantRequest(
    @field:NotBlank @field:Size(max = 120) val label: String,
    @field:Size(max = 120) val sku: String? = null,
    @field:Size(max = 80) val size: String? = null,
    @field:Size(max = 80) val color: String? = null,
    @field:Pattern(regexp = "^[A-Za-z]{3}$", message = "Currency must be a three-letter code.") val currency: String = "USD",
    @field:Min(0) val costMinor: Long,
    @field:Min(0) val priceMinor: Long,
    val isActive: Boolean = true,
)

data class UpdateProductVariantStatusRequest(
    val isActive: Boolean,
)

data class ProductResponse(
    val id: UUID,
    val organizationId: UUID,
    val storeId: UUID,
    val name: String,
    val description: String?,
    val catalogSource: String,
    val manualVendorId: UUID?,
    val manualVendorName: String?,
    val printifyBlueprintId: Long?,
    val printifyPrintPosition: String,
    val hasDesign: Boolean,
    /** Swag Shop (DESIGN-DOC.md section 13): whether a team logo has been frozen onto this product for order-time compositing. */
    val hasSwagLogo: Boolean,
    val status: String,
    val createdAt: Instant,
    val updatedAt: Instant,
)

fun Product.toResponse(hasDesign: Boolean) =
    ProductResponse(
        id,
        organizationId,
        storeId,
        name,
        description,
        catalogSource.name,
        manualVendorId,
        manualVendorName,
        printifyBlueprintId,
        printifyPrintPosition,
        hasDesign,
        swagLogoMediaAssetId != null,
        status.name,
        createdAt,
        updatedAt,
    )

data class ProductVariantResponse(
    val id: UUID,
    val productId: UUID,
    val catalogSource: String,
    val label: String,
    val sku: String?,
    val size: String?,
    val color: String?,
    val printifyPrintProviderId: Long?,
    val printifyVariantId: Long?,
    val currency: String,
    val costMinor: Long,
    val priceMinor: Long,
    val isActive: Boolean,
    /** Swag Shop (DESIGN-DOC.md section 13): real print-area pixel dimensions, captured at creation time — null for manual variants. */
    val printAreaWidthPx: Int?,
    val printAreaHeightPx: Int?,
    /** Real Printify "back" placeholder dimensions, so BACK-placed personalization can print on the garment's actual back. Null if the blueprint has no back placeholder or the variant predates this capability. */
    val backPrintAreaWidthPx: Int?,
    val backPrintAreaHeightPx: Int?,
    /** Real photorealistic Printify mockup images (this variant's color, design already composited) — free from the same cost-discovery call, no stock-photo fallback needed. Null for manual variants or variants created before this capability. */
    val mockupFrontUrl: String?,
    val mockupBackUrl: String?,
)

fun ProductVariant.toResponse() =
    ProductVariantResponse(
        id,
        productId,
        catalogSource.name,
        label,
        sku,
        size,
        color,
        printifyPrintProviderId,
        printifyVariantId,
        currency,
        costMinor,
        priceMinor,
        isActive,
        printAreaWidthPx,
        printAreaHeightPx,
        backPrintAreaWidthPx,
        backPrintAreaHeightPx,
        mockupFrontUrl,
        mockupBackUrl,
    )

/** Swag Shop (DESIGN-DOC.md section 13) buyer picker — one row per active apparel type, combining store/product/variant so the frontend doesn't need three round trips per store. */
data class SwagShopApparelTypeResponse(
    val storeId: java.util.UUID,
    val storeName: String,
    val productId: java.util.UUID,
    val productName: String,
    val description: String?,
    val hasSwagLogo: Boolean,
    val variants: List<ProductVariantResponse>,
)

data class PrintifyBlueprintResponse(
    val id: Long,
    val title: String,
    val brand: String?,
    val model: String?,
)

fun PrintifyBlueprint.toResponse() = PrintifyBlueprintResponse(id, title, brand, model)

data class PrintifyLocationResponse(
    val country: String?,
    val region: String?,
    val city: String?,
)

data class EligiblePrintProviderResponse(
    val id: Long,
    val title: String,
    val decorationMethods: List<String>?,
    val location: PrintifyLocationResponse,
)

fun EligiblePrintProvider.toResponse() =
    EligiblePrintProviderResponse(id, title, decorationMethods, PrintifyLocationResponse(location.country, location.region, location.city))

/** Swag Shop (DESIGN-DOC.md section 13): position -> real print-area pixel dimensions, used by the Swag Shop setup UI to know each apparel type's real placement bounds. */
data class PrintifyPlaceholderResponse(
    val position: String,
    val widthPx: Int,
    val heightPx: Int,
)

data class PrintifyCatalogVariantResponse(
    val id: Long,
    val title: String,
    val options: Map<String, String>?,
    val placeholders: List<PrintifyPlaceholderResponse>?,
)

fun PrintifyCatalogVariant.toResponse() =
    PrintifyCatalogVariantResponse(
        id,
        title,
        options,
        placeholders?.map { PrintifyPlaceholderResponse(it.position, it.width, it.height) },
    )
