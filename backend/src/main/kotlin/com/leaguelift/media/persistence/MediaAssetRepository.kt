package com.leaguelift.media.persistence

import com.leaguelift.media.domain.MediaAsset
import com.leaguelift.media.domain.MediaAssetStatus
import com.leaguelift.media.domain.MediaUsageSlot
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

private const val MEDIA_ASSET_COLUMNS = """
    id, organization_id, uploaded_by_user_id, intended_usage_slot, original_file_name,
    declared_content_type, detected_content_type, storage_key, byte_size, checksum_sha256,
    width_px, height_px, status, rejection_reason, created_at, updated_at
"""

@Repository
class MediaAssetRepository(private val jdbcClient: JdbcClient) {

	fun findById(id: UUID, organizationId: UUID): MediaAsset? =
		jdbcClient.sql("select $MEDIA_ASSET_COLUMNS from media_asset where id = :id and organization_id = :organizationId")
			.param("id", id)
			.param("organizationId", organizationId)
			.query(::mapRow)
			.optional()
			.orElse(null)

	fun insert(
		id: UUID,
		organizationId: UUID,
		uploadedByUserId: UUID,
		usageSlot: MediaUsageSlot,
		originalFileName: String,
		declaredContentType: String,
		storageKey: String,
	): MediaAsset {
		val now = Instant.now()
		jdbcClient.sql(
			"""
            insert into media_asset
                (id, organization_id, uploaded_by_user_id, intended_usage_slot, original_file_name,
                 declared_content_type, storage_key, status, created_at, updated_at)
            values
                (:id, :organizationId, :uploadedByUserId, :usageSlot, :originalFileName,
                 :declaredContentType, :storageKey, 'PENDING_UPLOAD', :now, :now)
            """.trimIndent(),
		)
			.param("id", id)
			.param("organizationId", organizationId)
			.param("uploadedByUserId", uploadedByUserId)
			.param("usageSlot", usageSlot.name)
			.param("originalFileName", originalFileName)
			.param("declaredContentType", declaredContentType)
			.param("storageKey", storageKey)
			.param("now", Timestamp.from(now))
			.update()
		return MediaAsset(
			id = id,
			organizationId = organizationId,
			uploadedByUserId = uploadedByUserId,
			intendedUsageSlot = usageSlot,
			originalFileName = originalFileName,
			declaredContentType = declaredContentType,
			detectedContentType = null,
			storageKey = storageKey,
			byteSize = null,
			checksumSha256 = null,
			widthPx = null,
			heightPx = null,
			status = MediaAssetStatus.PENDING_UPLOAD,
			rejectionReason = null,
			createdAt = now,
			updatedAt = now,
		)
	}

	fun markConfirmed(
		id: UUID,
		organizationId: UUID,
		status: MediaAssetStatus,
		detectedContentType: String?,
		byteSize: Long?,
		checksumSha256: String?,
		widthPx: Int?,
		heightPx: Int?,
		rejectionReason: String?,
	): Int {
		val now = Instant.now()
		return jdbcClient.sql(
			"""
            update media_asset
            set status                = :status,
                detected_content_type = :detectedContentType,
                byte_size             = :byteSize,
                checksum_sha256       = :checksumSha256,
                width_px              = :widthPx,
                height_px             = :heightPx,
                rejection_reason      = :rejectionReason,
                updated_at            = :now
            where id = :id and organization_id = :organizationId
            """.trimIndent(),
		)
			.param("status", status.name)
			.param("detectedContentType", detectedContentType)
			.param("byteSize", byteSize)
			.param("checksumSha256", checksumSha256)
			.param("widthPx", widthPx)
			.param("heightPx", heightPx)
			.param("rejectionReason", rejectionReason)
			.param("now", Timestamp.from(now))
			.param("id", id)
			.param("organizationId", organizationId)
			.update()
	}

	fun archive(id: UUID, organizationId: UUID): Int {
		val now = Instant.now()
		return jdbcClient.sql(
			"update media_asset set status = 'ARCHIVED', updated_at = :now where id = :id and organization_id = :organizationId",
		)
			.param("now", Timestamp.from(now))
			.param("id", id)
			.param("organizationId", organizationId)
			.update()
	}

	private fun mapRow(rs: ResultSet, rowNum: Int): MediaAsset =
		MediaAsset(
			id = rs.getObject("id", UUID::class.java),
			organizationId = rs.getObject("organization_id", UUID::class.java),
			uploadedByUserId = rs.getObject("uploaded_by_user_id", UUID::class.java),
			intendedUsageSlot = MediaUsageSlot.valueOf(rs.getString("intended_usage_slot")),
			originalFileName = rs.getString("original_file_name"),
			declaredContentType = rs.getString("declared_content_type"),
			detectedContentType = rs.getString("detected_content_type"),
			storageKey = rs.getString("storage_key"),
			byteSize = rs.getObject("byte_size") as Long?,
			checksumSha256 = rs.getString("checksum_sha256"),
			widthPx = rs.getObject("width_px") as Int?,
			heightPx = rs.getObject("height_px") as Int?,
			status = MediaAssetStatus.valueOf(rs.getString("status")),
			rejectionReason = rs.getString("rejection_reason"),
			createdAt = rs.getTimestamp("created_at").toInstant(),
			updatedAt = rs.getTimestamp("updated_at").toInstant(),
		)
}
