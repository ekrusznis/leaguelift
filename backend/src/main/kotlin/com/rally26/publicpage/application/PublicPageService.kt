package com.rally26.publicpage.application

import com.rally26.audit.application.AuditService
import com.rally26.common.error.ConflictException
import com.rally26.common.error.NotFoundException
import com.rally26.common.error.ValidationException
import com.rally26.common.web.CurrentUser
import com.rally26.membership.application.MembershipService
import com.rally26.publicpage.domain.PageStatus
import com.rally26.publicpage.domain.PageType
import com.rally26.publicpage.domain.PublicPage
import com.rally26.publicpage.domain.isValidPageSlug
import com.rally26.publicpage.persistence.PublicPageRepository
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class PublicPageService(
    private val publicPageRepository: PublicPageRepository,
    private val membershipService: MembershipService,
    private val auditService: AuditService,
) {

    fun list(organizationId: UUID, currentUser: CurrentUser, offset: Int, limit: Int): List<PublicPage> {
        membershipService.requireActiveMembership(organizationId, currentUser)
        return publicPageRepository.findAllForOrg(organizationId, offset, limit)
    }

    fun count(organizationId: UUID, currentUser: CurrentUser): Long {
        membershipService.requireActiveMembership(organizationId, currentUser)
        return publicPageRepository.countForOrg(organizationId)
    }

    fun get(organizationId: UUID, pageId: UUID, currentUser: CurrentUser): PublicPage {
        membershipService.requireActiveMembership(organizationId, currentUser)
        return publicPageRepository.findById(pageId, organizationId)
            ?: throw NotFoundException("PAGE_NOT_FOUND", "The page could not be found.")
    }

    fun getPublic(slug: String): PublicPage {
        val page = publicPageRepository.findBySlug(slug)
            ?: throw NotFoundException("PAGE_NOT_FOUND", "The page could not be found.")
        if (page.status != PageStatus.PUBLISHED) {
            throw NotFoundException("PAGE_NOT_FOUND", "The page could not be found.")
        }
        return page
    }

    fun getForEntity(organizationId: UUID, entityId: UUID, currentUser: CurrentUser): PublicPage? {
        membershipService.requireActiveMembership(organizationId, currentUser)
        return publicPageRepository.findByEntityId(entityId)
    }

    @Transactional
    fun create(
        organizationId: UUID,
        pageType: PageType,
        entityId: UUID,
        slug: String,
        title: String,
        summary: String?,
        currentUser: CurrentUser,
    ): PublicPage {
        membershipService.requireManagerRole(organizationId, currentUser)
        validateSlug(slug)
        publicPageRepository.findByEntityId(entityId)?.let {
            throw ConflictException("PAGE_ALREADY_EXISTS", "A page already exists for this entity.")
        }
        return try {
            val page = publicPageRepository.insert(organizationId, pageType, entityId, slug, title, summary)
            auditService.record(
                actorUserId = currentUser.userId,
                organizationId = organizationId,
                action = "page.created",
                entityType = "public_page",
                entityId = page.id,
            )
            page
        } catch (e: DuplicateKeyException) {
            throw ConflictException("PAGE_SLUG_TAKEN", "This slug is already in use.")
        }
    }

    @Transactional
    fun update(
        organizationId: UUID,
        pageId: UUID,
        title: String?,
        slug: String?,
        summary: String?,
        currentUser: CurrentUser,
    ): PublicPage {
        membershipService.requireManagerRole(organizationId, currentUser)
        publicPageRepository.findById(pageId, organizationId)
            ?: throw NotFoundException("PAGE_NOT_FOUND", "The page could not be found.")
        if (slug != null) validateSlug(slug)
        return try {
            publicPageRepository.update(pageId, organizationId, title, slug, summary)
            auditService.record(
                actorUserId = currentUser.userId,
                organizationId = organizationId,
                action = "page.updated",
                entityType = "public_page",
                entityId = pageId,
            )
            publicPageRepository.findById(pageId, organizationId)!!
        } catch (e: DuplicateKeyException) {
            throw ConflictException("PAGE_SLUG_TAKEN", "This slug is already in use.")
        }
    }

    @Transactional
    fun publish(organizationId: UUID, pageId: UUID, currentUser: CurrentUser): PublicPage {
        membershipService.requireManagerRole(organizationId, currentUser)
        val page = publicPageRepository.findById(pageId, organizationId)
            ?: throw NotFoundException("PAGE_NOT_FOUND", "The page could not be found.")
        if (page.status == PageStatus.PUBLISHED) return page
        if (page.status == PageStatus.ARCHIVED) {
            throw ValidationException("An archived page cannot be published.")
        }
        publicPageRepository.updateStatus(pageId, organizationId, PageStatus.PUBLISHED, Instant.now())
        auditService.record(
            actorUserId = currentUser.userId,
            organizationId = organizationId,
            action = "page.published",
            entityType = "public_page",
            entityId = pageId,
        )
        return publicPageRepository.findById(pageId, organizationId)!!
    }

    @Transactional
    fun unpublish(organizationId: UUID, pageId: UUID, currentUser: CurrentUser): PublicPage {
        membershipService.requireManagerRole(organizationId, currentUser)
        val page = publicPageRepository.findById(pageId, organizationId)
            ?: throw NotFoundException("PAGE_NOT_FOUND", "The page could not be found.")
        if (page.status == PageStatus.DRAFT) return page
        if (page.status == PageStatus.ARCHIVED) {
            throw ValidationException("An archived page cannot be unpublished.")
        }
        publicPageRepository.updateStatus(pageId, organizationId, PageStatus.DRAFT, null)
        auditService.record(
            actorUserId = currentUser.userId,
            organizationId = organizationId,
            action = "page.unpublished",
            entityType = "public_page",
            entityId = pageId,
        )
        return publicPageRepository.findById(pageId, organizationId)!!
    }

    private fun validateSlug(slug: String) {
        if (!isValidPageSlug(slug)) {
            throw ValidationException(
                "Slug must be lowercase alphanumeric with optional hyphens.",
                listOf(com.rally26.common.error.FieldError("slug", "Invalid slug format.")),
            )
        }
    }
}
