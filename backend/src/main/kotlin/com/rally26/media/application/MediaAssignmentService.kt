package com.rally26.media.application

import com.rally26.audit.application.AuditService
import com.rally26.common.error.ForbiddenException
import com.rally26.common.error.NotFoundException
import com.rally26.common.error.ValidationException
import com.rally26.common.web.CurrentUser
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
import com.rally26.publicpage.domain.PageStatus
import com.rally26.publicpage.domain.PageType
import com.rally26.publicpage.persistence.PublicPageRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Assigns READY media assets to polymorphic business entities. Existing product,
 * sponsor, document, and organization workflows retain their manager-only entry
 * points. Phase 16 branding uses [MediaEntityAccessService] so team/tournament roles,
 * guardians, and controlled athlete-self accounts are authorized at the exact target
 * rather than promoted to organization-wide manager access.
 */
@Service
class MediaAssignmentService(
    private val mediaAssignmentRepository: MediaAssignmentRepository,
    private val mediaAssetRepository: MediaAssetRepository,
    private val publicPageRepository: PublicPageRepository,
    private val membershipService: MembershipService,
    private val mediaEntityAccessService: MediaEntityAccessService,
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
        return assignInternal(
            organizationId,
            entityType,
            entityId,
            usageSlot,
            assetId,
            altText,
            currentUser,
            visibilityOf,
        )
    }

    @Transactional
    fun assignEntityMedia(
        organizationId: UUID,
        entityType: MediaEntityType,
        entityId: UUID,
        usageSlot: MediaUsageSlot,
        assetId: UUID,
        altText: String?,
        currentUser: CurrentUser,
    ): MediaAssignment {
        val target = mediaEntityAccessService.resolveForManage(organizationId, entityType, entityId, currentUser)
        mediaEntityAccessService.requireAllowedSlot(target, usageSlot)
        return assignInternal(
            organizationId,
            entityType,
            entityId,
            usageSlot,
            assetId,
            altText,
            currentUser,
        ) { target.visibility }
    }

    private fun assignInternal(
        organizationId: UUID,
        entityType: MediaEntityType,
        entityId: UUID,
        usageSlot: MediaUsageSlot,
        assetId: UUID,
        altText: String?,
        currentUser: CurrentUser,
        visibilityOf: () -> Visibility,
    ): MediaAssignment {
        val asset =
            mediaAssetRepository.findById(assetId, organizationId)
                ?: throw NotFoundException("MEDIA_ASSET_NOT_FOUND", "The media asset could not be found.")
        if (asset.status != MediaAssetStatus.READY) {
            throw ValidationException("Only a successfully uploaded asset can be assigned.")
        }
        if (asset.intendedUsageSlot != usageSlot) {
            throw ValidationException("The uploaded asset was requested for ${asset.intendedUsageSlot.name}, not ${usageSlot.name}.")
        }
        if (asset.uploadedByUserId != currentUser.userId && !membershipService.hasManagerRole(organizationId, currentUser)) {
            throw ForbiddenException("MEDIA_ASSET_ACCESS_DENIED", "You cannot assign this media asset.")
        }

        val visibility = visibilityOf()
        val publicationStatus = if (visibility == Visibility.PUBLIC) PublicationStatus.PUBLISHED else PublicationStatus.PRIVATE

        val previous = mediaAssignmentRepository.findActiveBySlot(entityType, entityId, usageSlot)
        if (previous != null) {
            mediaAssignmentRepository.retire(previous.id, organizationId)
            mediaAssetRepository.archive(previous.assetId, organizationId)
        }

        val assignment =
            mediaAssignmentRepository.insert(
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
    fun remove(
        organizationId: UUID,
        entityType: MediaEntityType,
        entityId: UUID,
        usageSlot: MediaUsageSlot,
        currentUser: CurrentUser,
    ) {
        membershipService.requireManagerRole(organizationId, currentUser)
        removeInternal(organizationId, entityType, entityId, usageSlot, currentUser)
    }

    @Transactional
    fun removeEntityMedia(
        organizationId: UUID,
        entityType: MediaEntityType,
        entityId: UUID,
        usageSlot: MediaUsageSlot,
        currentUser: CurrentUser,
    ) {
        val target = mediaEntityAccessService.resolveForManage(organizationId, entityType, entityId, currentUser)
        mediaEntityAccessService.requireAllowedSlot(target, usageSlot)
        removeInternal(organizationId, entityType, entityId, usageSlot, currentUser)
    }

    private fun removeInternal(
        organizationId: UUID,
        entityType: MediaEntityType,
        entityId: UUID,
        usageSlot: MediaUsageSlot,
        currentUser: CurrentUser,
    ) {
        val active =
            mediaAssignmentRepository.findActiveBySlot(entityType, entityId, usageSlot)
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

    fun listActive(
        organizationId: UUID,
        entityType: MediaEntityType,
        entityId: UUID,
        currentUser: CurrentUser,
    ): List<MediaAssignment> {
        membershipService.requireActiveMembership(organizationId, currentUser)
        return mediaAssignmentRepository.listActive(entityType, entityId)
    }

    fun listEntityMedia(
        organizationId: UUID,
        entityType: MediaEntityType,
        entityId: UUID,
        currentUser: CurrentUser,
    ): List<MediaAssignment> {
        mediaEntityAccessService.resolveForRead(organizationId, entityType, entityId, currentUser)
        return mediaAssignmentRepository.listActive(entityType, entityId)
    }

    /** The only PUBLIC-visibility assignment a fully unauthenticated caller may see. */
    fun getPublicAssignment(
        entityType: MediaEntityType,
        entityId: UUID,
        usageSlot: MediaUsageSlot,
    ): MediaAssignment? =
        mediaAssignmentRepository.findActiveBySlot(entityType, entityId, usageSlot)?.takeIf { it.visibility == Visibility.PUBLIC }

    /** Internal/public-page composition read. Callers must establish their own visibility boundary first. */
    fun getActiveAssignment(
        entityType: MediaEntityType,
        entityId: UUID,
        usageSlot: MediaUsageSlot,
    ): MediaAssignment? = mediaAssignmentRepository.findActiveBySlot(entityType, entityId, usageSlot)

    fun assignOrganizationMedia(
        organizationId: UUID,
        usageSlot: MediaUsageSlot,
        assetId: UUID,
        altText: String?,
        currentUser: CurrentUser,
    ): MediaAssignment =
        assign(organizationId, MediaEntityType.ORGANIZATION, organizationId, usageSlot, assetId, altText, currentUser) {
            computeOrganizationVisibility(organizationId)
        }

    fun removeOrganizationMedia(
        organizationId: UUID,
        usageSlot: MediaUsageSlot,
        currentUser: CurrentUser,
    ) = remove(organizationId, MediaEntityType.ORGANIZATION, organizationId, usageSlot, currentUser)

    fun listActiveOrganizationMedia(
        organizationId: UUID,
        currentUser: CurrentUser,
    ): List<MediaAssignment> = listActive(organizationId, MediaEntityType.ORGANIZATION, organizationId, currentUser)

    private fun computeOrganizationVisibility(organizationId: UUID): Visibility {
        val page = publicPageRepository.findByEntityId(organizationId) ?: return Visibility.ORGANIZATION_PRIVATE
        return if (page.pageType == PageType.ORGANIZATION && page.status == PageStatus.PUBLISHED) {
            Visibility.PUBLIC
        } else {
            Visibility.ORGANIZATION_PRIVATE
        }
    }
}
