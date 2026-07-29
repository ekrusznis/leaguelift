package com.leaguelift.media.application

import com.leaguelift.audit.application.AuditService
import com.leaguelift.common.error.NotFoundException
import com.leaguelift.common.error.ValidationException
import com.leaguelift.common.web.CurrentUser
import com.leaguelift.media.domain.MediaAssetStatus
import com.leaguelift.media.domain.MediaAssignment
import com.leaguelift.media.domain.MediaEntityType
import com.leaguelift.media.domain.MediaUsageSlot
import com.leaguelift.media.domain.PublicationStatus
import com.leaguelift.media.domain.Visibility
import com.leaguelift.media.persistence.MediaAssetRepository
import com.leaguelift.media.persistence.MediaAssignmentRepository
import com.leaguelift.membership.application.MembershipService
import com.leaguelift.outbox.application.OutboxWriter
import com.leaguelift.publicpage.domain.PageStatus
import com.leaguelift.publicpage.domain.PageType
import com.leaguelift.publicpage.persistence.PublicPageRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Assigns a READY media asset to an organization's LOGO/COVER slot (DESIGN-DOC.md
 * section 11.3). Re-assigning a slot retires the prior assignment and archives its
 * asset rather than versioning within one asset — simpler, and nothing in this slice
 * needs upload history beyond what ARCHIVED already preserves.
 */
@Service
class MediaAssignmentService(
	private val mediaAssignmentRepository: MediaAssignmentRepository,
	private val mediaAssetRepository: MediaAssetRepository,
	private val publicPageRepository: PublicPageRepository,
	private val membershipService: MembershipService,
	private val auditService: AuditService,
	private val outboxWriter: OutboxWriter,
) {

	@Transactional
	fun assign(
		organizationId: UUID,
		usageSlot: MediaUsageSlot,
		assetId: UUID,
		altText: String?,
		currentUser: CurrentUser,
	): MediaAssignment {
		membershipService.requireManagerRole(organizationId, currentUser)
		val asset = mediaAssetRepository.findById(assetId, organizationId)
			?: throw NotFoundException("MEDIA_ASSET_NOT_FOUND", "The media asset could not be found.")
		if (asset.status != MediaAssetStatus.READY) {
			throw ValidationException("Only a successfully uploaded asset can be assigned.")
		}

		val visibility = computeVisibility(organizationId)
		val publicationStatus = if (visibility == Visibility.PUBLIC) PublicationStatus.PUBLISHED else PublicationStatus.PRIVATE

		val previous = mediaAssignmentRepository.findActiveBySlot(MediaEntityType.ORGANIZATION, organizationId, usageSlot)
		if (previous != null) {
			mediaAssignmentRepository.retire(previous.id, organizationId)
			mediaAssetRepository.archive(previous.assetId, organizationId)
		}

		val assignment = mediaAssignmentRepository.insert(
			organizationId = organizationId,
			assetId = assetId,
			entityType = MediaEntityType.ORGANIZATION,
			entityId = organizationId,
			usageSlot = usageSlot,
			publicationStatus = publicationStatus,
			visibility = visibility,
			altText = altText,
		)
		auditService.record(
			actorUserId = currentUser.userId,
			organizationId = organizationId,
			action = "media.assigned",
			entityType = "media_assignment",
			entityId = assignment.id,
		)
		if (visibility == Visibility.PUBLIC) {
			outboxWriter.write(
				aggregateType = "media_assignment",
				aggregateId = assignment.id,
				organizationId = organizationId,
				eventType = "media.assignment.published",
				payloadJson = """{"assignmentId":"${assignment.id}","usageSlot":"${usageSlot.name}"}""",
			)
		}
		return assignment
	}

	@Transactional
	fun remove(organizationId: UUID, usageSlot: MediaUsageSlot, currentUser: CurrentUser) {
		membershipService.requireManagerRole(organizationId, currentUser)
		val active = mediaAssignmentRepository.findActiveBySlot(MediaEntityType.ORGANIZATION, organizationId, usageSlot)
			?: throw NotFoundException("MEDIA_ASSIGNMENT_NOT_FOUND", "No active assignment for this slot.")
		mediaAssignmentRepository.retire(active.id, organizationId)
		auditService.record(
			actorUserId = currentUser.userId,
			organizationId = organizationId,
			action = "media.retired",
			entityType = "media_assignment",
			entityId = active.id,
		)
	}

	fun listActive(organizationId: UUID, currentUser: CurrentUser): List<MediaAssignment> {
		membershipService.requireActiveMembership(organizationId, currentUser)
		return mediaAssignmentRepository.listActive(MediaEntityType.ORGANIZATION, organizationId)
	}

	/**
	 * PUBLIC once the organization's public page is PUBLISHED, else ORGANIZATION_PRIVATE
	 * (DESIGN-DOC.md section 11.3). Computed at assign-time only — not retroactively
	 * recomputed if the page later publishes/unpublishes (documented gap, cheap
	 * fast-follow: have PublicPageService touch media assignments on publish/unpublish).
	 */
	private fun computeVisibility(organizationId: UUID): Visibility {
		val page = publicPageRepository.findByEntityId(organizationId) ?: return Visibility.ORGANIZATION_PRIVATE
		return if (page.pageType == PageType.ORGANIZATION && page.status == PageStatus.PUBLISHED) {
			Visibility.PUBLIC
		} else {
			Visibility.ORGANIZATION_PRIVATE
		}
	}
}
