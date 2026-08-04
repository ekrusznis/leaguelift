package com.rally26.support.web

import com.rally26.support.domain.SupportArticle
import com.rally26.support.domain.SupportAudience
import com.rally26.support.domain.SupportCase
import com.rally26.support.domain.SupportCaseCategory
import com.rally26.support.domain.SupportCasePriority
import com.rally26.support.domain.SupportCaseStatus
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class SaveSupportArticleRequest(
    @field:NotBlank @field:Size(max = 120) val slug: String,
    @field:NotBlank @field:Size(max = 180) val title: String,
    @field:NotBlank @field:Size(max = 400) val summary: String,
    @field:NotBlank @field:Size(max = 20000) val bodyMarkdown: String,
    @field:NotBlank @field:Size(max = 80) val category: String,
    @field:NotNull val audience: SupportAudience,
    @field:Min(0) @field:Max(10000) val sortOrder: Int = 0,
)

data class SupportArticleResponse(
    val id: UUID, val slug: String, val title: String, val summary: String, val bodyMarkdown: String,
    val category: String, val audience: String, val status: String, val sortOrder: Int,
    val publishedAt: Instant?, val createdAt: Instant, val updatedAt: Instant,
)

fun SupportArticle.toResponse() = SupportArticleResponse(
    id, slug, title, summary, bodyMarkdown, category, audience.name, status.name, sortOrder, publishedAt, createdAt, updatedAt,
)

data class PublicSupportCaseRequest(
    @field:NotBlank @field:Size(min = 8, max = 120) val idempotencyKey: String,
    @field:NotBlank @field:Size(max = 120) val requesterName: String,
    @field:NotBlank @field:Email @field:Size(max = 320) val requesterEmail: String,
    @field:NotNull val category: SupportCaseCategory,
    @field:NotBlank @field:Size(max = 200) val subject: String,
    @field:NotBlank @field:Size(max = 5000) val description: String,
)

data class AuthenticatedSupportCaseRequest(
    @field:NotBlank @field:Size(min = 8, max = 120) val idempotencyKey: String,
    val organizationId: UUID? = null,
    @field:NotNull val category: SupportCaseCategory,
    @field:NotBlank @field:Size(max = 200) val subject: String,
    @field:NotBlank @field:Size(max = 5000) val description: String,
)

data class UpdateSupportCaseRequest(
    @field:NotNull val status: SupportCaseStatus,
    @field:NotNull val priority: SupportCasePriority,
    val assignedPlatformUserId: UUID? = null,
    @field:Size(max = 4000) val resolution: String? = null,
)

data class SupportCaseResponse(
    val id: UUID, val organizationId: UUID?, val organizationName: String?, val requesterUserId: UUID?,
    val requesterName: String, val requesterEmail: String, val category: String, val priority: String,
    val subject: String, val description: String, val status: String, val assignedPlatformUserId: UUID?,
    val assignedPlatformUserName: String?, val resolution: String?, val closedAt: Instant?,
    val createdAt: Instant, val updatedAt: Instant,
)

fun SupportCase.toResponse() = SupportCaseResponse(
    id, organizationId, organizationName, requesterUserId, requesterName, requesterEmail, category.name, priority.name,
    subject, description, status.name, assignedPlatformUserId, assignedPlatformUserName, resolution, closedAt, createdAt, updatedAt,
)
