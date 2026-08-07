package com.rally26.store.web

import com.rally26.store.application.AthleteStorefrontPublic
import com.rally26.store.domain.AthleteStorefront
import com.rally26.store.domain.ProductVariant
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class CreateAthleteStorefrontRequest(
    @field:NotNull val participantId: UUID,
    val teamId: UUID? = null,
    @field:NotNull val storeId: UUID,
    @field:NotEmpty val productIds: List<UUID>,
    @field:Pattern(regexp = "^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$") @field:Size(min = 1, max = 63) val slug: String,
)

data class UpdateAthleteStorefrontProductsRequest(
    @field:NotEmpty val productIds: List<UUID>,
)

data class BuildAthleteStorefrontShareLinkRequest(
    @field:NotNull @field:Size(max = 2000) val url: String,
)

data class ShareLinkQrResponse(
    val qrDataUri: String,
)

data class AthleteStorefrontResponse(
    val id: UUID,
    val organizationId: UUID,
    val participantId: UUID,
    val teamId: UUID?,
    val storeId: UUID,
    val slug: String,
    val status: String,
    val publishedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

fun AthleteStorefront.toResponse() =
    AthleteStorefrontResponse(id, organizationId, participantId, teamId, storeId, slug, status.name, publishedAt, createdAt, updatedAt)

/** Buyer-facing product/variant shape — deliberately fuller than the generic `PublicProductVariantResponse` (mockup + print-area + logo preview) so the 24.2 `SwagPersonalizationPreview` component works unmodified on a public storefront page. */
data class AthleteStorefrontProductVariantResponse(
    val id: UUID,
    val label: String,
    val priceMinor: Long,
    val currency: String,
    val printAreaWidthPx: Int?,
    val printAreaHeightPx: Int?,
    val backPrintAreaWidthPx: Int?,
    val backPrintAreaHeightPx: Int?,
    val mockupFrontUrl: String?,
    val mockupBackUrl: String?,
)

fun ProductVariant.toAthleteStorefrontResponse() =
    AthleteStorefrontProductVariantResponse(
        id,
        label,
        priceMinor,
        currency,
        printAreaWidthPx,
        printAreaHeightPx,
        backPrintAreaWidthPx,
        backPrintAreaHeightPx,
        mockupFrontUrl,
        mockupBackUrl,
    )

data class AthleteStorefrontProductResponse(
    val id: UUID,
    val name: String,
    val description: String?,
    val hasSwagLogo: Boolean,
    val logoPreviewUrl: String?,
    val variants: List<AthleteStorefrontProductVariantResponse>,
)

data class AthleteStorefrontPublicResponse(
    val slug: String,
    val organizationName: String,
    val teamName: String?,
    val athletePublicLabel: String,
    val products: List<AthleteStorefrontProductResponse>,
)

fun AthleteStorefrontPublic.toResponse(
    organizationName: String,
    teamName: String?,
    products: List<AthleteStorefrontProductResponse>,
) = AthleteStorefrontPublicResponse(storefront.slug, organizationName, teamName, athletePublicLabel, products)
