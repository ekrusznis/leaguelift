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

enum class CampaignStatus { DRAFT, ACTIVE, COMPLETED, ARCHIVED }

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
    val createdAt: Instant,
    val updatedAt: Instant,
)

private val SLUG_PATTERN = Regex("^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$")

fun isValidCampaignSlug(slug: String): Boolean = SLUG_PATTERN.matches(slug)
