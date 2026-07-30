package com.leaguelift.media.persistence

import com.leaguelift.media.domain.MediaAssignment
import com.leaguelift.media.domain.MediaEntityType
import com.leaguelift.media.domain.MediaUsageSlot
import com.leaguelift.media.domain.PublicationStatus
import com.leaguelift.media.domain.Visibility
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

private const val MEDIA_ASSIGNMENT_COLUMNS = """
    id, organization_id, asset_id, entity_type, entity_id, usage_slot,
    publication_status, visibility, alt_text, created_at, updated_at
"""

@Repository
class MediaAssignmentRepository(private val jdbcClient: JdbcClient) {

	fun findById(id: UUID, organizationId: UUID): MediaAssignment? =
		jdbcClient.sql("select $MEDIA_ASSIGNMENT_COLUMNS from media_assignment where id = :id and organization_id = :organizationId")
			.param("id", id)
			.param("organizationId", organizationId)
			.query(::mapRow)
			.optional()
			.orElse(null)

	fun findActiveBySlot(entityType: MediaEntityType, entityId: UUID, usageSlot: MediaUsageSlot): MediaAssignment? =
		jdbcClient.sql(
			"""
            select $MEDIA_ASSIGNMENT_COLUMNS from media_assignment
            where entity_type = :entityType and entity_id = :entityId and usage_slot = :usageSlot
              and publication_status <> 'RETIRED'
            """.trimIndent(),
		)
			.param("entityType", entityType.name)
			.param("entityId", entityId)
			.param("usageSlot", usageSlot.name)
			.query(::mapRow)
			.optional()
			.orElse(null)

	fun listActive(entityType: MediaEntityType, entityId: UUID): List<MediaAssignment> =
		jdbcClient.sql(
			"""
            select $MEDIA_ASSIGNMENT_COLUMNS from media_assignment
            where entity_type = :entityType and entity_id = :entityId and publication_status <> 'RETIRED'
            order by usage_slot asc
            """.trimIndent(),
		)
			.param("entityType", entityType.name)
			.param("entityId", entityId)
			.query(::mapRow)
			.list()

	fun insert(
		organizationId: UUID,
		assetId: UUID,
		entityType: MediaEntityType,
		entityId: UUID,
		usageSlot: MediaUsageSlot,
		publicationStatus: PublicationStatus,
		visibility: Visibility,
		altText: String?,
	): MediaAssignment {
		val now = Instant.now()
		val id = UUID.randomUUID()
		jdbcClient.sql(
			"""
            insert into media_assignment
                (id, organization_id, asset_id, entity_type, entity_id, usage_slot,
                 publication_status, visibility, alt_text, created_at, updated_at)
            values
                (:id, :organizationId, :assetId, :entityType, :entityId, :usageSlot,
                 :publicationStatus, :visibility, :altText, :now, :now)
            """.trimIndent(),
		)
			.param("id", id)
			.param("organizationId", organizationId)
			.param("assetId", assetId)
			.param("entityType", entityType.name)
			.param("entityId", entityId)
			.param("usageSlot", usageSlot.name)
			.param("publicationStatus", publicationStatus.name)
			.param("visibility", visibility.name)
			.param("altText", altText)
			.param("now", Timestamp.from(now))
			.update()
		return MediaAssignment(
			id = id,
			organizationId = organizationId,
			assetId = assetId,
			entityType = entityType,
			entityId = entityId,
			usageSlot = usageSlot,
			publicationStatus = publicationStatus,
			visibility = visibility,
			altText = altText,
			createdAt = now,
			updatedAt = now,
		)
	}

	fun retire(id: UUID, organizationId: UUID): Int {
		val now = Instant.now()
		return jdbcClient.sql(
			"""
            update media_assignment set publication_status = 'RETIRED', updated_at = :now
            where id = :id and organization_id = :organizationId
            """.trimIndent(),
		)
			.param("now", Timestamp.from(now))
			.param("id", id)
			.param("organizationId", organizationId)
			.update()
	}

	private fun mapRow(rs: ResultSet, rowNum: Int): MediaAssignment =
		MediaAssignment(
			id = rs.getObject("id", UUID::class.java),
			organizationId = rs.getObject("organization_id", UUID::class.java),
			assetId = rs.getObject("asset_id", UUID::class.java),
			entityType = MediaEntityType.valueOf(rs.getString("entity_type")),
			entityId = rs.getObject("entity_id", UUID::class.java),
			usageSlot = MediaUsageSlot.valueOf(rs.getString("usage_slot")),
			publicationStatus = PublicationStatus.valueOf(rs.getString("publication_status")),
			visibility = Visibility.valueOf(rs.getString("visibility")),
			altText = rs.getString("alt_text"),
			createdAt = rs.getTimestamp("created_at").toInstant(),
			updatedAt = rs.getTimestamp("updated_at").toInstant(),
		)
}
