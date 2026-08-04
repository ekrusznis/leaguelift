package com.rally26.document.application

import com.rally26.audit.application.AuditService
import com.rally26.authorization.application.AuthorizationService
import com.rally26.authorization.domain.Capabilities
import com.rally26.authorization.persistence.GuardianRelationshipRepository
import com.rally26.common.error.ForbiddenException
import com.rally26.common.error.NotFoundException
import com.rally26.common.error.ValidationException
import com.rally26.common.web.CurrentUser
import com.rally26.document.domain.DocumentAcknowledgment
import com.rally26.document.persistence.DocumentAcknowledgmentRepository
import com.rally26.household.persistence.HouseholdRepository
import com.rally26.media.domain.MediaAssetStatus
import com.rally26.media.domain.MediaAssignment
import com.rally26.media.domain.MediaEntityType
import com.rally26.media.domain.MediaUsageSlot
import com.rally26.media.domain.PublicationStatus
import com.rally26.media.domain.Visibility
import com.rally26.media.persistence.MediaAssetRepository
import com.rally26.media.persistence.MediaAssignmentRepository
import com.rally26.membership.application.MembershipService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

private const val HOUSEHOLD_LISTING_LIMIT = 500

/**
 * Household/org document storage (DESIGN-DOC.md section 13, Phase 7 completion) —
 * generalizes the media pipeline beyond images. Reuses [MediaUploadService] as-is for
 * the upload/validation half (unchanged: still org-manager-only, so a guardian cannot
 * self-upload a document to their own household in this slice — a documented scope
 * cut, not an oversight) and [MediaAssignmentRepository] directly (bypassing
 * [com.rally26.media.application.MediaAssignmentService]'s single-active-slot
 * orchestration, which would incorrectly retire a household's previous document every
 * time a new one is added) for the entity-link half.
 */
@Service
class DocumentService(
	private val mediaAssetRepository: MediaAssetRepository,
	private val mediaAssignmentRepository: MediaAssignmentRepository,
	private val membershipService: MembershipService,
	private val authorizationService: AuthorizationService,
	private val householdRepository: HouseholdRepository,
	private val guardianRelationshipRepository: GuardianRelationshipRepository,
	private val documentAcknowledgmentRepository: DocumentAcknowledgmentRepository,
	private val auditService: AuditService,
) {

	@Transactional
	fun assignToOrganization(organizationId: UUID, assetId: UUID, title: String?, currentUser: CurrentUser): MediaAssignment {
		membershipService.requireManagerRole(organizationId, currentUser)
		val asset = requireReadyDocumentAsset(organizationId, assetId)
		val assignment = mediaAssignmentRepository.insert(
			organizationId = organizationId,
			assetId = asset.id,
			entityType = MediaEntityType.ORGANIZATION,
			entityId = organizationId,
			usageSlot = MediaUsageSlot.DOCUMENT,
			publicationStatus = PublicationStatus.APPROVED,
			visibility = Visibility.ORGANIZATION_PRIVATE,
			altText = title,
		)
		auditService.record(currentUser.userId, organizationId, "document.assigned_organization", "media_assignment", assignment.id)
		return assignment
	}

	@Transactional
	fun assignToHousehold(organizationId: UUID, householdId: UUID, assetId: UUID, title: String?, currentUser: CurrentUser): MediaAssignment {
		membershipService.requireManagerRole(organizationId, currentUser)
		requireNotNull(householdRepository.findById(householdId, organizationId)) { "Household not found in this organization." }
		val asset = requireReadyDocumentAsset(organizationId, assetId)
		val assignment = mediaAssignmentRepository.insert(
			organizationId = organizationId,
			assetId = asset.id,
			entityType = MediaEntityType.HOUSEHOLD,
			entityId = householdId,
			usageSlot = MediaUsageSlot.DOCUMENT,
			publicationStatus = PublicationStatus.APPROVED,
			visibility = Visibility.HOUSEHOLD_PRIVATE,
			altText = title,
		)
		auditService.record(currentUser.userId, organizationId, "document.assigned_household", "media_assignment", assignment.id)
		return assignment
	}

	/** Convenience broadcast — one assignment per active household, all sharing the same underlying asset (e.g. a season waiver every family must sign). */
	@Transactional
	fun assignToAllHouseholds(organizationId: UUID, assetId: UUID, title: String?, currentUser: CurrentUser): List<MediaAssignment> {
		membershipService.requireManagerRole(organizationId, currentUser)
		val asset = requireReadyDocumentAsset(organizationId, assetId)
		val households = householdRepository.findAll(organizationId, 0, HOUSEHOLD_LISTING_LIMIT)
		val assignments = households.map { household ->
			mediaAssignmentRepository.insert(
				organizationId = organizationId,
				assetId = asset.id,
				entityType = MediaEntityType.HOUSEHOLD,
				entityId = household.id,
				usageSlot = MediaUsageSlot.DOCUMENT,
				publicationStatus = PublicationStatus.APPROVED,
				visibility = Visibility.HOUSEHOLD_PRIVATE,
				altText = title,
			)
		}
		auditService.record(currentUser.userId, organizationId, "document.broadcast_all_households", "media_asset", assetId)
		return assignments
	}

	fun listOrganizationDocuments(organizationId: UUID, currentUser: CurrentUser): List<MediaAssignment> {
		membershipService.requireActiveMembership(organizationId, currentUser)
		return mediaAssignmentRepository.listActive(MediaEntityType.ORGANIZATION, organizationId)
			.filter { it.usageSlot == MediaUsageSlot.DOCUMENT }
	}

	fun listHouseholdDocuments(organizationId: UUID, householdId: UUID, currentUser: CurrentUser): List<MediaAssignment> {
		if (!authorizationService.hasHouseholdCapability(organizationId, householdId, currentUser, Capabilities.HOUSEHOLD_VIEW)) {
			throw ForbiddenException("HOUSEHOLD_ACCESS_DENIED", "You do not have access to this household's documents.")
		}
		return mediaAssignmentRepository.listActive(MediaEntityType.HOUSEHOLD, householdId)
			.filter { it.usageSlot == MediaUsageSlot.DOCUMENT }
	}

	/** Retires the assignment only — never archives the asset, which may still back other households' assignments from a broadcast. */
	@Transactional
	fun removeDocument(organizationId: UUID, assignmentId: UUID, currentUser: CurrentUser) {
		membershipService.requireManagerRole(organizationId, currentUser)
		val assignment = requireDocumentAssignment(organizationId, assignmentId)
		mediaAssignmentRepository.retire(assignment.id, organizationId)
		auditService.record(currentUser.userId, organizationId, "document.retired", "media_assignment", assignment.id)
	}

	/** Idempotent: acknowledging an already-acknowledged document returns the existing record rather than erroring — a guardian re-submitting isn't a mistake worth surfacing. */
	@Transactional
	fun acknowledge(organizationId: UUID, householdId: UUID, assignmentId: UUID, currentUser: CurrentUser): DocumentAcknowledgment {
		val relationship = guardianRelationshipRepository.findActiveForHousehold(currentUser.userId, householdId)
			?: throw ForbiddenException("NOT_A_GUARDIAN", "Only a guardian of this household can acknowledge its documents.")
		val assignment = requireDocumentAssignment(organizationId, assignmentId)
		if (assignment.entityType != MediaEntityType.HOUSEHOLD || assignment.entityId != householdId) {
			throw ValidationException("This document is not assigned to this household.")
		}
		documentAcknowledgmentRepository.findFor(assignmentId, relationship.householdAdultId)?.let { return it }
		val acknowledgment = documentAcknowledgmentRepository.insert(
			organizationId = organizationId,
			mediaAssignmentId = assignmentId,
			householdId = householdId,
			householdAdultId = relationship.householdAdultId,
			acknowledgedByUserId = currentUser.userId,
		)
		auditService.record(currentUser.userId, organizationId, "document.acknowledged", "document_acknowledgment", acknowledgment.id)
		return acknowledgment
	}

	/**
	 * Org managers see this to know who still needs to sign; a guardian of the
	 * household this document belongs to can see it too, to check their own
	 * acknowledgment status (there's no separate "have I signed this" endpoint —
	 * the full list is small enough that a guardian scanning it for their own name
	 * is the smallest complete workflow here). An org-level document (no household)
	 * is manager-only, since there's no guardian relationship to check against.
	 */
	fun listAcknowledgments(organizationId: UUID, assignmentId: UUID, currentUser: CurrentUser): List<DocumentAcknowledgment> {
		val assignment = requireDocumentAssignment(organizationId, assignmentId)
		val authorized = if (assignment.entityType == MediaEntityType.HOUSEHOLD) {
			authorizationService.hasHouseholdCapability(organizationId, assignment.entityId, currentUser, Capabilities.HOUSEHOLD_VIEW)
		} else {
			runCatching { membershipService.requireManagerRole(organizationId, currentUser) }.isSuccess
		}
		if (!authorized) {
			throw ForbiddenException("DOCUMENT_ACCESS_DENIED", "You do not have access to this document's acknowledgments.")
		}
		return documentAcknowledgmentRepository.listForAssignment(assignmentId)
	}

	private fun requireReadyDocumentAsset(organizationId: UUID, assetId: UUID) =
		mediaAssetRepository.findById(assetId, organizationId)
			?.also { if (it.status != MediaAssetStatus.READY) throw ValidationException("Only a successfully uploaded asset can be assigned.") }
			?.also { if (it.intendedUsageSlot != MediaUsageSlot.DOCUMENT) throw ValidationException("This asset was not uploaded as a document.") }
			?: throw NotFoundException("MEDIA_ASSET_NOT_FOUND", "The media asset could not be found.")

	private fun requireDocumentAssignment(organizationId: UUID, assignmentId: UUID): MediaAssignment {
		val assignment = mediaAssignmentRepository.findById(assignmentId, organizationId)
			?.takeIf { it.usageSlot == MediaUsageSlot.DOCUMENT && it.publicationStatus != PublicationStatus.RETIRED }
		return assignment ?: throw NotFoundException("DOCUMENT_NOT_FOUND", "The document could not be found.")
	}
}
