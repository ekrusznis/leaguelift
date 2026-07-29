package com.leaguelift.sponsorship.web

import com.leaguelift.sponsorship.application.SponsorDirectoryEntry
import com.leaguelift.sponsorship.application.SponsorshipCheckout
import com.leaguelift.sponsorship.application.SponsorshipWithSponsor
import com.leaguelift.sponsorship.domain.Sponsorship
import com.leaguelift.sponsorship.domain.SponsorshipPackage
import com.leaguelift.sponsorship.domain.SponsorshipPackageStatus
import com.leaguelift.sponsorship.domain.effectiveMaxQuantity
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class CreateSponsorshipPackageRequest(
	@field:NotBlank @field:Size(min = 1, max = 120) val name: String,
	@field:Size(max = 2000) val description: String? = null,
	@field:NotNull @field:Min(0) val priceMinor: Long,
	@field:Size(min = 3, max = 3) val currency: String = "USD",
	@field:Min(1) val maxQuantity: Int? = null,
	val exclusive: Boolean = false,
	val placementStartDate: LocalDate? = null,
	val placementEndDate: LocalDate? = null,
)

data class UpdateSponsorshipPackageRequest(
	@field:Size(min = 1, max = 120) val name: String? = null,
	@field:Size(max = 2000) val description: String? = null,
	@field:Min(0) val priceMinor: Long? = null,
	@field:Min(1) val maxQuantity: Int? = null,
	val exclusive: Boolean? = null,
	val placementStartDate: LocalDate? = null,
	val placementEndDate: LocalDate? = null,
)

data class UpdateSponsorshipPackageStatusRequest(@field:NotNull val status: SponsorshipPackageStatus)

data class AssignSponsorLogoRequest(@field:NotNull val assetId: UUID, val altText: String? = null)

data class SponsorshipPackageResponse(
	val id: UUID,
	val organizationId: UUID,
	val name: String,
	val description: String?,
	val priceMinor: Long,
	val currency: String,
	val maxQuantity: Int?,
	val exclusive: Boolean,
	val placementStartDate: LocalDate?,
	val placementEndDate: LocalDate?,
	val status: String,
	val createdAt: Instant,
	val updatedAt: Instant,
	/** Sum of CONFIRMED (+ REFUNDED, still "was confirmed") sponsorships (SponsorshipRepository.countConfirmedForPackage). Real, not demo data. */
	val confirmedCount: Long,
	val soldOut: Boolean,
)

fun SponsorshipPackage.toResponse(confirmedCount: Long) = SponsorshipPackageResponse(
	id, organizationId, name, description, priceMinor, currency, maxQuantity, exclusive, placementStartDate, placementEndDate,
	status.name, createdAt, updatedAt, confirmedCount,
	soldOut = effectiveMaxQuantity()?.let { confirmedCount >= it } ?: false,
)

/** Public-facing shape for the org's sponsorship directory/purchase page (mirrors `PublicCampaignResponse`). */
data class PublicSponsorshipPackageResponse(
	val id: UUID,
	val organizationId: UUID,
	val name: String,
	val description: String?,
	val priceMinor: Long,
	val currency: String,
	val maxQuantity: Int?,
	val exclusive: Boolean,
	val placementStartDate: LocalDate?,
	val placementEndDate: LocalDate?,
	val confirmedCount: Long,
	val soldOut: Boolean,
)

fun SponsorshipPackage.toPublicResponse(confirmedCount: Long) = PublicSponsorshipPackageResponse(
	id, organizationId, name, description, priceMinor, currency, maxQuantity, exclusive, placementStartDate, placementEndDate,
	confirmedCount, soldOut = effectiveMaxQuantity()?.let { confirmedCount >= it } ?: false,
)

data class CreateSponsorshipCheckoutRequest(
	@field:NotBlank @field:Size(max = 120) val sponsorName: String,
	@field:Email @field:Size(max = 254) val sponsorContactEmail: String? = null,
	@field:NotBlank val successUrl: String,
	@field:NotBlank val cancelUrl: String,
)

data class SponsorshipCheckoutResponse(val sponsorshipId: UUID, val sponsorId: UUID, val checkoutUrl: String)

fun SponsorshipCheckout.toResponse() = SponsorshipCheckoutResponse(sponsorshipId, sponsorId, checkoutUrl)

/** Public status-poll shape — no sponsor contact info exposed back to the browser (mirrors `ContributionStatusResponse`). */
data class SponsorshipStatusResponse(
	val id: UUID,
	val status: String,
	val amountMinor: Long,
	val currency: String,
	val confirmedAt: Instant?,
)

fun Sponsorship.toStatusResponse() = SponsorshipStatusResponse(id, status.name, amountMinor, currency, confirmedAt)

/** Org-admin list shape (`GET /organizations/{id}/sponsorship-packages/{packageId}/sponsorships`). */
data class SponsorshipResponse(
	val id: UUID,
	val status: String,
	val amountMinor: Long,
	val currency: String,
	val sponsorId: UUID,
	val sponsorName: String,
	val sponsorContactEmail: String?,
	val confirmedAt: Instant?,
	val createdAt: Instant,
)

fun SponsorshipWithSponsor.toResponse() = SponsorshipResponse(
	sponsorship.id, sponsorship.status.name, sponsorship.amountMinor, sponsorship.currency,
	sponsor.id, sponsor.name, sponsor.contactEmail, sponsorship.confirmedAt, sponsorship.createdAt,
)

data class SponsorDirectoryEntryResponse(
	val sponsorId: UUID,
	val sponsorName: String,
	val packageId: UUID,
	val packageName: String,
	val logoUrl: String?,
)

fun SponsorDirectoryEntry.toResponse() = SponsorDirectoryEntryResponse(sponsorId, sponsorName, packageId, packageName, logoUrl)
