package com.rally26.store.web

import com.rally26.organization.persistence.OrganizationRepository
import com.rally26.store.application.AthleteStorefrontService
import com.rally26.store.application.ProductService
import com.rally26.team.persistence.TeamRepository
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Public, unauthenticated — a supporter buying for a specific athlete is
 * never a Rally26 user (mirrors StorePublicController/CampaignPublicController).
 * Combines storefront + org/team names + the athlete's public-safe label
 * (first name + last initial only, computed server-side — the real
 * `Participant.lastName` is never serialized, DESIGN-DOC.md section 1
 * youth-data boundary) + approved ACTIVE products/variants into one response.
 */
@RestController
@RequestMapping("/api/v1/public/athlete-storefronts")
class AthleteStorefrontPublicController(
    private val service: AthleteStorefrontService,
    private val productService: ProductService,
    private val organizationRepository: OrganizationRepository,
    private val teamRepository: TeamRepository,
) {
    @GetMapping("/{slug}")
    fun getStorefront(
        @PathVariable slug: String,
    ): AthleteStorefrontPublicResponse {
        val public = service.getPublic(slug)
        val storefront = public.storefront
        val organizationName = organizationRepository.findById(storefront.organizationId)?.name ?: ""
        val teamName = storefront.teamId?.let { teamRepository.findById(it, storefront.organizationId)?.name }
        val products =
            service.getPublicProducts(storefront).map { (product, variants) ->
                AthleteStorefrontProductResponse(
                    id = product.id,
                    name = product.name,
                    description = product.description,
                    hasSwagLogo = product.swagLogoMediaAssetId != null,
                    logoPreviewUrl = productService.getSwagLogoPreviewUrl(storefront.organizationId, product),
                    variants = variants.map { it.toAthleteStorefrontResponse() },
                )
            }
        return public.toResponse(organizationName, teamName, products)
    }
}
