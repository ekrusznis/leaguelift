package com.rally26.support.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.rally26.audit.application.AuditService
import com.rally26.authorization.application.AuthorizationService
import com.rally26.authorization.domain.Capabilities
import com.rally26.authorization.domain.ContextType
import com.rally26.common.error.ConflictException
import com.rally26.common.error.NotFoundException
import com.rally26.common.error.ValidationException
import com.rally26.common.web.CurrentUser
import com.rally26.common.web.PageRequest
import com.rally26.common.web.PageResponse
import com.rally26.media.application.MediaReadService
import com.rally26.media.domain.MediaEntityType
import com.rally26.media.persistence.MediaAssignmentRepository
import com.rally26.support.domain.PlatformOrganization
import com.rally26.support.domain.SupportArticle
import com.rally26.support.domain.SupportArticleStatus
import com.rally26.support.domain.SupportAudience
import com.rally26.support.persistence.SupportArticleRepository
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.util.UUID

private val ATTACHMENT_TOKEN = Regex("attachment:([0-9a-fA-F-]{36})")

@Service
class SupportArticleService(
    private val repository: SupportArticleRepository,
    private val authorizationService: AuthorizationService,
    private val auditService: AuditService,
    private val objectMapper: ObjectMapper,
    private val clock: Clock,
    private val mediaAssignmentRepository: MediaAssignmentRepository,
    private val mediaReadService: MediaReadService,
) {
    fun listPublic(
        query: String?,
        category: String?,
    ) = repository
        .listPublished(
            setOf(SupportAudience.PUBLIC),
            normalizeSearch(query),
            normalizeCategoryFilter(category),
        ).map(::resolveAttachments)

    fun getPublic(slug: String): SupportArticle =
        resolveAttachments(
            repository.findPublishedBySlug(slug.trim(), setOf(SupportAudience.PUBLIC))
                ?: throw NotFoundException("SUPPORT_ARTICLE_NOT_FOUND", "The help article could not be found."),
        )

    fun listAuthenticated(
        currentUser: CurrentUser,
        query: String?,
        category: String?,
    ) = repository
        .listPublished(
            audiencesFor(currentUser),
            normalizeSearch(query),
            normalizeCategoryFilter(category),
        ).map(::resolveAttachments)

    fun getAuthenticated(
        currentUser: CurrentUser,
        slug: String,
    ): SupportArticle =
        resolveAttachments(
            repository.findPublishedBySlug(slug.trim(), audiencesFor(currentUser))
                ?: throw NotFoundException("SUPPORT_ARTICLE_NOT_FOUND", "The help article could not be found."),
        )

    /**
     * The stored `bodyMarkdown` keeps durable `attachment:<assignmentId>` placeholders
     * (see [com.rally26.support.application.SupportArticleAttachmentService]'s doc
     * comment) so re-saving an article from the platform editor never bakes in an
     * ephemeral signed URL. Only reader-facing responses (public/authenticated get and
     * list) resolve those placeholders into a fresh 15-minute signed URL — the platform
     * CRUD paths ([listPlatform], [create], [update]) intentionally return the raw
     * placeholder-bearing markdown so the editor round-trips it correctly.
     */
    private fun resolveAttachments(article: SupportArticle): SupportArticle {
        if (!article.bodyMarkdown.contains("attachment:")) return article
        val resolvedBody =
            ATTACHMENT_TOKEN.replace(article.bodyMarkdown) { match ->
                val assignmentId = runCatching { UUID.fromString(match.groupValues[1]) }.getOrNull() ?: return@replace match.value
                val assignment = mediaAssignmentRepository.findById(assignmentId, PlatformOrganization.ID) ?: return@replace match.value
                if (assignment.entityType != MediaEntityType.SUPPORT_ARTICLE ||
                    assignment.entityId != article.id
                ) {
                    return@replace match.value
                }
                mediaReadService.describe(assignment)?.url ?: match.value
            }
        return article.copy(bodyMarkdown = resolvedBody)
    }

    fun listPlatform(
        currentUser: CurrentUser,
        query: String?,
        status: SupportArticleStatus?,
        audience: SupportAudience?,
        category: String?,
        page: PageRequest,
    ): PageResponse<SupportArticle> {
        authorizationService.requirePlatformCapability(currentUser, Capabilities.PLATFORM_HELP_MANAGE)
        return PageResponse(
            repository.listAll(normalizeSearch(query), status, audience, normalizeCategoryFilter(category), page),
            page.page,
            page.size,
            repository.countAll(normalizeSearch(query), status, audience, normalizeCategoryFilter(category)),
        )
    }

    @Transactional
    fun create(
        currentUser: CurrentUser,
        slug: String,
        title: String,
        summary: String,
        bodyMarkdown: String,
        category: String,
        audience: SupportAudience,
        sortOrder: Int,
    ): SupportArticle {
        authorizationService.requirePlatformCapability(currentUser, Capabilities.PLATFORM_HELP_MANAGE)
        val input = normalize(slug, title, summary, bodyMarkdown, category, sortOrder)
        val article =
            try {
                repository.insert(
                    input.slug,
                    input.title,
                    input.summary,
                    input.body,
                    input.category,
                    audience,
                    input.sortOrder,
                    currentUser.userId,
                )
            } catch (_: DuplicateKeyException) {
                throw ConflictException("SUPPORT_ARTICLE_SLUG_CONFLICT", "A help article already uses this slug.")
            }
        audit(currentUser, article, "support_article.created")
        return article
    }

    @Transactional
    fun update(
        currentUser: CurrentUser,
        articleId: UUID,
        slug: String,
        title: String,
        summary: String,
        bodyMarkdown: String,
        category: String,
        audience: SupportAudience,
        sortOrder: Int,
    ): SupportArticle {
        authorizationService.requirePlatformCapability(currentUser, Capabilities.PLATFORM_HELP_MANAGE)
        val existing = requireArticle(articleId)
        if (existing.status ==
            SupportArticleStatus.ARCHIVED
        ) {
            throw ConflictException("SUPPORT_ARTICLE_ARCHIVED", "Archived help articles cannot be edited.")
        }
        val input = normalize(slug, title, summary, bodyMarkdown, category, sortOrder)
        try {
            if (repository.update(
                    articleId,
                    input.slug,
                    input.title,
                    input.summary,
                    input.body,
                    input.category,
                    audience,
                    input.sortOrder,
                    currentUser.userId,
                ) ==
                0
            ) {
                throw ConflictException("SUPPORT_ARTICLE_ARCHIVED", "The help article is no longer editable.")
            }
        } catch (_: DuplicateKeyException) {
            throw ConflictException("SUPPORT_ARTICLE_SLUG_CONFLICT", "A help article already uses this slug.")
        }
        val updated = requireArticle(articleId)
        audit(currentUser, updated, "support_article.updated")
        return updated
    }

    @Transactional
    fun publish(
        currentUser: CurrentUser,
        articleId: UUID,
    ): SupportArticle {
        authorizationService.requirePlatformCapability(currentUser, Capabilities.PLATFORM_HELP_MANAGE)
        val existing = requireArticle(articleId)
        if (existing.status ==
            SupportArticleStatus.ARCHIVED
        ) {
            throw ConflictException("SUPPORT_ARTICLE_ARCHIVED", "Archived help articles cannot be published.")
        }
        if (existing.status == SupportArticleStatus.PUBLISHED) return existing
        repository.publish(articleId, currentUser.userId, Instant.now(clock))
        val published = requireArticle(articleId)
        audit(currentUser, published, "support_article.published")
        return published
    }

    @Transactional
    fun archive(
        currentUser: CurrentUser,
        articleId: UUID,
    ): SupportArticle {
        authorizationService.requirePlatformCapability(currentUser, Capabilities.PLATFORM_HELP_MANAGE)
        val existing = requireArticle(articleId)
        if (existing.status == SupportArticleStatus.ARCHIVED) return existing
        repository.archive(articleId, currentUser.userId)
        val archived = requireArticle(articleId)
        audit(currentUser, archived, "support_article.archived")
        return archived
    }

    private fun audiencesFor(currentUser: CurrentUser): Set<SupportAudience> {
        val audiences = mutableSetOf(SupportAudience.PUBLIC)
        for (context in authorizationService.listContexts(currentUser)) {
            when (context.contextType) {
                ContextType.PLATFORM_ADMIN -> audiences += SupportAudience.PLATFORM
                ContextType.HOUSEHOLD -> audiences += SupportAudience.GUARDIAN
                ContextType.ATHLETE -> audiences += SupportAudience.ATHLETE
                ContextType.TEAM, ContextType.TOURNAMENT -> audiences += SupportAudience.COACH
                ContextType.ORGANIZATION ->
                    when (context.role) {
                        "OWNER", "ADMINISTRATOR" -> audiences += SupportAudience.OWNER_ADMIN
                        "TEAM_ADMINISTRATOR", "TOURNAMENT_ADMINISTRATOR" -> audiences += SupportAudience.COACH
                    }
                else -> Unit
            }
        }
        return audiences
    }

    private fun requireArticle(id: UUID) =
        repository.findById(id)
            ?: throw NotFoundException("SUPPORT_ARTICLE_NOT_FOUND", "The help article could not be found.")

    private fun normalize(
        slug: String,
        title: String,
        summary: String,
        body: String,
        category: String,
        sortOrder: Int,
    ): Input {
        val normalizedSlug = slug.trim().lowercase()
        if (!normalizedSlug.matches(
                Regex("^[a-z0-9]([a-z0-9-]{0,118}[a-z0-9])?$"),
            )
        ) {
            throw ValidationException("Slug must use lowercase letters, numbers, and hyphens.")
        }
        val normalizedTitle = title.trim()
        if (normalizedTitle.length !in 3..180) throw ValidationException("Title must be between 3 and 180 characters.")
        val normalizedSummary = summary.trim()
        if (normalizedSummary.length !in 10..400) throw ValidationException("Summary must be between 10 and 400 characters.")
        val normalizedBody = body.trim()
        if (normalizedBody.length !in 20..20000) throw ValidationException("Article body must be between 20 and 20,000 characters.")
        val normalizedCategory = category.trim()
        if (normalizedCategory.length !in 3..80) throw ValidationException("Category must be between 3 and 80 characters.")
        if (sortOrder !in 0..10000) throw ValidationException("Sort order must be between 0 and 10,000.")
        return Input(normalizedSlug, normalizedTitle, normalizedSummary, normalizedBody, normalizedCategory, sortOrder)
    }

    private fun normalizeSearch(value: String?) =
        value?.trim()?.takeIf { it.isNotEmpty() }?.also {
            if (it.length > 120) throw ValidationException("Search must not exceed 120 characters.")
        }

    private fun normalizeCategoryFilter(value: String?) =
        value?.trim()?.takeIf { it.isNotEmpty() }?.also {
            if (it.length > 80) throw ValidationException("Category must not exceed 80 characters.")
        }

    private fun audit(
        currentUser: CurrentUser,
        article: SupportArticle,
        action: String,
    ) = auditService.record(
        currentUser.userId,
        null,
        action,
        "SUPPORT_ARTICLE",
        article.id,
        objectMapper.writeValueAsString(
            mapOf("slug" to article.slug, "status" to article.status.name, "audience" to article.audience.name),
        ),
    )

    private data class Input(
        val slug: String,
        val title: String,
        val summary: String,
        val body: String,
        val category: String,
        val sortOrder: Int,
    )
}
