package com.rally26.support.application

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.rally26.audit.application.AuditService
import com.rally26.authorization.application.AuthorizationService
import com.rally26.authorization.domain.AuthorizationContext
import com.rally26.authorization.domain.ContextType
import com.rally26.common.web.CurrentUser
import com.rally26.media.application.MediaDescriptor
import com.rally26.media.application.MediaReadService
import com.rally26.media.domain.MediaAssignment
import com.rally26.media.domain.MediaEntityType
import com.rally26.media.domain.MediaUsageSlot
import com.rally26.media.domain.PublicationStatus
import com.rally26.media.domain.Visibility
import com.rally26.media.persistence.MediaAssignmentRepository
import com.rally26.support.domain.PlatformOrganization
import com.rally26.support.domain.SupportArticle
import com.rally26.support.domain.SupportArticleStatus
import com.rally26.support.domain.SupportAudience
import com.rally26.support.persistence.SupportArticleRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SupportArticleServiceTest {
    private val repository = mockk<SupportArticleRepository>()
    private val authorizationService = mockk<AuthorizationService>()
    private val auditService = mockk<AuditService>()
    private val mediaAssignmentRepository = mockk<MediaAssignmentRepository>()
    private val mediaReadService = mockk<MediaReadService>()
    private val service =
        SupportArticleService(
            repository,
            authorizationService,
            auditService,
            jacksonObjectMapper(),
            Clock.fixed(Instant.parse("2026-08-01T13:30:00Z"), ZoneOffset.UTC),
            mediaAssignmentRepository,
            mediaReadService,
        )
    private val user = CurrentUser(UUID.randomUUID(), "coach@example.com", "Coach User")

    @Test
    fun `public catalog can only request public articles`() {
        every { repository.listPublished(setOf(SupportAudience.PUBLIC), null, null) } returns listOf(article(SupportAudience.PUBLIC))

        val result = service.listPublic(null, null)

        assertEquals(1, result.size)
        verify(exactly = 1) { repository.listPublished(setOf(SupportAudience.PUBLIC), null, null) }
    }

    @Test
    fun `authenticated catalog combines public and role audiences`() {
        every { authorizationService.listContexts(user) } returns
            listOf(
                AuthorizationContext(
                    contextType = ContextType.TEAM,
                    resourceId = UUID.randomUUID(),
                    organizationId = UUID.randomUUID(),
                    label = "U14 Blue",
                    role = "TEAM_MANAGER",
                    capabilities = emptySet(),
                ),
                AuthorizationContext(
                    contextType = ContextType.HOUSEHOLD,
                    resourceId = UUID.randomUUID(),
                    organizationId = UUID.randomUUID(),
                    label = "Morgan Household",
                    role = "GUARDIAN",
                    capabilities = emptySet(),
                ),
            )
        val expected = setOf(SupportAudience.PUBLIC, SupportAudience.COACH, SupportAudience.GUARDIAN)
        every { repository.listPublished(expected, null, null) } returns listOf(article(SupportAudience.COACH))

        val result = service.listAuthenticated(user, null, null)

        assertEquals(SupportAudience.COACH, result.single().audience)
        verify(exactly = 1) { repository.listPublished(expected, null, null) }
    }

    @Test
    fun `getPublic resolves an attachment placeholder into a signed URL`() {
        val attachmentId = UUID.randomUUID()
        val withAttachment = article(SupportAudience.PUBLIC).copy(bodyMarkdown = "See the diagram: ![Setup diagram](attachment:$attachmentId)")
        every { repository.findPublishedBySlug("getting-started", setOf(SupportAudience.PUBLIC)) } returns withAttachment
        val assignment =
            MediaAssignment(
                id = attachmentId,
                organizationId = PlatformOrganization.ID,
                assetId = UUID.randomUUID(),
                entityType = MediaEntityType.SUPPORT_ARTICLE,
                entityId = withAttachment.id,
                usageSlot = MediaUsageSlot.ARTICLE_ATTACHMENT,
                publicationStatus = PublicationStatus.APPROVED,
                visibility = Visibility.PUBLIC,
                altText = null,
                createdAt = Instant.parse("2026-08-13T00:00:00Z"),
                updatedAt = Instant.parse("2026-08-13T00:00:00Z"),
            )
        every { mediaAssignmentRepository.findById(attachmentId, PlatformOrganization.ID) } returns assignment
        every { mediaReadService.describe(assignment) } returns
            MediaDescriptor(assignment, "https://signed.example.com/diagram.png", "image/png", 1024, 400, 300)

        val result = service.getPublic("getting-started")

        assertEquals("See the diagram: ![Setup diagram](https://signed.example.com/diagram.png)", result.bodyMarkdown)
    }

    @Test
    fun `getPublic leaves an unresolvable attachment placeholder untouched`() {
        val attachmentId = UUID.randomUUID()
        val withAttachment = article(SupportAudience.PUBLIC).copy(bodyMarkdown = "![Gone](attachment:$attachmentId)")
        every { repository.findPublishedBySlug("getting-started", setOf(SupportAudience.PUBLIC)) } returns withAttachment
        every { mediaAssignmentRepository.findById(attachmentId, PlatformOrganization.ID) } returns null

        val result = service.getPublic("getting-started")

        assertTrue(result.bodyMarkdown.contains("attachment:$attachmentId"))
    }

    private fun article(audience: SupportAudience) =
        SupportArticle(
            id = UUID.randomUUID(),
            slug = "getting-started",
            title = "Getting started",
            summary = "A practical introduction to the Rally26 workspace.",
            bodyMarkdown = "## Start here\n\nUse the organization workspace to begin setup.",
            category = "Getting Started",
            audience = audience,
            status = SupportArticleStatus.PUBLISHED,
            sortOrder = 10,
            publishedAt = Instant.parse("2026-08-01T13:30:00Z"),
            createdBy = null,
            updatedBy = null,
            createdAt = Instant.parse("2026-08-01T13:30:00Z"),
            updatedAt = Instant.parse("2026-08-01T13:30:00Z"),
        )
}
