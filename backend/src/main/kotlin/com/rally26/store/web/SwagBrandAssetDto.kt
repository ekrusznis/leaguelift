package com.rally26.store.web

import com.rally26.store.application.SwagBrandAssetDescriptor
import com.rally26.store.domain.SwagBrandAssetCategory
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class CreateSwagBrandAssetRequest(
    val teamId: UUID? = null,
    @field:NotNull val mediaAssetId: UUID,
    @field:NotBlank @field:Size(max = 120) val name: String,
    @field:NotNull val category: SwagBrandAssetCategory,
)

data class SwagBrandAssetResponse(
    val id: UUID,
    val organizationId: UUID,
    val teamId: UUID?,
    val mediaAssetId: UUID,
    val name: String,
    val category: String,
    val status: String,
    val previewUrl: String,
    val contentType: String,
    val widthPx: Int?,
    val heightPx: Int?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

fun SwagBrandAssetDescriptor.toResponse() =
    SwagBrandAssetResponse(
        id = asset.id,
        organizationId = asset.organizationId,
        teamId = asset.teamId,
        mediaAssetId = asset.mediaAssetId,
        name = asset.name,
        category = asset.category.name,
        status = asset.status.name,
        previewUrl = previewUrl,
        contentType = contentType,
        widthPx = widthPx,
        heightPx = heightPx,
        createdAt = asset.createdAt,
        updatedAt = asset.updatedAt,
    )
