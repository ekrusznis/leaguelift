package com.rally26.store.web

import com.rally26.store.application.ProductService
import com.rally26.store.application.StoreService
import com.rally26.team.domain.Team
import com.rally26.team.persistence.TeamRepository
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Public, unauthenticated — a supporter browsing a store is never a Rally26
 * user (mirrors CampaignPublicController). Combines store + ACTIVE products +
 * active variants + each product's public design URL into one response so the
 * storefront doesn't need N+1 calls.
 */
@RestController
@RequestMapping("/api/v1/public/stores")
class StorePublicController(
    private val storeService: StoreService,
    private val productService: ProductService,
    private val teamRepository: TeamRepository,
) {
    @GetMapping("/{slug}")
    fun getStore(
        @PathVariable slug: String,
    ): PublicStoreResponse {
        val store = storeService.getPublic(slug)
        val products =
            productService.listPublicProducts(store.id).map { product ->
                PublicProductResponse(
                    id = product.id,
                    name = product.name,
                    description = product.description,
                    designUrl = productService.getPublicDesignUrl(product.id),
                    variants =
                        productService.listPublicVariants(product.id).map {
                            PublicProductVariantResponse(it.id, it.label, it.priceMinor, it.currency)
                        },
                )
            }
        val team = store.teamId?.let { teamRepository.findById(it, store.organizationId) }
        return PublicStoreResponse(
            store.id,
            store.name,
            store.slug,
            products,
            primaryColor = team?.resolvedPrimaryColor ?: Team.DEFAULT_PRIMARY_COLOR,
            secondaryColor = team?.resolvedSecondaryColor ?: Team.DEFAULT_SECONDARY_COLOR,
        )
    }
}
