package com.rally26.media.web

import com.rally26.media.application.MediaDescriptor
import java.time.Instant
import java.util.UUID

data class AssignHouseholdMediaRequest(
    val assetId: UUID,
)

data class HouseholdMediaResponse(
    val id: UUID,
    val assetId: UUID,
    val url: String,
    val contentType: String?,
    val byteSizeBytes: Long?,
    val widthPx: Int?,
    val heightPx: Int?,
    val isVideo: Boolean,
    val visibility: String,
    val publicationStatus: String,
    val createdAt: Instant,
    val updatedAt: Instant,
)

fun MediaDescriptor.toHouseholdMediaResponse() =
    HouseholdMediaResponse(
        id = assignment.id,
        assetId = assignment.assetId,
        url = url,
        contentType = contentType,
        byteSizeBytes = byteSizeBytes,
        widthPx = widthPx,
        heightPx = heightPx,
        isVideo = contentType?.startsWith("video/") == true,
        visibility = assignment.visibility.name,
        publicationStatus = assignment.publicationStatus.name,
        createdAt = assignment.createdAt,
        updatedAt = assignment.updatedAt,
    )

data class HouseholdMediaListResponse(
    val items: List<HouseholdMediaResponse>,
)

data class ReleaseHouseholdMediaPubliclyRequest(
    val assignmentIds: List<UUID>,
)
