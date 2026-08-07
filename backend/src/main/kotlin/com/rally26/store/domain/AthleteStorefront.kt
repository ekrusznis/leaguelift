package com.rally26.store.domain

import java.time.Instant
import java.util.UUID

enum class AthleteStorefrontStatus { DRAFT, PUBLISHED, ARCHIVED }

/**
 * Phase 24 slice 24.3 (DESIGN-DOC.md section 14.1G): a public, unauthenticated
 * per-athlete Swag Shop storefront — scoped to exactly one participant at
 * publish time, so household attribution for a resulting order is a direct
 * `participantId -> household.id` lookup, never an attribution code.
 */
data class AthleteStorefront(
    val id: UUID,
    val organizationId: UUID,
    val participantId: UUID,
    val teamId: UUID?,
    val storeId: UUID,
    val slug: String,
    val status: AthleteStorefrontStatus,
    val publishedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

private val ATHLETE_STOREFRONT_SLUG_PATTERN = Regex("^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$")

fun isValidAthleteStorefrontSlug(slug: String): Boolean = ATHLETE_STOREFRONT_SLUG_PATTERN.matches(slug)
