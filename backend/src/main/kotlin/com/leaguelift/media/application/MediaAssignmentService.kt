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
 * Assigns a READY media asset to an entity's usage slot (DESIGN-DOC.md section
 * 11.3) — ORGANIZATION (LOGO/COVER, branding) and PRODUCT (PRODUCT_DESIGN, Phase 4
 * store) for now. Re-assigning a slot retires the prior assignment and archives
 * its asset rather than versioning within one asset — simpler, and nothing in
 * this slice needs upload history beyond what ARCHIVED already preserves.
 *
 * Visibility is computed differently per entity type (an organization's page-
 * publish status vs. a product's own ACTIVE status), and those two questions live
 * in different modules — rather than give this module a dependency back on
 * `store` (or `publicpage` reaching further than it already does), the generic
 * [assign] takes a `visibilityOf` supplier the caller provides, invoked only after
 * asset validation succeeds. [assignOrganizationMedia] preserves the original
 * organization-branding behavior/contract exactly, computing that decision
 * internally as before.
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
		entityType: MediaEntityType,
		entityId: UUID,
		usageSlot: MediaUsageSlot,
		assetId: UUID,
		altText: String?,
		currentUser: CurrentUser,
		visibilityOf: () -> Visibility,
	): MediaAssignment {
		membershipService.requireManagerRole(organizationId, currentUser)
		val asset = mediaAssetRepository.findById(assetId, organizationId)
			?: throw NotFoundException("MEDIA_ASSET_NOT_FOUND", "The media asset could not be found.")
		if (asset.status != MediaAssetStatus.READY) {
			throw ValidationException("Only a successfully uploaded asset can be assigned.")
		}

		// Deferred until after validation above — computing this eagerly (e.g. an
		// organization's public-page lookup) would run an extra query even when the
		// asset lookup/READY check is about to fail the whole call anyway.
		val visibility = visibilityOf()
		val publicationStatus = if (visibility == Visibility.PUBLIC) PublicationStatus.PUBLISHED else PublicationStatus.PRIVATE

		val previous = mediaAssignmentRepository.findActiveBySlot(entityType, entityId, usageSlot)
		if (previous != null) {
			mediaAssignmentRepository.retire(previous.id, organizationId)
			mediaAssetRepository.archive(previous.assetId, organizationId)
		}

		val assignment = mediaAssignmentRepository.insert(
			organizationId = organizationId,
			assetId = assetId,
			entityType = entityType,
			entityId = entityId,
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
	fun remove(organizationId: UUID, entityType: MediaEntityType, entityId: UUID, usageSlot: MediaUsageSlot, currentUser: CurrentUser) {
		membershipService.requireManagerRole(organizationId, currentUser)
		val active = mediaAssignmentRepository.findActiveBySlot(entityType, entityId, usageSlot)
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

	fun listActive(organizationId: UUID, entityType: MediaEntityType, entityId: UUID, currentUser: CurrentUser): List<MediaAssignment> {
		membershipService.requireActiveMembership(organizationId, currentUser)
		return mediaAssignmentRepository.listActive(entityType, entityId)
	}

	/** The only PUBLIC-visibility assignment a fully unauthenticated caller (e.g. a public storefront) may ever see — never returns a PRIVATE one. */
	fun getPublicAssignment(entityType: MediaEntityType, entityId: UUID, usageSlot: MediaUsageSlot): MediaAssignment? =
		mediaAssignmentRepository.findActiveBySlot(entityType, entityId, usageSlot)?.takeIf { it.visibility == Visibility.PUBLIC }

	/** No visibility filter — for internal backend-to-provider calls (e.g. handing a design image to Printify at fulfillment time), never for responses sent to a browser. */
	fun getActiveAssignment(entityType: MediaEntityType, entityId: UUID, usageSlot: MediaUsageSlot): MediaAssignment? =
		mediaAssignmentRepository.findActiveBySlot(entityType, entityId, usageSlot)

	fun assignOrganizationMedia(organizationId: UUID, usageSlot: MediaUsageSlot, assetId: UUID, altText: String?, currentUser: CurrentUser): MediaAssignment =
		assign(organizationId, MediaEntityType.ORGANIZATION, organizationId, usageSlot, assetId, altText, currentUser) {
			computeOrganizationVisibility(organizationId)
		}

	fun removeOrganizationMedia(organizationId: UUID, usageSlot: MediaUsageSlot, currentUser: CurrentUser) =
		remove(organizationId, MediaEntityType.ORGANIZATION, organizationId, usageSlot, currentUser)

	fun listActiveOrganizationMedia(organizationId: UUID, currentUser: CurrentUser): List<MediaAssignment> =
		listActive(organizationId, MediaEntityType.ORGANIZATION, organizationId, currentUser)

	/**
	 * PUBLIC once the organization's public page is PUBLISHED, else ORGANIZATION_PRIVATE
	 * (DESIGN-DOC.md section 11.3). Computed at assign-time only — not retroactively
	 * recomputed if the page later publishes/unpublishes (documented gap, cheap
	 * fast-follow: have PublicPageService touch media assignments on publish/unpublish).
	 */
	private fun computeOrganizationVisibility(organizationId: UUID): Visibility {
		val page = publicPageRepository.findByEntityId(organizationId) ?: return Visibility.ORGANIZATION_PRIVATE
		return if (page.pageType == PageType.ORGANIZATION && page.status == PageStatus.PUBLISHED) {
			Visibility.PUBLIC
		} else {
			Visibility.ORGANIZATION_PRIVATE
		}
	}
}
