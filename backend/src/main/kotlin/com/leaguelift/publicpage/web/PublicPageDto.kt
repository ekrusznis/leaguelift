package com.leaguelift.publicpage.web

import com.leaguelift.common.web.PageResponse
import com.leaguelift.publicpage.domain.PageType
import com.leaguelift.publicpage.domain.PublicPage
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class CreatePublicPageRequest(
    @field:NotNull
    val pageType: PageType,
    @field:NotNull
    val entityId: UUID,
    @field:NotBlank
    @field:Pattern(regexp = "^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$")
    val slug: String,
    @field:NotBlank
    @field:Size(min = 1, max = 200)
    val title: String,
    @field:Size(max = 1000)
    val summary: String? = null,
)

data class UpdatePublicPageRequest(
    @field:Size(min = 1, max = 200)
    val title: String? = null,
    @field:Pattern(regexp = "^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$")
    val slug: String? = null,
    @field:Size(max = 1000)
    val summary: String? = null,
)

data class PublicPageResponse(
    val id: UUID,
    val organizationId: UUID,
    val pageType: PageType,
    val entityId: UUID,
    val slug: String,
    val title: String,
    val summary: String?,
    val status: String,
    val publishedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

fun PublicPage.toResponse() = PublicPageResponse(
    id = id,
    organizationId = organizationId,
    pageType = pageType,
    entityId = entityId,
    slug = slug,
    title = title,
    summary = summary,
    status = status.name,
    publishedAt = publishedAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

typealias PublicPagePageResponse = PageResponse<PublicPageResponse>
