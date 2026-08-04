package com.rally26.publicpage.domain

import java.time.Instant
import java.util.UUID

enum class PageType { ORGANIZATION, TEAM, TOURNAMENT }

enum class PageStatus { DRAFT, PUBLISHED, ARCHIVED }

data class PublicPage(
    val id: UUID,
    val organizationId: UUID,
    val pageType: PageType,
    val entityId: UUID,
    val slug: String,
    val title: String,
    val summary: String?,
    val status: PageStatus,
    val publishedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

private val SLUG_PATTERN = Regex("^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$")

fun isValidPageSlug(slug: String): Boolean = SLUG_PATTERN.matches(slug)
