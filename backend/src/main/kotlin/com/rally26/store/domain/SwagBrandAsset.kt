package com.rally26.store.domain

import java.time.Instant
import java.util.UUID

enum class SwagBrandAssetCategory {
    PRIMARY,
    ALTERNATE,
    LIGHT,
    DARK,
    WORDMARK,
    MASCOT,
    SMALL_PRINT,
    WIDE,
    SEASONAL,
    COMMEMORATIVE,
}

enum class SwagBrandAssetStatus { ACTIVE, ARCHIVED }

data class SwagBrandAsset(
    val id: UUID,
    val organizationId: UUID,
    val teamId: UUID?,
    val mediaAssetId: UUID,
    val name: String,
    val category: SwagBrandAssetCategory,
    val status: SwagBrandAssetStatus,
    val createdByUserId: UUID,
    val createdAt: Instant,
    val updatedAt: Instant,
)
