package com.rally26.media.application

import com.rally26.audit.application.AuditService
import com.rally26.authorization.application.AuthorizationService
import com.rally26.common.error.ForbiddenException
import com.rally26.common.error.NotFoundException
import com.rally26.common.error.ValidationException
import com.rally26.common.web.CurrentUser
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
import com.rally26.outbox.application.OutboxWriter
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Household media center (Track 5, 2026-08-13) — guardian-uploaded photos/videos, a
 * scoped MVP precursor to the still-deferred Phase 39 AI Highlights plan (upload/
 * store/tag-as-public only, no reel-building or AI tagging). Follows
 * [com.rally26.document.application.DocumentService]'s exact shape (multiple
 * coexisting assignments per household, bypassing [MediaAssignmentService]'s
 * single-active-slot orchestration, which would incorrectly retire a prior item every
 * time a new one is added) with one real difference: uploading here is guardian
 * self-service, not manager-only, so [assign] and [releasePublicly] both accept a
 * guardian of the household, not just org staff — resolved through
 * [MediaEntityAccessService]'s new HOUSEHOLD case (the upload's own `requestUpload`
 * call already went through that same check).
 */
@Service
class HouseholdMediaService(
    private val mediaAssetRepository: MediaAssetRepository,
    private val mediaAssignmentRepository: MediaAssignmentRepository,
    private val membershipService: MembershipService,
    private val authorizationService: AuthorizationService,
    private val householdRepository: HouseholdRepository,
    private val auditService: AuditService,
    private val outboxWriter: OutboxWriter,
) {
    fun list(
        organizationId: UUID,
        householdId: UUID,
        currentUser: CurrentUser,
    ): List<MediaAssignment> {
        requireHouseholdMediaAccess(organizationId, householdId, currentUser)
        return mediaAssignmentRepository
            .listActive(MediaEntityType.HOUSEHOLD, householdId)
            .filter { it.usageSlot == MediaUsageSlot.HOUSEHOLD_MEDIA }
    }

    @Transactional
    fun assign(
        organizationId: UUID,
        householdId: UUID,
        assetId: UUID,
        currentUser: CurrentUser,
    ): MediaAssignment {
        requireHouseholdMediaAccess(organizationId, householdId, currentUser)
        householdRepository.findById(householdId, organizationId)
            ?: throw NotFoundException("HOUSEHOLD_NOT_FOUND", "The household could not be found.")
        val asset =
            mediaAssetRepository.findById(assetId, organizationId)
                ?: throw NotFoundException("MEDIA_ASSET_NOT_FOUND", "The media asset could not be found.")
        if (asset.status != MediaAssetStatus.READY) {
            throw ValidationException("Only a successfully uploaded asset can be added to the media center.")
        }
        if (asset.intendedUsageSlot != MediaUsageSlot.HOUSEHOLD_MEDIA) {
            throw ValidationException("This asset was not uploaded as household media.")
        }
        val assignment =
            mediaAssignmentRepository.insert(
                organizationId = organizationId,
                assetId = asset.id,
                entityType = MediaEntityType.HOUSEHOLD,
                entityId = householdId,
                usageSlot = MediaUsageSlot.HOUSEHOLD_MEDIA,
                publicationStatus = PublicationStatus.APPROVED,
                visibility = Visibility.HOUSEHOLD_PRIVATE,
                altText = null,
            )
        auditService.record(currentUser.userId, organizationId, "household_media.added", "media_assignment", assignment.id, householdId = householdId)
        return assignment
    }

    @Transactional
    fun remove(
        organizationId: UUID,
        householdId: UUID,
        assignmentId: UUID,
        currentUser: CurrentUser,
    ) {
        requireHouseholdMediaAccess(organizationId, householdId, currentUser)
        val assignment = requireHouseholdMediaAssignment(organizationId, householdId, assignmentId)
        mediaAssignmentRepository.retire(assignment.id, organizationId)
        auditService.record(currentUser.userId, organizationId, "household_media.removed", "media_assignment", assignment.id, householdId = householdId)
    }

    /**
     * Bulk "release publicly" — enables org use for social sharing/highlight content.
     * One outbox event per asset released (matching [MediaAssignmentService]'s own
     * `media.assignment.published` shape) and one audit record per asset, not one for
     * the whole batch — these are photos/videos of minors leaving household-private
     * scope, worth precise per-item traceability over a single summary entry.
     */
    @Transactional
    fun releasePublicly(
        organizationId: UUID,
        householdId: UUID,
        assignmentIds: List<UUID>,
        currentUser: CurrentUser,
    ): List<MediaAssignment> {
        requireHouseholdMediaAccess(organizationId, householdId, currentUser)
        return assignmentIds.map { assignmentId ->
            val assignment = requireHouseholdMediaAssignment(organizationId, householdId, assignmentId)
            mediaAssignmentRepository.publishPublicly(assignment.id, organizationId)
            auditService.record(
                currentUser.userId,
                organizationId,
                "household_media.released_publicly",
                "media_assignment",
                assignment.id,
                householdId = householdId,
            )
            outboxWriter.write(
                aggregateType = "media_assignment",
                aggregateId = assignment.id,
                organizationId = organizationId,
                eventType = "media.assignment.published",
                payloadJson = """{"assignmentId":"${assignment.id}","usageSlot":"${MediaUsageSlot.HOUSEHOLD_MEDIA.name}"}""",
            )
            mediaAssignmentRepository.findById(assignment.id, organizationId)!!
        }
    }

    private fun requireHouseholdMediaAccess(
        organizationId: UUID,
        householdId: UUID,
        currentUser: CurrentUser,
    ) {
        if (membershipService.hasManagerRole(organizationId, currentUser)) return
        if (authorizationService.hasGuardianRelationship(organizationId, householdId, currentUser)) return
        throw ForbiddenException("HOUSEHOLD_MEDIA_ACCESS_DENIED", "You do not have access to this household's media.")
    }

    private fun requireHouseholdMediaAssignment(
        organizationId: UUID,
        householdId: UUID,
        assignmentId: UUID,
    ): MediaAssignment {
        val assignment =
            mediaAssignmentRepository
                .findById(assignmentId, organizationId)
                ?.takeIf {
                    it.usageSlot == MediaUsageSlot.HOUSEHOLD_MEDIA &&
                        it.entityType == MediaEntityType.HOUSEHOLD &&
                        it.entityId == householdId &&
                        it.publicationStatus != PublicationStatus.RETIRED
                }
        return assignment ?: throw NotFoundException("HOUSEHOLD_MEDIA_NOT_FOUND", "The media item could not be found.")
    }
}
