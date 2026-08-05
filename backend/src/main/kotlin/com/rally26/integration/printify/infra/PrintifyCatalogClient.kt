package com.rally26.integration.printify.infra

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

data class PrintifyBlueprint(
    val id: Long,
    val title: String,
    val brand: String?,
    val model: String?,
)

data class PrintifyPrintProviderSummary(
    val id: Long,
    val title: String,
    val decorationMethods: List<String>?,
)

data class PrintifyLocation(
    val country: String?,
    val region: String?,
    val city: String?,
    val address1: String?,
)

data class PrintifyPrintProviderDetail(
    val id: Long,
    val title: String,
    val location: PrintifyLocation?,
)

data class PrintifyPlaceholder(
    val position: String,
    val height: Int,
    val width: Int,
)

/** No cost/price here — Printify's catalog never exposes pricing; it only appears on a created product's variants (see PrintifyProductClient). */
data class PrintifyCatalogVariant(
    val id: Long,
    val title: String,
    val options: Map<String, String>?,
    val placeholders: List<PrintifyPlaceholder>?,
)

private data class PrintifyPrintProvidersListDto(
    val id: Long,
    val title: String,
    @JsonProperty("decoration_methods") val decorationMethods: List<String>?,
)

private data class PrintifyLocationDto(
    val country: String?,
    val region: String?,
    val city: String?,
    val address1: String?,
)

private data class PrintifyPrintProviderDetailDto(
    val id: Long,
    val title: String,
    val location: PrintifyLocationDto?,
)

private data class PrintifyPlaceholderDto(
    val position: String,
    val height: Int,
    val width: Int,
)

private data class PrintifyVariantDto(
    val id: Long,
    val title: String,
    val options: Map<String, String>?,
    val placeholders: List<PrintifyPlaceholderDto>?,
)

private data class PrintifyVariantsResponseDto(
    val variants: List<PrintifyVariantDto>,
)

/**
 * Read-only catalog access (blueprints/print-providers/variants). Plain REST
 * client (Spring's `RestClient`, via the `printifyRestClient` bean) — no official
 * Kotlin/Java Printify SDK exists, unlike `stripe-java`. Callers translate
 * `RestClientException` into a clean `ServiceUnavailableException`
 * ([com.rally26.integration.printify.application.VendorSelectionService] does
 * this once for the whole read path other services reuse).
 */
@Component
class PrintifyCatalogClient(
    private val printifyRestClient: RestClient,
) {
    fun listBlueprints(): List<PrintifyBlueprint> =
        printifyRestClient
            .get()
            .uri("/catalog/blueprints.json")
            .retrieve()
            .body(Array<PrintifyBlueprintDto>::class.java)
            ?.map { PrintifyBlueprint(it.id, it.title, it.brand, it.model) }
            ?: emptyList()

    fun listPrintProviders(blueprintId: Long): List<PrintifyPrintProviderSummary> =
        printifyRestClient
            .get()
            .uri("/catalog/blueprints/{blueprintId}/print_providers.json", blueprintId)
            .retrieve()
            .body(Array<PrintifyPrintProvidersListDto>::class.java)
            ?.map { PrintifyPrintProviderSummary(it.id, it.title, it.decorationMethods) }
            ?: emptyList()

    fun getPrintProviderLocation(printProviderId: Long): PrintifyPrintProviderDetail {
        val dto =
            printifyRestClient
                .get()
                .uri("/catalog/print_providers/{printProviderId}.json", printProviderId)
                .retrieve()
                .body(PrintifyPrintProviderDetailDto::class.java)
                ?: error("Printify returned an empty response for print provider $printProviderId")
        return PrintifyPrintProviderDetail(
            dto.id,
            dto.title,
            dto.location?.let { PrintifyLocation(it.country, it.region, it.city, it.address1) },
        )
    }

    fun listVariants(
        blueprintId: Long,
        printProviderId: Long,
    ): List<PrintifyCatalogVariant> {
        val dto =
            printifyRestClient
                .get()
                .uri("/catalog/blueprints/{blueprintId}/print_providers/{printProviderId}/variants.json", blueprintId, printProviderId)
                .retrieve()
                .body(PrintifyVariantsResponseDto::class.java)
                ?: return emptyList()
        return dto.variants.map { v ->
            PrintifyCatalogVariant(v.id, v.title, v.options, v.placeholders?.map { PrintifyPlaceholder(it.position, it.height, it.width) })
        }
    }
}

private data class PrintifyBlueprintDto(
    val id: Long,
    val title: String,
    val brand: String?,
    val model: String?,
)
