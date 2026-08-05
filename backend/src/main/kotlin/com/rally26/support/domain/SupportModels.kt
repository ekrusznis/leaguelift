package com.rally26.support.domain

import java.time.Instant
import java.util.UUID

enum class SupportAudience { PUBLIC, OWNER_ADMIN, COACH, GUARDIAN, ATHLETE, PLATFORM }

enum class SupportArticleStatus { DRAFT, PUBLISHED, ARCHIVED }

data class SupportArticle(
    val id: UUID,
    val slug: String,
    val title: String,
    val summary: String,
    val bodyMarkdown: String,
    val category: String,
    val audience: SupportAudience,
    val status: SupportArticleStatus,
    val sortOrder: Int,
    val publishedAt: Instant?,
    val createdBy: UUID?,
    val updatedBy: UUID?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

enum class SupportCaseCategory {
    ACCOUNT_ACCESS,
    ORGANIZATION_SETUP,
    TEAM_OR_ROSTER,
    FEES_OR_PAYMENTS,
    FUNDRAISING,
    STORE_OR_ORDER,
    SPONSORSHIP,
    EVENT_OR_RSVP,
    TECHNICAL_PROBLEM,
    PRIVACY_OR_SAFETY,
    OTHER,
}

enum class SupportCasePriority { LOW, NORMAL, HIGH, URGENT }

enum class SupportCaseStatus { OPEN, IN_PROGRESS, WAITING_ON_CUSTOMER, RESOLVED, CLOSED }

data class SupportCase(
    val id: UUID,
    val idempotencyKey: String,
    val organizationId: UUID?,
    val organizationName: String?,
    val requesterUserId: UUID?,
    val requesterName: String,
    val requesterEmail: String,
    val category: SupportCaseCategory,
    val priority: SupportCasePriority,
    val subject: String,
    val description: String,
    val status: SupportCaseStatus,
    val assignedPlatformUserId: UUID?,
    val assignedPlatformUserName: String?,
    val resolution: String?,
    val closedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
)
