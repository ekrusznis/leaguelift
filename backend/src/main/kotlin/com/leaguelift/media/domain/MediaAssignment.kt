package com.leaguelift.media.domain

import java.time.Instant
import java.util.UUID

/** Polymorphic media targets. Phase 16 adds TEAM/TOURNAMENT branding and private adult/participant profile photos to the existing organization/product/sponsor/document pipeline. */
enum class MediaEntityType { ORGANIZATION, TEAM, TOURNAMENT, HOUSEHOLD_ADULT, PARTICIPANT, PRODUCT, SPONSOR, HOUSEHOLD }

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
