package com.rally26.support.application

import com.rally26.audit.application.AuditService
import com.rally26.authorization.application.AuthorizationService
import com.rally26.authorization.domain.Capabilities
import com.rally26.common.error.ForbiddenException
import com.rally26.common.error.NotFoundException
import com.rally26.common.error.ValidationException
import com.rally26.common.web.CurrentUser
import com.rally26.media.domain.MediaAsset
import com.rally26.media.domain.MediaAssetStatus
import com.rally26.media.domain.MediaAssignment
import com.rally26.media.domain.MediaEntityType
import com.rally26.media.domain.MediaUsageSlot
import com.rally26.media.domain.PublicationStatus
import com.rally26.media.domain.Visibility
import com.rally26.media.persistence.MediaAssetRepository
import com.rally26.media.persistence.MediaAssignmentRepository
import com.rally26.support.domain.PlatformOrganization
import com.rally26.support.domain.SupportArticle
import com.rally26.support.domain.SupportArticleStatus
import com.rally26.support.domain.SupportAudience
import com.rally26.support.persistence.SupportArticleRepository
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SupportArticleAttachmentServiceTest {
    private val mediaAssetRepository = mockk<MediaAssetRepository>()
    private val mediaAssignmentRepository = mockk<MediaAssignmentRepository>()
    private val supportArticleRepository = mockk<SupportArticleRepository>()
    private val authorizationService = mockk<AuthorizationService>()
    private val auditService = mockk<AuditService>(relaxed = true)

    private val service =
        SupportArticleAttachmentService(
            mediaAssetRepository,
            mediaAssignmentRepository,
            supportArticleRepository,
            authorizationService,
            auditService,
        )

    private val articleId = UUID.randomUUID()
    private val admin = CurrentUser(UUID.randomUUID(), "admin@rally26.com", "Platform Admin")

    private fun article() =
        SupportArticle(
            id = articleId,
            slug = "getting-started",
            title = "Getting started",
            summary = "A practical introduction to the Rally26 workspace.",
            bodyMarkdown = "## Start here",
            category = "Getting Started",
            audience = SupportAudience.PUBLIC,
            status = SupportArticleStatus.DRAFT,
            sortOrder = 10,
            publishedAt = null,
            createdBy = null,
            updatedBy = null,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )

    private fun asset(
        id: UUID = UUID.randomUUID(),
        status: MediaAssetStatus = MediaAssetStatus.READY,
        slot: MediaUsageSlot = MediaUsageSlot.ARTICLE_ATTACHMENT,
    ) = MediaAsset(
        id,
        PlatformOrganization.ID,
        admin.userId,
        slot,
        "diagram.png",
        "image/png",
        "image/png",
        "key/$id",
        1024L,
        "checksum",
        400,
        300,
        status,
        null,
        Instant.now(),
        Instant.now(),
    )

    private fun assignment(
        id: UUID = UUID.randomUUID(),
        assetId: UUID = UUID.randomUUID(),
        entityId: UUID = articleId,
        status: PublicationStatus = PublicationStatus.APPROVED,
    ) = MediaAssignment(
        id,
        PlatformOrganization.ID,
        assetId,
        MediaEntityType.SUPPORT_ARTICLE,
        entityId,
        MediaUsageSlot.ARTICLE_ATTACHMENT,
        status,
        Visibility.PUBLIC,
        null,
        Instant.now(),
        Instant.now(),
    )

    private fun allowManage() {
        every { supportArticleRepository.findById(articleId) } returns article()
        every { authorizationService.requirePlatformCapability(admin, Capabilities.PLATFORM_HELP_MANAGE) } just runs
    }

    @Test
    fun `list requires the article to exist and the caller to have platform help capability`() {
        every { supportArticleRepository.findById(articleId) } returns null

        assertFailsWith<NotFoundException> {
            service.list(admin, articleId)
        }
    }

    @Test
    fun `list returns only ARTICLE_ATTACHMENT assignments for the article`() {
        allowManage()
        val attachment = assignment()
        every { mediaAssignmentRepository.listActive(MediaEntityType.SUPPORT_ARTICLE, articleId) } returns listOf(attachment)

        val result = service.list(admin, articleId)

        assertEquals(listOf(attachment), result)
    }

    @Test
    fun `add inserts an assignment for a READY ARTICLE_ATTACHMENT asset`() {
        allowManage()
        val readyAsset = asset()
        every { mediaAssetRepository.findById(readyAsset.id, PlatformOrganization.ID) } returns readyAsset
        every {
            mediaAssignmentRepository.insert(
                organizationId = PlatformOrganization.ID,
                assetId = readyAsset.id,
                entityType = MediaEntityType.SUPPORT_ARTICLE,
                entityId = articleId,
                usageSlot = MediaUsageSlot.ARTICLE_ATTACHMENT,
                publicationStatus = PublicationStatus.APPROVED,
                visibility = Visibility.PUBLIC,
                altText = null,
            )
        } returns assignment(assetId = readyAsset.id)

        val result = service.add(admin, articleId, readyAsset.id)

        assertEquals(readyAsset.id, result.assetId)
        verify(exactly = 1) { auditService.record(admin.userId, null, "support_article_attachment.added", "media_assignment", result.id) }
    }

    @Test
    fun `add rejects an asset that is not READY`() {
        allowManage()
        val pending = asset(status = MediaAssetStatus.PENDING_UPLOAD)
        every { mediaAssetRepository.findById(pending.id, PlatformOrganization.ID) } returns pending

        assertFailsWith<ValidationException> {
            service.add(admin, articleId, pending.id)
        }
    }

    @Test
    fun `add rejects an asset uploaded for a different slot`() {
        allowManage()
        val householdAsset = asset(slot = MediaUsageSlot.HOUSEHOLD_MEDIA)
        every { mediaAssetRepository.findById(householdAsset.id, PlatformOrganization.ID) } returns householdAsset

        assertFailsWith<ValidationException> {
            service.add(admin, articleId, householdAsset.id)
        }
    }

    @Test
    fun `remove retires the attachment`() {
        allowManage()
        val attachment = assignment()
        every { mediaAssignmentRepository.findById(attachment.id, PlatformOrganization.ID) } returns attachment
        every { mediaAssignmentRepository.retire(attachment.id, PlatformOrganization.ID) } returns 1

        service.remove(admin, articleId, attachment.id)

        verify(exactly = 1) { mediaAssignmentRepository.retire(attachment.id, PlatformOrganization.ID) }
    }

    @Test
    fun `remove throws NotFoundException for an attachment belonging to a different article`() {
        allowManage()
        val attachment = assignment(entityId = UUID.randomUUID())
        every { mediaAssignmentRepository.findById(attachment.id, PlatformOrganization.ID) } returns attachment

        assertFailsWith<NotFoundException> {
            service.remove(admin, articleId, attachment.id)
        }
    }

    @Test
    fun `a caller without platform help capability is denied`() {
        every { supportArticleRepository.findById(articleId) } returns article()
        every {
            authorizationService.requirePlatformCapability(admin, Capabilities.PLATFORM_HELP_MANAGE)
        } throws ForbiddenException("PLATFORM_CAPABILITY_DENIED", "You do not have this platform capability.")

        assertFailsWith<ForbiddenException> {
            service.list(admin, articleId)
        }
    }
}
