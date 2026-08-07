package com.rally26.store.domain

import java.time.Instant
import java.util.UUID

data class ProductVariant(
    val id: UUID,
    val organizationId: UUID,
    val productId: UUID,
    val catalogSource: CatalogSource,
    val label: String,
    val sku: String?,
    val size: String?,
    val color: String?,
    val printifyPrintProviderId: Long?,
    val printifyVariantId: Long?,
    val currency: String,
    /** Provider or vendor cost in minor units, snapshotted onto each order item at checkout. */
    val costMinor: Long,
    val priceMinor: Long,
    val isActive: Boolean,
    /** Swag Shop (DESIGN-DOC.md section 13): real Printify print-area pixel dimensions for this variant's print position, captured from the same catalog call that learns costMinor/priceMinor. Null for manual variants or variants created before Swag Shop personalization. */
    val printAreaWidthPx: Int? = null,
    val printAreaHeightPx: Int? = null,
    /** Real Printify "back" placeholder pixel dimensions, so BACK-placed personalization can print on the garment's actual back rather than a back-style layout on the front. Null if the blueprint has no back placeholder or the variant predates this capability. */
    val backPrintAreaWidthPx: Int? = null,
    val backPrintAreaHeightPx: Int? = null,
    /** Real photorealistic Printify mockup images (this variant's color, design already composited), captured free from the same cost-discovery create-product call. Stable CDN URLs, not presigned. */
    val mockupFrontUrl: String? = null,
    val mockupBackUrl: String? = null,
    /** Phase 24 slice 24.4 (ADR-070): Printify's own external product id for this variant (each variant is its own distinct Printify "product" — see ProductService.createVariant). Null for manual variants. */
    val printifyProductId: String? = null,
    /** Snapshotted at creation time from the then-configured shop, so historical rows keep pointing at the shop they were actually created under even after a future token/shop rotation. Null for manual variants. */
    val printifyShopId: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
)
