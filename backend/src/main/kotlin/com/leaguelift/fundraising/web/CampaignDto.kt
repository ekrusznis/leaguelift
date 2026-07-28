package com.leaguelift.fundraising.web

import com.leaguelift.fundraising.domain.Campaign
import com.leaguelift.fundraising.domain.CampaignStatus
import com.leaguelift.fundraising.domain.CampaignType
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class CreateCampaignRequest(
    val teamId: UUID? = null,
    @field:NotBlank @field:Size(min = 1, max = 120) val name: String,
    @field:NotBlank @field:Size(min = 1, max = 63) val slug: String,
    @field:Size(max = 2000) val description: String? = null,
    @field:NotNull val campaignType: CampaignType,
    @field:NotNull @field:Min(0) val goalAmountMinor: Long,
    @field:Size(min = 3, max = 3) val currency: String = "USD",
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
)

data class UpdateCampaignRequest(
    @field:Size(min = 1, max = 120) val name: String? = null,
    @field:Size(max = 2000) val description: String? = null,
    @field:Min(0) val goalAmountMinor: Long? = null,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
)

data class UpdateCampaignStatusRequest(
    @field:NotNull val status: CampaignStatus,
)

data class CampaignResponse(
    val id: UUID,
    val organizationId: UUID,
    val teamId: UUID?,
    val name: String,
    val slug: String,
    val description: String?,
    val campaignType: String,
    val goalAmountMinor: Long,
    val currency: String,
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val status: String,
    val publishedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/** Public-facing shape for the campaign's public page (section 16.6: `GET /public/campaigns/{slug}`). */
data class PublicCampaignResponse(
    val id: UUID,
    val organizationId: UUID,
    val teamId: UUID?,
    val name: String,
    val slug: String,
    val description: String?,
    val campaignType: String,
    val goalAmountMinor: Long,
    val currency: String,
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val status: String,
    val publishedAt: Instant?,
)

fun Campaign.toResponse() = CampaignResponse(
    id, organizationId, teamId, name, slug, description, campaignType.name, goalAmountMinor, currency, startDate, endDate, status.name, publishedAt, createdAt, updatedAt,
)

fun Campaign.toPublicResponse() = PublicCampaignResponse(
    id, organizationId, teamId, name, slug, description, campaignType.name, goalAmountMinor, currency, startDate, endDate, status.name, publishedAt,
)
