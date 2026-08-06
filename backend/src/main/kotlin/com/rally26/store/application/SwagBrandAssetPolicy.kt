package com.rally26.store.application

import com.rally26.common.error.ValidationException
import com.rally26.media.domain.MediaAsset
import com.rally26.media.domain.MediaAssetStatus
import com.rally26.media.domain.MediaUsageSlot
import com.rally26.store.domain.SwagBrandAsset
import com.rally26.store.domain.SwagBrandAssetStatus
import java.util.UUID

/** Pure Phase 24.1 validation shared by library creation, restore, and product assignment. */
object SwagBrandAssetPolicy {
    private val printContentTypes = setOf("image/png", "image/jpeg", "image/webp")

    fun requireProductUse(
        asset: SwagBrandAsset,
        productTeamId: UUID?,
    ) {
        if (asset.status != SwagBrandAssetStatus.ACTIVE) {
            throw ValidationException("Choose an active Swag Brand Asset.")
        }
        if (asset.teamId != null && asset.teamId != productTeamId) {
            throw ValidationException("This team-scoped Swag Brand Asset cannot be used by that product.")
        }
    }

    fun requirePrintableMedia(media: MediaAsset) {
        if (media.status != MediaAssetStatus.READY) {
            throw ValidationException("Finish uploading the brand image before adding it to the library.")
        }
        if (media.intendedUsageSlot !in setOf(MediaUsageSlot.PRODUCT_DESIGN, MediaUsageSlot.LOGO)) {
            throw ValidationException("Use a logo or product-design image for a Swag Brand Asset.")
        }
        val contentType = media.detectedContentType ?: media.declaredContentType
        if (contentType !in printContentTypes) {
            throw ValidationException("Swag Brand Assets must be PNG, JPEG, or WebP. SVG is not supported for print yet.")
        }
    }
}
