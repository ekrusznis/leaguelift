package com.rally26.integration.printify.infra

import com.rally26.config.PrintifyProperties
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

data class PrintifyProductVariantCost(
    val printifyVariantId: Long,
    val costMinor: Long,
    val priceMinor: Long,
)

data class PrintifyProductResult(
    val printifyProductId: String,
    val variantCosts: List<PrintifyProductVariantCost>,
)

private data class CreateProductVariantDto(
    val id: Long,
    val price: Long,
    val is_enabled: Boolean,
)

private data class PositionedImageDto(
    val id: String,
    val x: Double = 0.5,
    val y: Double = 0.5,
    val scale: Double = 1.0,
    val angle: Int = 0,
)

private data class PlaceholderDto(
    val position: String,
    val images: List<PositionedImageDto>,
)

private data class PrintAreaDto(
    val variant_ids: List<Long>,
    val placeholders: List<PlaceholderDto>,
)

private data class CreateProductRequestDto(
    val title: String,
    val blueprint_id: Long,
    val print_provider_id: Long,
    val variants: List<CreateProductVariantDto>,
    val print_areas: List<PrintAreaDto>,
)

private data class ProductVariantResponseDto(
    val id: Long,
    val price: Long,
    val cost: Long,
)

private data class CreateProductResponseDto(
    val id: String,
    val variants: List<ProductVariantResponseDto>,
)

/**
 * Creates a real product in the founder's Printify shop — this is the only way to
 * learn a print provider's actual `cost` per variant (in cents; Printify's
 * catalog-browse endpoints never expose pricing, confirmed against Printify's
 * OpenAPI spec). Called once when an admin sets up a product/variant in our
 * system (`store/application/ProductService.kt`), never per candidate provider —
 * there's no cross-provider price comparison in this slice (see
 * VendorSelectionService). This does not submit anything for production; it's a
 * shop catalog entry, same non-execution posture as
 * `payout/infra/StripeConnectClient.kt`'s onboarding-only scope.
 */
@Component
class PrintifyProductClient(
    private val printifyRestClient: RestClient,
    private val printifyProperties: PrintifyProperties,
) {
    fun createProduct(
        title: String,
        blueprintId: Long,
        printProviderId: Long,
        printifyVariantIds: List<Long>,
        requestedPriceMinor: Long,
        printifyImageId: String,
        printPosition: String,
    ): PrintifyProductResult {
        val request =
            CreateProductRequestDto(
                title = title,
                blueprint_id = blueprintId,
                print_provider_id = printProviderId,
                variants = printifyVariantIds.map { CreateProductVariantDto(id = it, price = requestedPriceMinor, is_enabled = true) },
                print_areas =
                    listOf(
                        PrintAreaDto(
                            variant_ids = printifyVariantIds,
                            placeholders =
                                listOf(
                                    PlaceholderDto(position = printPosition, images = listOf(PositionedImageDto(id = printifyImageId))),
                                ),
                        ),
                    ),
            )
        val dto =
            printifyRestClient
                .post()
                .uri("/shops/{shopId}/products.json", printifyProperties.shopId)
                .body(request)
                .retrieve()
                .body(CreateProductResponseDto::class.java)
                ?: error("Printify returned an empty response creating a product for blueprint $blueprintId")
        return PrintifyProductResult(
            printifyProductId = dto.id,
            variantCosts = dto.variants.map { PrintifyProductVariantCost(it.id, it.cost, it.price) },
        )
    }
}
