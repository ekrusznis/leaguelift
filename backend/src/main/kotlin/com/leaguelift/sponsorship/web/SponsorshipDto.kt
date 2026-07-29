package com.leaguelift.sponsorship.web

import com.leaguelift.sponsorship.application.SponsorDirectoryEntry
import com.leaguelift.sponsorship.application.SponsorshipCheckout
import com.leaguelift.sponsorship.application.SponsorshipInvoice
import com.leaguelift.sponsorship.application.SponsorshipWithSponsor
import com.leaguelift.sponsorship.domain.Sponsor
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

/** Org-admin list shape (`GET /organizations/{id}/sponsorship-packages/{packageId}/sponsorships`, `GET /organizations/{id}/sponsorships/pending-review`). */
data class SponsorshipResponse(
	val id: UUID,
	val status: String,
	val amountMinor: Long,
	val currency: String,
	val sponsorId: UUID,
	val sponsorName: String,
	val sponsorContactEmail: String?,
	val confirmedAt: Instant?,
	val refundedAt: Instant?,
	val reviewStatus: String,
	val reviewedAt: Instant?,
	val createdAt: Instant,
)

fun SponsorshipWithSponsor.toResponse() = SponsorshipResponse(
	sponsorship.id, sponsorship.status.name, sponsorship.amountMinor, sponsorship.currency,
	sponsor.id, sponsor.name, sponsor.contactEmail, sponsorship.confirmedAt, sponsorship.refundedAt,
	sponsorship.reviewStatus.name, sponsorship.reviewedAt, sponsorship.createdAt,
)

/** Sponsor-contact CRM shape (Phase 6 remainder, ADR-019). */
data class SponsorResponse(
	val id: UUID,
	val name: String,
	val contactEmail: String?,
	val phone: String?,
	val companyName: String?,
	val notes: String?,
	val createdAt: Instant,
	val updatedAt: Instant,
)

fun Sponsor.toResponse() = SponsorResponse(id, name, contactEmail, phone, companyName, notes, createdAt, updatedAt)

data class UpdateSponsorRequest(
	@field:Size(min = 1, max = 200) val name: String? = null,
	@field:Email @field:Size(max = 254) val contactEmail: String? = null,
	@field:Size(max = 40) val phone: String? = null,
	@field:Size(max = 200) val companyName: String? = null,
	@field:Size(max = 2000) val notes: String? = null,
)

/** A downloadable/viewable receipt-style summary of one confirmed sponsorship (Phase 6 remainder, ADR-019) — no invoice numbering sequence. */
data class SponsorshipInvoiceResponse(
	val sponsorshipId: UUID,
	val status: String,
	val amountMinor: Long,
	val currency: String,
	val confirmedAt: Instant?,
	val sponsorName: String,
	val sponsorContactEmail: String?,
	val sponsorCompanyName: String?,
	val packageId: UUID,
	val packageName: String,
	val organizationId: UUID,
	val organizationName: String,
)

fun SponsorshipInvoice.toResponse() = SponsorshipInvoiceResponse(
	sponsorshipId = sponsorship.id,
	status = sponsorship.status.name,
	amountMinor = sponsorship.amountMinor,
	currency = sponsorship.currency,
	confirmedAt = sponsorship.confirmedAt,
	sponsorName = sponsor.name,
	sponsorContactEmail = sponsor.contactEmail,
	sponsorCompanyName = sponsor.companyName,
	packageId = sponsorshipPackage.id,
	packageName = sponsorshipPackage.name,
	organizationId = organization.id,
	organizationName = organization.name,
)

/** `GET /organizations/{id}/sponsorship-packages/qr-code` response — a plain URL plus a ready-to-render `data:image/png;base64,...` QR code image of that same URL (Phase 6 remainder, ADR-019; no click-through tracking, nothing persisted). */
data class ShareLinkResponse(val url: String, val qrCodeDataUri: String)

data class SponsorDirectoryEntryResponse(
	val sponsorId: UUID,
	val sponsorName: String,
	val packageId: UUID,
	val packageName: String,
	val logoUrl: String?,
)

fun SponsorDirectoryEntry.toResponse() = SponsorDirectoryEntryResponse(sponsorId, sponsorName, packageId, packageName, logoUrl)
