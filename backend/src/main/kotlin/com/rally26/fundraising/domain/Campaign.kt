package com.rally26.fundraising.domain

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

enum class CampaignType {
    ORGANIZATION_GENERAL,
    TEAM_GENERAL,
    TRAVEL,
    TOURNAMENT_FEES,
    UNIFORMS,
    EQUIPMENT,
    FACILITY_IMPROVEMENTS,
    SCHOLARSHIPS,
    SPECIAL_EVENTS,
    APPAREL_BASED,
    SPONSOR_SUPPORTED,
}

/**
 * Campaign lifecycle.
 *
 * PENDING_APPROVAL is used only when the organization owner has enabled the
 * fundraising approval requirement. ACTIVE remains the only state that can
 * accept new public contributions.
 */
enum class CampaignStatus { DRAFT, PENDING_APPROVAL, ACTIVE, COMPLETED, ARCHIVED }

/** Which starter template, if any, produced this campaign (Phase 42, DESIGN-DOC.md §14.1Q). Null means a blank/custom campaign. */
enum class FundraiserTemplateKey {
    GENERAL,
    IN_PERSON_EVENT,
    SPONSOR_MATCH,
    MILESTONE_CHALLENGE,
    FUNDRAISING_CHALLENGE,
    BOX_POOL,
    BAKE_SALE,
    CAR_WASH,
}

data class Campaign(
    val id: UUID,
    val organizationId: UUID,
    val teamId: UUID?,
    val name: String,
    val slug: String,
    val description: String?,
    val campaignType: CampaignType,
    val goalAmountMinor: Long,
    val currency: String,
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val status: CampaignStatus,
    val publishedAt: Instant?,
    val createdByUserId: UUID?,
    val templateKey: FundraiserTemplateKey?,
    val createdAt: Instant,
    val updatedAt: Instant,
    // Added after the original Campaign fields so existing positional Kotlin call sites
    // remain source-compatible. New code should prefer named arguments.
    val eventLocationName: String? = null,
    val eventAddress: String? = null,
    val submittedAt: Instant? = null,
    val approvedAt: Instant? = null,
    val approvedByUserId: UUID? = null,
)

private val SLUG_PATTERN = Regex("^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$")

fun isValidCampaignSlug(slug: String): Boolean = SLUG_PATTERN.matches(slug)
