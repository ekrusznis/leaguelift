package com.rally26.integration.printify.application

import com.rally26.common.error.ServiceUnavailableException
import com.rally26.integration.printify.infra.PrintifyCatalogClient
import com.rally26.integration.printify.infra.PrintifyLocation
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClientException

private val log = LoggerFactory.getLogger(VendorSelectionService::class.java)

data class EligiblePrintProvider(
    val id: Long,
    val title: String,
    val decorationMethods: List<String>?,
    val location: PrintifyLocation,
)

/**
 * Named and documented honestly: this is a **location filter**, not a price
 * comparison. Printify's catalog-browse endpoints never expose cost/price — that
 * only appears once a product is actually created against a specific provider
 * (see `integration/printify/infra/PrintifyProductClient.kt`) — so ranking
 * candidate providers by "cheapest" would require creating a throwaway product
 * per candidate just to read back a quote, which this slice deliberately does
 * not do (founder decision). The admin sees this filtered list and picks (or
 * accepts the sole match) — never a fully invisible auto-decision.
 */
@Service
class VendorSelectionService(
    private val printifyCatalogClient: PrintifyCatalogClient,
) {
    fun listUsPrintProviders(blueprintId: Long): List<EligiblePrintProvider> =
        withPrintifyErrorTranslation {
            printifyCatalogClient.listPrintProviders(blueprintId).mapNotNull { summary ->
                val detail = printifyCatalogClient.getPrintProviderLocation(summary.id)
                val location = detail.location
                if (location != null && location.country?.equals("US", ignoreCase = true) == true) {
                    EligiblePrintProvider(summary.id, summary.title, summary.decorationMethods, location)
                } else {
                    null
                }
            }
        }

    private fun <T> withPrintifyErrorTranslation(block: () -> T): T =
        try {
            block()
        } catch (e: RestClientException) {
            log.warn("Printify catalog API call failed: {}", e.message, e)
            throw ServiceUnavailableException(
                "PRINTIFY_PROVIDER_UNAVAILABLE",
                "The apparel provider is not available right now. If this is local/staging, confirm PRINTIFY_API_TOKEN is set.",
            )
        }
}
