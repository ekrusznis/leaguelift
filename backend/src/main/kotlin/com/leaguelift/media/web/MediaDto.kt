package com.leaguelift.media.web

import com.leaguelift.common.error.FieldError
import com.leaguelift.common.error.ValidationException
import com.leaguelift.media.application.MediaDescriptor
import com.leaguelift.media.application.RequestUploadResult
import com.leaguelift.media.domain.MediaAsset
import com.leaguelift.media.domain.MediaEntityType
import com.leaguelift.media.domain.MediaUsageSlot
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

fun parseUsageSlot(value: String): MediaUsageSlot = try {
	MediaUsageSlot.valueOf(value.uppercase())
} catch (e: IllegalArgumentException) {
	throw ValidationException(
		"Usage slot must be one of: ${MediaUsageSlot.entries.joinToString { it.name }}.",
		listOf(FieldError("usageSlot", "Not a recognized usage slot.")),
	)
}


fun parseEntityType(value: String): MediaEntityType = try {
	MediaEntityType.valueOf(value.uppercase())
} catch (_: IllegalArgumentException) {
	throw ValidationException(
		"Entity type must be one of: ${MediaEntityType.entries.joinToString { it.name }}.",
		listOf(FieldError("entityType", "Not a recognized media entity type.")),
	)
}

data class RequestUploadRequest(
	@field:NotBlank
	val usageSlot: String,
	@field:NotBlank
	@field:Size(min = 1, max = 255)
	val fileName: String,
	@field:NotBlank
	val contentType: String,
	@field:Min(1)
	val fileSizeBytes: Long,
	val entityType: String? = null,
	val entityId: UUID? = null,
)

data class RequestUploadResponse(
	val assetId: UUID,
	val uploadUrl: String,
	val uploadMethod: String,
	val requiredHeaders: Map<String, String>,
	val expiresAt: Instant,
)

fun RequestUploadResult.toResponse() = RequestUploadResponse(
	assetId = asset.id,
	uploadUrl = uploadUrl,
	uploadMethod = "PUT",
	requiredHeaders = mapOf("Content-Type" to asset.declaredContentType),
	expiresAt = expiresAt,
)

data class ConfirmUploadResponse(
	val assetId: UUID,
	val status: String,
	val contentType: String?,
	val byteSize: Long?,
	val widthPx: Int?,
	val heightPx: Int?,
	val rejectionReason: String?,
)

fun MediaAsset.toConfirmResponse() = ConfirmUploadResponse(
	assetId = id,
	status = status.name,
	contentType = detectedContentType ?: declaredContentType,
	byteSize = byteSize,
	widthPx = widthPx,
	heightPx = heightPx,
	rejectionReason = rejectionReason,
)

data class AssignMediaRequest(
	val assetId: UUID,
	@field:Size(max = 255)
	val altText: String? = null,
)

data class MediaAssignmentResponse(
	val usageSlot: String,
	val assetId: UUID,
	val url: String,
	val altText: String?,
	val contentType: String?,
	val widthPx: Int?,
	val heightPx: Int?,
	val byteSizeBytes: Long?,
	val visibility: String,
	val publicationStatus: String,
	val updatedAt: Instant,
)

fun MediaDescriptor.toResponse() = MediaAssignmentResponse(
	usageSlot = assignment.usageSlot.name,
	assetId = assignment.assetId,
	url = url,
	altText = assignment.altText,
	contentType = contentType,
	widthPx = widthPx,
	heightPx = heightPx,
	byteSizeBytes = byteSizeBytes,
	visibility = assignment.visibility.name,
	publicationStatus = assignment.publicationStatus.name,
	updatedAt = assignment.updatedAt,
)

data class MediaAssignmentListResponse(val items: List<MediaAssignmentResponse>)
