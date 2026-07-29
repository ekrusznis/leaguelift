package com.leaguelift.media.domain

import java.time.Instant
import java.util.UUID

/** ORGANIZATION (branding) and PRODUCT (Phase 4 store design) — team/tournament logos remain an intentional fast-follow (DESIGN-DOC.md section 11.3). */
enum class MediaEntityType { ORGANIZATION, PRODUCT }

enum class PublicationStatus { PRIVATE, APPROVED, PUBLISHED, RETIRED }

enum class Visibility { PUBLIC, AUTHENTICATED, ORGANIZATION_PRIVATE, TEAM_PRIVATE, HOUSEHOLD_PRIVATE, SELF_PRIVATE, PLATFORM_PRIVATE }

data class MediaAssignment(
	val id: UUID,
	val organizationId: UUID,
	val assetId: UUID,
	val entityType: MediaEntityType,
	val entityId: UUID,
	val usageSlot: MediaUsageSlot,
	val publicationStatus: PublicationStatus,
	val visibility: Visibility,
	val altText: String?,
	val createdAt: Instant,
	val updatedAt: Instant,
)
