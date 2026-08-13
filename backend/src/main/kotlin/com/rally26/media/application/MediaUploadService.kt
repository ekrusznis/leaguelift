package com.rally26.media.application

import com.rally26.audit.application.AuditService
import com.rally26.common.error.FieldError
import com.rally26.common.error.ForbiddenException
import com.rally26.common.error.NotFoundException
import com.rally26.common.error.ValidationException
import com.rally26.common.web.CurrentUser
import com.rally26.media.domain.MediaAsset
import com.rally26.media.domain.MediaAssetStatus
import com.rally26.media.domain.MediaEntityType
import com.rally26.media.domain.MediaUsageSlot
import com.rally26.media.domain.UploadLimits
import com.rally26.media.infra.SpacesClient
import com.rally26.media.persistence.MediaAssetRepository
import com.rally26.membership.application.MembershipService
import com.rally26.outbox.application.OutboxWriter
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.UUID
import javax.imageio.ImageIO

data class RequestUploadResult(
    val asset: MediaAsset,
    val uploadUrl: String,
    val expiresAt: Instant,
)

data class ConfirmUploadResult(
    val asset: MediaAsset,
)

private val EXTENSION_BY_CONTENT_TYPE =
    mapOf(
        "image/png" to "png",
        "image/jpeg" to "jpg",
        "image/webp" to "webp",
        "image/svg+xml" to "svg",
        "application/pdf" to "pdf",
        "video/mp4" to "mp4",
        "video/quicktime" to "mov",
    )

/**
 * Requests presigned upload URLs and validates uploaded bytes synchronously on confirm
 * (DESIGN-DOC.md section 11.3, ADR-012 — no async worker exists yet, and pilot-scale
 * file sizes make synchronous validation acceptable). The presigned PUT's Content-Type
 * binding only stops a *header* mismatch; [confirmUpload] re-derives the real type from
 * the uploaded bytes (defense in depth against a bytes/header mismatch).
 */
@Service
class MediaUploadService(
    private val mediaAssetRepository: MediaAssetRepository,
    private val spacesClient: SpacesClient,
    private val membershipService: MembershipService,
    private val mediaEntityAccessService: MediaEntityAccessService,
    private val auditService: AuditService,
    private val outboxWriter: OutboxWriter,
) {
    @Transactional
    fun requestUpload(
        organizationId: UUID,
        usageSlot: MediaUsageSlot,
        fileName: String,
        contentType: String,
        fileSizeBytes: Long,
        currentUser: CurrentUser,
        entityType: MediaEntityType? = null,
        entityId: UUID? = null,
    ): RequestUploadResult {
        if ((entityType == null) != (entityId == null)) {
            throw ValidationException("entityType and entityId must be supplied together.")
        }
        if (entityType != null && entityId != null) {
            val target = mediaEntityAccessService.resolveForManage(organizationId, entityType, entityId, currentUser)
            mediaEntityAccessService.requireAllowedSlot(target, usageSlot)
        } else {
            membershipService.requireManagerRole(organizationId, currentUser)
        }
        if (!UploadLimits.isContentTypeAllowed(usageSlot, contentType)) {
            throw ValidationException(
                "This file type is not allowed for $usageSlot.",
                listOf(
                    FieldError(
                        "contentType",
                        "Allowed types: ${UploadLimits.allowedContentTypes(usageSlot).joinToString()}.",
                    ),
                ),
            )
        }
        val maxBytes = UploadLimits.maxBytes(usageSlot, contentType)
        if (fileSizeBytes > maxBytes) {
            throw ValidationException(
                "The file is too large for $usageSlot.",
                listOf(FieldError("fileSizeBytes", "Must be at most $maxBytes bytes.")),
            )
        }

        val assetId = UUID.randomUUID()
        val extension = EXTENSION_BY_CONTENT_TYPE.getValue(contentType)
        val storageKey = "organizations/$organizationId/media/$assetId/original.$extension"
        val asset =
            mediaAssetRepository.insert(
                id = assetId,
                organizationId = organizationId,
                uploadedByUserId = currentUser.userId,
                usageSlot = usageSlot,
                originalFileName = fileName,
                declaredContentType = contentType,
                storageKey = storageKey,
            )
        val presigned = spacesClient.presignedPutUrl(storageKey, contentType, Duration.ofMinutes(15))
        auditService.record(
            actorUserId = currentUser.userId,
            organizationId = organizationId,
            action = "media.upload_requested",
            entityType = "media_asset",
            entityId = assetId,
        )
        return RequestUploadResult(asset, presigned.url, presigned.expiresAt)
    }

    @Transactional
    fun confirmUpload(
        organizationId: UUID,
        assetId: UUID,
        currentUser: CurrentUser,
    ): ConfirmUploadResult {
        val asset =
            mediaAssetRepository.findById(assetId, organizationId)
                ?: throw NotFoundException("MEDIA_ASSET_NOT_FOUND", "The media asset could not be found.")
        if (asset.uploadedByUserId != currentUser.userId && !membershipService.hasManagerRole(organizationId, currentUser)) {
            throw ForbiddenException("MEDIA_UPLOAD_ACCESS_DENIED", "You cannot confirm this upload.")
        }
        if (asset.status != MediaAssetStatus.PENDING_UPLOAD) {
            throw ValidationException("This asset has already been confirmed.")
        }

        val head = spacesClient.headObject(asset.storageKey)
        if (!head.exists) {
            throw ValidationException("No file was found at the upload location. Upload the file before confirming.")
        }

        val maxBytes = UploadLimits.maxBytes(asset.intendedUsageSlot, asset.declaredContentType)
        val bytes = spacesClient.getObjectBytesCapped(asset.storageKey, maxBytes)

        val rejectionReason = rejectionReasonFor(asset, bytes, maxBytes)
        if (rejectionReason != null) {
            mediaAssetRepository.markConfirmed(
                id = assetId,
                organizationId = organizationId,
                status = MediaAssetStatus.REJECTED,
                detectedContentType = null,
                byteSize = bytes.size.toLong(),
                checksumSha256 = null,
                widthPx = null,
                heightPx = null,
                rejectionReason = rejectionReason,
            )
            auditService.record(
                actorUserId = currentUser.userId,
                organizationId = organizationId,
                action = "media.rejected",
                entityType = "media_asset",
                entityId = assetId,
            )
            return ConfirmUploadResult(mediaAssetRepository.findById(assetId, organizationId)!!)
        }

        // rejectionReasonFor returning null guarantees detectContentType succeeded.
        val detectedContentType = UploadLimits.detectContentType(bytes)!!
        val dimensions = imageDimensions(detectedContentType, bytes)
        val checksum = sha256Hex(bytes)

        mediaAssetRepository.markConfirmed(
            id = assetId,
            organizationId = organizationId,
            status = MediaAssetStatus.READY,
            detectedContentType = detectedContentType,
            byteSize = bytes.size.toLong(),
            checksumSha256 = checksum,
            widthPx = dimensions?.first,
            heightPx = dimensions?.second,
            rejectionReason = null,
        )
        auditService.record(
            actorUserId = currentUser.userId,
            organizationId = organizationId,
            action = "media.confirmed",
            entityType = "media_asset",
            entityId = assetId,
        )
        outboxWriter.write(
            aggregateType = "media_asset",
            aggregateId = assetId,
            organizationId = organizationId,
            eventType = "media.asset.ready",
            payloadJson = """{"assetId":"$assetId","usageSlot":"${asset.intendedUsageSlot.name}"}""",
        )
        return ConfirmUploadResult(mediaAssetRepository.findById(assetId, organizationId)!!)
    }

    private fun rejectionReasonFor(
        asset: MediaAsset,
        bytes: ByteArray,
        maxBytes: Long,
    ): String? {
        if (bytes.size.toLong() > maxBytes) return "FILE_TOO_LARGE"
        val detected = UploadLimits.detectContentType(bytes) ?: return "UNRECOGNIZED_FILE_FORMAT"
        if (detected != asset.declaredContentType) return "CONTENT_TYPE_MISMATCH"
        // SVG and non-image types (e.g. a DOCUMENT slot's PDF) have no raster dimensions
        // to decode/cap — only PNG/JPEG/WebP go through the image-decode check.
        if (detected != "image/svg+xml" && !UploadLimits.isNonImageContentType(detected)) {
            val dimensions = imageDimensions(detected, bytes) ?: return "INVALID_IMAGE"
            if (dimensions.first > UploadLimits.MAX_DIMENSION_PX || dimensions.second > UploadLimits.MAX_DIMENSION_PX) {
                return "IMAGE_DIMENSIONS_TOO_LARGE"
            }
        }
        return null
    }

    private fun imageDimensions(
        contentType: String,
        bytes: ByteArray,
    ): Pair<Int, Int>? {
        if (contentType == "image/svg+xml" || UploadLimits.isNonImageContentType(contentType)) return null
        // ImageIO.read throws (rather than returning null) on some malformed/truncated
        // images (e.g. a valid signature with no image data) — both cases mean
        // "not decodable" for this slice's purposes.
        val image =
            try {
                ImageIO.read(ByteArrayInputStream(bytes))
            } catch (e: java.io.IOException) {
                null
            } ?: return null
        return image.width to image.height
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
