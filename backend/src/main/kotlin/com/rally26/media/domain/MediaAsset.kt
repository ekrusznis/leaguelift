package com.rally26.media.domain

import java.time.Instant
import java.util.UUID

enum class MediaAssetStatus { PENDING_UPLOAD, UPLOADED, PROCESSING, READY, FAILED, REJECTED, ARCHIVED }

enum class MediaUsageSlot { LOGO, COVER, PROFILE_PHOTO, PRODUCT_DESIGN, SPONSOR_LOGO, DOCUMENT }

data class MediaAsset(
    val id: UUID,
    val organizationId: UUID,
    val uploadedByUserId: UUID,
    val intendedUsageSlot: MediaUsageSlot,
    val originalFileName: String,
    val declaredContentType: String,
    val detectedContentType: String?,
    val storageKey: String,
    val byteSize: Long?,
    val checksumSha256: String?,
    val widthPx: Int?,
    val heightPx: Int?,
    val status: MediaAssetStatus,
    val rejectionReason: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)
