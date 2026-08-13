package com.rally26.support.application

import com.rally26.audit.application.AuditService
import com.rally26.authorization.application.AuthorizationService
import com.rally26.authorization.domain.Capabilities
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
import com.rally26.support.domain.PlatformOrganization
import com.rally26.support.persistence.SupportArticleRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Help Center article attachments (Track 4, 2026-08-13) — images/GIFs/video/PDFs a
 * platform admin attaches to an article, then references from the body via
 * `attachment:<id>` markdown embeds ([SupportArticleService.resolveAttachments]
 * rewrites those into real signed URLs only on the reader-facing paths, never on the
 * platform CRUD paths, so the stored markdown always keeps the durable placeholder).
 * Same "gallery, not one replaceable slot" shape as [com.rally26.document.application.DocumentService]
 * and [com.rally26.media.application.HouseholdMediaService] — bypasses
 * [com.rally26.media.application.MediaAssignmentService]'s single-active-slot
 * orchestration for the same reason those two do.
 */
@Service
class SupportArticleAttachmentService(
    private val mediaAssetRepository: MediaAssetRepository,
    private val mediaAssignmentRepository: MediaAssignmentRepository,
    private val supportArticleRepository: SupportArticleRepository,
    private val authorizationService: AuthorizationService,
    private val auditService: AuditService,
) {
    fun list(
        currentUser: CurrentUser,
        articleId: UUID,
    ): List<MediaAssignment> {
        requireManage(currentUser, articleId)
        return mediaAssignmentRepository
            .listActive(MediaEntityType.SUPPORT_ARTICLE, articleId)
            .filter { it.usageSlot == MediaUsageSlot.ARTICLE_ATTACHMENT }
    }

    @Transactional
    fun add(
        currentUser: CurrentUser,
        articleId: UUID,
        assetId: UUID,
    ): MediaAssignment {
        requireManage(currentUser, articleId)
        val asset =
            mediaAssetRepository.findById(assetId, PlatformOrganization.ID)
                ?: throw NotFoundException("MEDIA_ASSET_NOT_FOUND", "The media asset could not be found.")
        if (asset.status != MediaAssetStatus.READY) {
            throw ValidationException("Only a successfully uploaded asset can be attached to an article.")
        }
        if (asset.intendedUsageSlot != MediaUsageSlot.ARTICLE_ATTACHMENT) {
            throw ValidationException("This asset was not uploaded as an article attachment.")
        }
        val assignment =
            mediaAssignmentRepository.insert(
                organizationId = PlatformOrganization.ID,
                assetId = asset.id,
                entityType = MediaEntityType.SUPPORT_ARTICLE,
                entityId = articleId,
                usageSlot = MediaUsageSlot.ARTICLE_ATTACHMENT,
                publicationStatus = PublicationStatus.APPROVED,
                visibility = Visibility.PUBLIC,
                altText = null,
            )
        auditService.record(currentUser.userId, null, "support_article_attachment.added", "media_assignment", assignment.id)
        return assignment
    }

    @Transactional
    fun remove(
        currentUser: CurrentUser,
        articleId: UUID,
        assignmentId: UUID,
    ) {
        requireManage(currentUser, articleId)
        val assignment = requireAttachment(articleId, assignmentId)
        mediaAssignmentRepository.retire(assignment.id, PlatformOrganization.ID)
        auditService.record(currentUser.userId, null, "support_article_attachment.removed", "media_assignment", assignment.id)
    }

    private fun requireManage(
        currentUser: CurrentUser,
        articleId: UUID,
    ) {
        supportArticleRepository.findById(articleId)
            ?: throw NotFoundException("SUPPORT_ARTICLE_NOT_FOUND", "The help article could not be found.")
        authorizationService.requirePlatformCapability(currentUser, Capabilities.PLATFORM_HELP_MANAGE)
    }

    private fun requireAttachment(
        articleId: UUID,
        assignmentId: UUID,
    ): MediaAssignment {
        val assignment =
            mediaAssignmentRepository
                .findById(assignmentId, PlatformOrganization.ID)
                ?.takeIf {
                    it.usageSlot == MediaUsageSlot.ARTICLE_ATTACHMENT &&
                        it.entityType == MediaEntityType.SUPPORT_ARTICLE &&
                        it.entityId == articleId &&
                        it.publicationStatus != PublicationStatus.RETIRED
                }
        return assignment ?: throw NotFoundException("ARTICLE_ATTACHMENT_NOT_FOUND", "The attachment could not be found.")
    }
}
