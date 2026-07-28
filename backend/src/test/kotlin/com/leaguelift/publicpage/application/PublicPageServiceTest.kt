package com.leaguelift.publicpage.application

import com.leaguelift.audit.application.AuditService
import com.leaguelift.common.error.ConflictException
import com.leaguelift.common.error.NotFoundException
import com.leaguelift.common.error.ValidationException
import com.leaguelift.common.web.CurrentUser
import com.leaguelift.membership.application.MembershipService
import com.leaguelift.membership.domain.MembershipRole
import com.leaguelift.membership.domain.MembershipStatus
import com.leaguelift.membership.domain.OrganizationMembership
import com.leaguelift.publicpage.domain.PageStatus
import com.leaguelift.publicpage.domain.PageType
import com.leaguelift.publicpage.domain.PublicPage
import com.leaguelift.publicpage.persistence.PublicPageRepository
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
import kotlin.test.assertNull

class PublicPageServiceTest {

    private val publicPageRepository = mockk<PublicPageRepository>()
    private val membershipService = mockk<MembershipService>()
    private val auditService = mockk<AuditService>()
    private val service = PublicPageService(publicPageRepository, membershipService, auditService)

    private val orgId = UUID.randomUUID()
    private val currentUser = CurrentUser(UUID.randomUUID(), "manager@example.com", "Manager")

    @Test
    fun `create rejects an invalid slug`() {
        every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()

        assertFailsWith<ValidationException> {
            service.create(orgId, PageType.ORGANIZATION, orgId, "Invalid Slug!", "My Org", null, currentUser)
        }
    }

    @Test
    fun `create rejects when a page already exists for the entity`() {
        every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
        every { publicPageRepository.findByEntityId(orgId) } returns samplePage()

        assertFailsWith<ConflictException> {
            service.create(orgId, PageType.ORGANIZATION, orgId, "my-org", "My Org", null, currentUser)
        }
    }

    @Test
    fun `create succeeds and records audit`() {
        val page = samplePage()
        every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
        every { publicPageRepository.findByEntityId(orgId) } returns null
        every { publicPageRepository.insert(orgId, PageType.ORGANIZATION, orgId, "my-org", "My Org", null) } returns page
        every { auditService.record(any(), any(), any(), any(), any(), any()) } just runs

        val result = service.create(orgId, PageType.ORGANIZATION, orgId, "my-org", "My Org", null, currentUser)

        assertEquals(page.id, result.id)
        verify(exactly = 1) { auditService.record(currentUser.userId, orgId, "page.created", "public_page", page.id, any()) }
    }

    @Test
    fun `getPublic returns only published pages`() {
        val published = samplePage().copy(status = PageStatus.PUBLISHED, publishedAt = Instant.now())
        every { publicPageRepository.findBySlug("my-org") } returns published

        val result = service.getPublic("my-org")
        assertEquals(PageStatus.PUBLISHED, result.status)
    }

    @Test
    fun `getPublic throws NotFoundException for draft pages`() {
        every { publicPageRepository.findBySlug("my-org") } returns samplePage()

        assertFailsWith<NotFoundException> {
            service.getPublic("my-org")
        }
    }

    @Test
    fun `publish transitions DRAFT to PUBLISHED and records audit`() {
        val page = samplePage()
        val published = page.copy(status = PageStatus.PUBLISHED, publishedAt = Instant.now())
        every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
        every { publicPageRepository.findById(page.id, orgId) } returnsMany listOf(page, published)
        every { publicPageRepository.updateStatus(page.id, orgId, PageStatus.PUBLISHED, any()) } returns 1
        every { auditService.record(any(), any(), any(), any(), any(), any()) } just runs

        val result = service.publish(orgId, page.id, currentUser)

        assertEquals(PageStatus.PUBLISHED, result.status)
        verify(exactly = 1) { auditService.record(currentUser.userId, orgId, "page.published", "public_page", page.id, any()) }
    }

    @Test
    fun `publish is a no-op for already-published pages`() {
        val published = samplePage().copy(status = PageStatus.PUBLISHED, publishedAt = Instant.now())
        every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
        every { publicPageRepository.findById(published.id, orgId) } returns published

        val result = service.publish(orgId, published.id, currentUser)
        assertEquals(PageStatus.PUBLISHED, result.status)
    }

    @Test
    fun `publish throws ValidationException for archived pages`() {
        val archived = samplePage().copy(status = PageStatus.ARCHIVED)
        every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
        every { publicPageRepository.findById(archived.id, orgId) } returns archived

        assertFailsWith<ValidationException> {
            service.publish(orgId, archived.id, currentUser)
        }
    }

    @Test
    fun `unpublish transitions PUBLISHED to DRAFT and records audit`() {
        val published = samplePage().copy(status = PageStatus.PUBLISHED, publishedAt = Instant.now())
        val draft = samplePage().copy(status = PageStatus.DRAFT)
        every { membershipService.requireManagerRole(orgId, currentUser) } returns managerMembership()
        every { publicPageRepository.findById(published.id, orgId) } returnsMany listOf(published, draft)
        every { publicPageRepository.updateStatus(published.id, orgId, PageStatus.DRAFT, null) } returns 1
        every { auditService.record(any(), any(), any(), any(), any(), any()) } just runs

        val result = service.unpublish(orgId, published.id, currentUser)

        assertEquals(PageStatus.DRAFT, result.status)
        assertNull(result.publishedAt)
        verify(exactly = 1) { auditService.record(currentUser.userId, orgId, "page.unpublished", "public_page", published.id, any()) }
    }

    @Test
    fun `get throws NotFoundException when page does not belong to org`() {
        every { membershipService.requireActiveMembership(orgId, currentUser) } returns managerMembership()
        every { publicPageRepository.findById(any(), orgId) } returns null

        assertFailsWith<NotFoundException> {
            service.get(orgId, UUID.randomUUID(), currentUser)
        }
    }

    private fun samplePage() = PublicPage(
        id = UUID.randomUUID(),
        organizationId = orgId,
        pageType = PageType.ORGANIZATION,
        entityId = orgId,
        slug = "my-org",
        title = "My Organization",
        summary = null,
        status = PageStatus.DRAFT,
        publishedAt = null,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
    )

    private fun managerMembership() = OrganizationMembership(
        id = UUID.randomUUID(),
        organizationId = orgId,
        userId = currentUser.userId,
        role = MembershipRole.ADMINISTRATOR,
        status = MembershipStatus.ACTIVE,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
    )
}
