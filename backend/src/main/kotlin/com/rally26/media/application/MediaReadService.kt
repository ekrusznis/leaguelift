package com.rally26.media.application

import com.rally26.media.domain.MediaAssignment
import com.rally26.media.infra.SpacesClient
import com.rally26.media.persistence.MediaAssetRepository
import org.springframework.stereotype.Service
import java.time.Duration

data class MediaDescriptor(
	val assignment: MediaAssignment,
	val url: String,
	val contentType: String?,
	val byteSizeBytes: Long?,
	val widthPx: Int?,
	val heightPx: Int?,
)

/**
 * Turns an assignment + its asset into a descriptor with a freshly-signed GET URL.
 * Kept separate from [MediaAssignmentService] because signing is pure infra with no
 * business logic — reads always go through a signed URL, never a public-read ACL
 * (DESIGN-DOC.md section 11.3, ADR-012).
 */
@Service
class MediaReadService(
	private val mediaAssetRepository: MediaAssetRepository,
	private val spacesClient: SpacesClient,
) {

	fun describe(assignment: MediaAssignment): MediaDescriptor? {
		val asset = mediaAssetRepository.findById(assignment.assetId, assignment.organizationId) ?: return null
		val url = spacesClient.presignedGetUrl(asset.storageKey, Duration.ofMinutes(15))
		return MediaDescriptor(
			assignment = assignment,
			url = url,
			contentType = asset.detectedContentType ?: asset.declaredContentType,
			byteSizeBytes = asset.byteSize,
			widthPx = asset.widthPx,
			heightPx = asset.heightPx,
		)
	}

	fun describeAll(assignments: List<MediaAssignment>): List<MediaDescriptor> = assignments.mapNotNull(::describe)
}
