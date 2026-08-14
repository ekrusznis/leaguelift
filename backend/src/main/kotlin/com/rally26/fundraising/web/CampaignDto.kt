package com.rally26.fundraising.web

import com.rally26.fundraising.application.CampaignPermissions
import com.rally26.fundraising.domain.Campaign
import com.rally26.fundraising.domain.CampaignStatus
import com.rally26.fundraising.domain.CampaignType
import com.rally26.fundraising.domain.FundraiserTemplateKey
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
    @field:Size(max = 160) val eventLocationName: String? = null,
    @field:Size(max = 500) val eventAddress: String? = null,
    val templateKey: FundraiserTemplateKey? = null,
)

data class UpdateCampaignRequest(
    @field:Size(min = 1, max = 120) val name: String? = null,
    @field:Size(max = 2000) val description: String? = null,
    @field:Min(0) val goalAmountMinor: Long? = null,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    @field:Size(max = 160) val eventLocationName: String? = null,
    @field:Size(max = 500) val eventAddress: String? = null,
)

data class UpdateCampaignStatusRequest(
    @field:NotNull val status: CampaignStatus,
)

/** `GET /organizations/{id}/campaigns/qr-code` response — mirrors sponsorship's `ShareLinkResponse` shape exactly (a plain URL plus a ready-to-render `data:image/png;base64,...` QR image of that same URL; no click-through tracking, nothing persisted). */
data class CampaignShareLinkResponse(
    val url: String,
    val qrCodeDataUri: String,
)

data class CampaignPermissionsResponse(
    val canEdit: Boolean,
    val canRequestActivation: Boolean,
    val canApprove: Boolean,
    val canReturnToDraft: Boolean,
    val canClose: Boolean,
    val canArchive: Boolean,
    val canManageBoxPool: Boolean,
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
    val eventLocationName: String?,
    val eventAddress: String?,
    val status: String,
    val publishedAt: Instant?,
    val createdByUserId: UUID?,
    val templateKey: String?,
    val submittedAt: Instant?,
    val approvedAt: Instant?,
    val approvedByUserId: UUID?,
    val permissions: CampaignPermissionsResponse,
    val createdAt: Instant,
    val updatedAt: Instant,
    /** Sum of CONFIRMED contributions (fundraising/persistence/ContributionRepository.kt). Real, not demo data. */
    val raisedMinor: Long,
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
    val eventLocationName: String?,
    val eventAddress: String?,
    val status: String,
    val publishedAt: Instant?,
    val raisedMinor: Long,
    val logoUrl: String?,
    val coverUrl: String?,
    val primaryColor: String,
    val secondaryColor: String,
)

fun Campaign.toResponse(
    raisedMinor: Long,
    permissions: CampaignPermissions,
) = CampaignResponse(
    id,
    organizationId,
    teamId,
    name,
    slug,
    description,
    campaignType.name,
    goalAmountMinor,
    currency,
    startDate,
    endDate,
    eventLocationName,
    eventAddress,
    status.name,
    publishedAt,
    createdByUserId,
    templateKey?.name,
    submittedAt,
    approvedAt,
    approvedByUserId,
    CampaignPermissionsResponse(
        canEdit = permissions.canEdit,
        canRequestActivation = permissions.canRequestActivation,
        canApprove = permissions.canApprove,
        canReturnToDraft = permissions.canReturnToDraft,
        canClose = permissions.canClose,
        canArchive = permissions.canArchive,
        canManageBoxPool = permissions.canManageBoxPool,
    ),
    createdAt,
    updatedAt,
    raisedMinor,
)

fun Campaign.toPublicResponse(
    raisedMinor: Long,
    logoUrl: String? = null,
    coverUrl: String? = null,
    primaryColor: String = "#0B1F33",
    secondaryColor: String = "#20B26B",
) = PublicCampaignResponse(
    id,
    organizationId,
    teamId,
    name,
    slug,
    description,
    campaignType.name,
    goalAmountMinor,
    currency,
    startDate,
    endDate,
    eventLocationName,
    eventAddress,
    status.name,
    publishedAt,
    raisedMinor,
    logoUrl,
    coverUrl,
    primaryColor,
    secondaryColor,
)
